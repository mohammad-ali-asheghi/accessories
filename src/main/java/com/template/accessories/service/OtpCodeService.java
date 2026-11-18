package com.template.accessories.service;

import com.template.accessories.entity.OtpCodeEntity;
import com.template.accessories.exception.ServiceException;
import com.template.accessories.repository.OtpCodeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class OtpCodeService {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    @Value("${telegram.bot.token:}")
    private String botToken;

    private final OtpCodeRepository otpCodeRepository;
    private final RestTemplate restTemplate;

    public void sendOtp(Long chatId, String code) {
        if (botToken == null || botToken.isBlank()) {
            throw new ServiceException("توکن ربات برای ارسال کد تایید تنظیم نشده است.");
        }
        if (chatId == null || chatId == 0L) {
            throw new ServiceException("شناسه چت برای این کاربر تنظیم نشده است.");
        }

        String url = "https://tapi.bale.ai/bot" + botToken + "/sendMessage";

        Map<String, Object> request = new HashMap<>();
        request.put("chat_id", chatId);
        request.put("text", "کد تایید ورود به پنل مدیریت: " + code);

        try {
            restTemplate.postForEntity(url, request, String.class);
            log.info("Sent OTP code to chat id: {}", chatId);
        } catch (Exception e) {
            log.error("Failed to send OTP code to chat id {}: {}", chatId, e.getMessage());
            throw new ServiceException("خطا در ارسال کد تایید به پیام‌رسان.");
        }
    }

    public String generateOtp(String mobile) {
        String code = String.format("%06d", SECURE_RANDOM.nextInt(1_000_000));

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
