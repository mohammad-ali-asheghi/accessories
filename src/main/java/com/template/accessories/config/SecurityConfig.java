package com.template.accessories.config;

import com.template.accessories.entity.UserEntity;
import com.template.accessories.enums.RoleEnum;
import com.template.accessories.repository.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.annotation.web.configurers.HeadersConfigurer;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.client.RestTemplate;

@Slf4j
@Configuration
@EnableWebSecurity
@EnableMethodSecurity()
public class SecurityConfig {

    @Value("${app.admin.mobile:09902904565}")
    private String mobileNumbers;

    @Value("${app.admin.password:admin1234}")
    private String adminPassword;

    @Value("${app.admin.chat-id:1979392472}")
    private String chatIds;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .headers(headers -> headers.frameOptions(HeadersConfigurer.FrameOptionsConfig::disable))
                .authorizeHttpRequests(auth -> auth
                        // Static assets
                        .requestMatchers("/css/**", "/js/**", "/fonts/**", "/images/**", "/uploads/**", "/icon.png", "/favicon.ico").permitAll()
                        // Public storefront pages & endpoints
                        .requestMatchers("/login", "/otp/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/products/{id:[0-9]+}").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/products/**").permitAll()
                        // Admin restricted endpoints
                        .requestMatchers("/admin/**").hasRole("ADMIN")
                        .requestMatchers("/products/create").hasRole("ADMIN")
                        .requestMatchers("/products/*/edit").hasRole("ADMIN")
                        .requestMatchers("/products/*/delete").hasRole("ADMIN")
                        .requestMatchers("/products/*/toggle-stock").hasRole("ADMIN")
                        .anyRequest().authenticated()
                )
                .logout(logout -> logout
                        .logoutUrl("/logout")
                        .logoutSuccessUrl("/?logout=true")
                        .invalidateHttpSession(true)
                        .deleteCookies("JSESSIONID")
                        .permitAll()
                );

        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    CommandLineRunner createAdmin(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        return args -> {
            try {
                if (mobileNumbers == null || mobileNumbers.isBlank() || chatIds == null || chatIds.isBlank()) {
                    log.warn("Admin mobile or chat ID configuration is empty.");
                    return;
                }

                String[] mobiles = mobileNumbers.split(",");
                String[] chats = chatIds.split(",");

                for (int i = 0; i < mobiles.length; i++) {
                    String mobile = mobiles[i].trim();
                    if (mobile.isEmpty()) continue;

                    Long chatId = (i < chats.length && !chats[i].trim().isEmpty())
                            ? Long.parseLong(chats[i].trim())
                            : 0L;

                    userRepository.findByUsername(mobile).ifPresentOrElse(
                            existing -> {
                                log.info("Admin user {} already exists.", mobile);
                                if (existing.getChatId() == 0L) {
                                    existing.setChatId(chatId);
                                    userRepository.save(existing);
                                }
                            },
                            () -> {
                                UserEntity admin = new UserEntity();
                                admin.setUsername(mobile);
                                admin.setEnabled(true);
                                admin.setRole(RoleEnum.ADMIN);
                                admin.setPassword(passwordEncoder.encode(adminPassword));
                                admin.setChatId(chatId);
                                userRepository.save(admin);
                                log.info("Created admin user: {}", mobile);
                            }
                    );
                }
            } catch (Exception e) {
                log.error("Error creating initial admin users: {}", e.getMessage());
            }
        };
    }

    @Bean
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }
}
