package com.template.accessories.enums;

import lombok.Getter;
import lombok.NonNull;
import org.springframework.security.core.GrantedAuthority;

@Getter
public enum RoleEnum implements GrantedAuthority {

    ADMIN("ADMIN", 1),
    USER("USER", 2);

    private final String name;
    private final int code;

    RoleEnum(String name, int code) {
        this.name = name;
        this.code = code;
    }

    @Override
    public @NonNull String getAuthority() {
        return "ROLE_" + this.name;
    }
}
