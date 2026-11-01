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
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Controller
@RequiredArgsConstructor
public class AuthController {

    private final OtpCodeService otpCodeService;
    private final UserService userService;
    private final PasswordEncoder passwordEncoder;

    // Per-mobile cooldown cache for OTP requests
    private static final Map<String, LocalDateTime> OTP_COOLDOWN = new ConcurrentHashMap<>();
    private static final int COOLDOWN_SECONDS = 60;

    @GetMapping("/login")
    public String loginPage(@RequestParam(value = "error", required = false) String error,
                            @RequestParam(value = "logout", required = false) String logout,
                            Model model) {
        if (error != null) {
            model.addAttribute("error", "شماره موبایل یا رمز عبور اشتباه است.");
        }
        if (logout != null) {
            model.addAttribute("message", "با موفقیت از سیستم خارج شدید.");
        }
        return "login";
    }

    /**
     * Password-based login for admin
     */
    @PostMapping("/login")
    public String loginWithPassword(@RequestParam String username,
                                    @RequestParam String password,
                                    HttpServletRequest request,
                                    RedirectAttributes redirectAttributes) {
        String cleanMobile = username.trim();
        try {
            UserEntity user = userService.loadUserByUsername(cleanMobile);
            if (passwordEncoder.matches(password, user.getPassword())) {
                authenticateUser(user, request);
                log.info("Admin {} logged in successfully with password.", cleanMobile);
                redirectAttributes.addFlashAttribute("successMessage", "ورود به پنل مدیریت با موفقیت انجام شد.");
                return "redirect:/admin";
            }
        } catch (Exception e) {
            log.warn("Login failed for user {}: {}", cleanMobile, e.getMessage());
        }

        redirectAttributes.addFlashAttribute("error", "شماره موبایل یا رمز عبور نادرست است.");
        return "redirect:/login";
    }

    /**
     * Send OTP to admin's Telegram/Bale chat
     */
    @PostMapping("/otp/send")
    public String sendOtp(@RequestParam String mobile,
                          RedirectAttributes redirectAttributes,
                          Model model) {
        String cleanMobile = mobile.trim();

        // Check rate limiting for this specific mobile
        LocalDateTime lastRequest = OTP_COOLDOWN.get(cleanMobile);
        if (lastRequest != null && lastRequest.isAfter(LocalDateTime.now())) {
            long remainingSec = Duration.between(LocalDateTime.now(), lastRequest).getSeconds();
            model.addAttribute("mobile", cleanMobile);
            model.addAttribute("error", "لطفاً " + remainingSec + " ثانیه دیگر مجدداً تلاش کنید.");
            return "verify-otp";
        }

        try {
            UserEntity user = userService.loadUserByUsername(cleanMobile);
            String code = otpCodeService.generateOtp(user.getUsername());
            otpCodeService.sendOtp(user.getChatId(), code);

            OTP_COOLDOWN.put(cleanMobile, LocalDateTime.now().plusSeconds(COOLDOWN_SECONDS));
            log.info("OTP sent to admin: {}", cleanMobile);

            model.addAttribute("mobile", cleanMobile);
            model.addAttribute("message", "کد تایید با موفقیت به پیام‌رسان بله/تلگرام شما ارسال شد.");
            model.addAttribute("cooldown", COOLDOWN_SECONDS);
            return "verify-otp";
        } catch (Exception e) {
            log.error("Failed to send OTP to {}: {}", cleanMobile, e.getMessage());
            redirectAttributes.addFlashAttribute("error", "کاربری با این شماره موبایل یافت نشد یا در ارسال کد خطایی رخ داد.");
            return "redirect:/login";
        }
    }

    /**
     * Verify OTP and log in
     */
    @PostMapping("/otp/verify")
    public String verifyOtp(@RequestParam String mobile,
                            @RequestParam String code,
                            HttpServletRequest request,
                            RedirectAttributes redirectAttributes,
                            Model model) {
        String cleanMobile = mobile.trim();
        String cleanCode = code.trim();

        boolean valid = otpCodeService.validateOtp(cleanMobile, cleanCode);
        if (!valid) {
            model.addAttribute("mobile", cleanMobile);
            model.addAttribute("error", "کد تایید وارد شده نامعتبر یا منقضی شده است.");
            return "verify-otp";
        }

        try {
            UserEntity user = userService.loadUserByUsername(cleanMobile);
            authenticateUser(user, request);
            OTP_COOLDOWN.remove(cleanMobile);
            log.info("Admin {} logged in successfully with OTP.", cleanMobile);
            redirectAttributes.addFlashAttribute("successMessage", "ورود به پنل مدیریت با موفقیت انجام شد.");
            return "redirect:/admin";
        } catch (Exception e) {
            log.error("Failed to load user during OTP verification", e);
            model.addAttribute("mobile", cleanMobile);
            model.addAttribute("error", "خطا در احراز هویت کاربر.");
            return "verify-otp";
        }
    }

    private void authenticateUser(UserEntity user, HttpServletRequest request) {
        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities());
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);

        HttpSession session = request.getSession(true);
        session.setAttribute("SPRING_SECURITY_CONTEXT", context);
    }
}
