package com.template.accessories.service;

import com.template.accessories.entity.OtpCodeEntity;
import com.template.accessories.repository.OtpCodeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Random;

@Slf4j
@Service
@RequiredArgsConstructor
public class OtpCodeService {

    @Value("${telegram.bot.token}")
    private String BOT_TOKEN;

    private final OtpCodeRepository otpCodeRepository;
    private final RestTemplate restTemplate;

    public void sendOtp(Long chatId, String code) {
        String url = "https://tapi.bale.ai/bot" + BOT_TOKEN + "/sendMessage";

        Map<String, Object> request = new HashMap<>();
        request.put("chat_id", chatId);
        request.put("text", code);

        try {
            restTemplate.postForEntity(url, request, String.class);
            log.info("Send OTP code to chat with id: {}", chatId);
        } catch (Exception e) {
            log.error("Send OTP code to chat with id: {} with error : {}", chatId, e.getMessage());
        }
    }

    public String generateOtp(String mobile) {
        String code = String.format("%06d", new Random().nextInt(1_000_000));

        OtpCodeEntity otp = new OtpCodeEntity();
        otp.setMobile(mobile);
        otp.setCode(code);
        otp.setExpiresAt(LocalDateTime.now().plusMinutes(2));
        otp.setUsed(false);

        otpCodeRepository.save(otp);
        return code;
    }

    public boolean validateOtp(String mobile, String code) {
        return this.topOtp(mobile)
                .filter(otp -> otp.getCode().equals(code))
                .filter(otp -> otp.getExpiresAt().isAfter(LocalDateTime.now()))
                .map(otp -> {
                    otp.setUsed(true);
                    otpCodeRepository.save(otp);
                    return true;
                })
                .orElse(false);
    }

    public Optional<OtpCodeEntity> topOtp(String mobile) {
        return otpCodeRepository.findTopByMobileAndUsedFalseOrderByIdDesc(mobile);
    }
}
