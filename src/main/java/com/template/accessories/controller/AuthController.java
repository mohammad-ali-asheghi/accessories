package com.template.accessories.controller;

import com.template.accessories.entity.UserEntity;
import com.template.accessories.service.OtpCodeService;
import com.template.accessories.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Controller
@RequiredArgsConstructor
public class AuthController {

    private final OtpCodeService otpCodeService;
    private final UserService userService;

    //local cache
    private static final Map<Integer, LocalDateTime> CACHE = new ConcurrentHashMap<>();

    @GetMapping("/login")
    public String loginPage() {
        return "login";
    }

    @PostMapping("/otp/send")
    public String sendOtp(@RequestParam String mobile, Model model) {
        validateLoginAttempt(model);

        if (CACHE.isEmpty()) {
            try {
                UserEntity user = userService.loadUserByUsername(mobile);
                String code = otpCodeService.generateOtp(user.getUsername());
                otpCodeService.sendOtp(user.getChatId(), code);
                log.debug("Otp code sent to: {} is: {}", user.getUsername(), code);
                model.addAttribute("mobile", mobile);
                model.addAttribute("message", "OTP generated successfully");
                return "verify-otp";
            } catch (Exception e) {
                CACHE.put(1, LocalDateTime.now().plusSeconds(10));
                e.fillInStackTrace();
                return "redirect:/login";
            }
        } else {
            return "redirect:/login";
        }
    }

    private void validateLoginAttempt(Model model) {
        if (CACHE.isEmpty()) {
            System.out.println("ok");
        } else {
            LocalDateTime loginAttemptTime = CACHE.get(1);
            if (loginAttemptTime == null) {
                return;
            }
            if (loginAttemptTime.isAfter(LocalDateTime.now())) {
                model.addAttribute("message", "please try again a few seconds later!");
            } else {
                CACHE.remove(1);
            }
        }
    }

    @PostMapping("/otp/verify")
    public String verifyOtp(@RequestParam String mobile,
                            @RequestParam String code,
                            HttpServletRequest request,
                            Model model) {

        boolean valid = otpCodeService.validateOtp(mobile, code);
        if (!valid) {
            model.addAttribute("mobile", mobile);
            model.addAttribute("error", "Invalid or expired OTP");
            return "verify-otp";
        }

        UserEntity user = userService.loadUserByUsername(mobile);

        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities());

        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);

        HttpSession session = request.getSession(true);
        session.setAttribute("SPRING_SECURITY_CONTEXT", context);

        return "redirect:/";
    }
}
