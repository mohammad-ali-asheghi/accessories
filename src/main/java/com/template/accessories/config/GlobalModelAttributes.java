package com.template.accessories.config;

import com.template.accessories.enums.RoleEnum;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

import java.util.Objects;

@ControllerAdvice
@RequiredArgsConstructor
public class GlobalModelAttributes {

    private final SellerConfig sellerConfig;

    @ModelAttribute("seller")
    public SellerConfig getSeller() {
        return sellerConfig;
    }

    @ModelAttribute("isAdmin")
    public boolean getIsAdmin(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return false;
        }
        return authentication.getAuthorities().stream()
                .anyMatch(a -> Objects.equals(a.getAuthority(), RoleEnum.ADMIN.getAuthority()));
    }
}
