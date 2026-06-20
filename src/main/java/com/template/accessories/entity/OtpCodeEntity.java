package com.template.accessories.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "otp_code")
@Getter
@Setter
@NoArgsConstructor
public class OtpCodeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NonNull
    @Column(nullable = false, name = "mobile")
    private String mobile;

    @NonNull
    @Column(nullable = false, name = "code")
    private String code;

    @NonNull
    @Column(nullable = false, name = "expires_at")
    private LocalDateTime expiresAt;

    @Column(nullable = false, name = "used")
    private boolean used = false;
}
