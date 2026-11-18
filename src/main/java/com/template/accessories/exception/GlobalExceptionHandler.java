package com.template.accessories.exception;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import java.util.Map;

@Slf4j
@ControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ServiceException.class)
    public Object handleServiceException(ServiceException ex, HttpServletRequest request, Model model) {
        log.warn("Service exception on {}: {}", request.getRequestURI(), ex.getMessage());
        if (isApiRequest(request)) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("success", false, "message", ex.getMessage()));
        }
        model.addAttribute("statusCode", 404);
        model.addAttribute("errorTitle", "موردی یافت نشد");
        model.addAttribute("errorMessage", ex.getMessage());
        return "error";
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public Object handleMaxUploadSize(MaxUploadSizeExceededException ex, HttpServletRequest request, Model model) {
        String msg = "حجم فایل‌های انتخاب‌شده بیشتر از حد مجاز است (حداکثر ۱۰ مگابایت برای هر عکس).";
        if (isApiRequest(request)) {
            return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE)
                    .body(Map.of("success", false, "message", msg));
        }
        model.addAttribute("statusCode", 413);
        model.addAttribute("errorTitle", "حجم فایل بیش از حد مجاز");
        model.addAttribute("errorMessage", msg);
        return "error";
    }

    @ExceptionHandler(AccessDeniedException.class)
    public Object handleAccessDenied(AccessDeniedException ex, HttpServletRequest request, Model model) {
        if (isApiRequest(request)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("success", false, "message", "دسترسی غیرمجاز"));
        }
        model.addAttribute("statusCode", 403);
        model.addAttribute("errorTitle", "عدم دسترسی");
        model.addAttribute("errorMessage", "شما مجوز دسترسی به این بخش را ندارید.");
        return "error";
    }

    @ExceptionHandler(Exception.class)
    public Object handleGenericException(Exception ex, HttpServletRequest request, Model model) {
        log.error("Unhandled exception on {}", request.getRequestURI(), ex);
        if (isApiRequest(request)) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("success", false, "message", "خطای داخلی سرور رخ داد."));
        }
        model.addAttribute("statusCode", 500);
        model.addAttribute("errorTitle", "خطای سیستمی");
        model.addAttribute("errorMessage", "متأسفانه خطایی در پردازش درخواست شما رخ داد. لطفاً دوباره تلاش کنید.");
        return "error";
    }

    private boolean isApiRequest(HttpServletRequest request) {
        String uri = request.getRequestURI();
        String accept = request.getHeader("Accept");
        return (uri != null && uri.startsWith("/api/"))
                || (accept != null && accept.contains("application/json"));
    }
}
