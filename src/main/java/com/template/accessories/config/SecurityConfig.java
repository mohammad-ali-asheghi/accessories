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
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.annotation.web.configurers.HeadersConfigurer;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;

@Slf4j
@Configuration
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true)
public class SecurityConfig {

    @Value("${app.admin.mobile:}")
    private String mobileNumbers;

    @Value("${app.admin.password:}")
    private String adminPassword;

    @Value("${app.admin.chat-id:}")
    private String chatIds;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .headers(headers -> headers.frameOptions(HeadersConfigurer.FrameOptionsConfig::disable))
                .authorizeHttpRequests(auth -> auth
                        // Static assets
                        .requestMatchers("/css/**", "/js/**", "/fonts/**", "/images/**", "/uploads/**", "/icon.png", "/favicon.ico", "/error").permitAll()
                        // Public storefront pages & endpoints
                        .requestMatchers("/", "/login", "/otp/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/products/{id:[0-9]+}").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/products", "/api/products/**").permitAll()
                        // Admin restricted endpoints
                        .requestMatchers("/admin/**").hasRole("ADMIN")
                        .requestMatchers("/products/create").hasRole("ADMIN")
                        .requestMatchers("/products/*/edit").hasRole("ADMIN")
                        .requestMatchers("/products/*/delete").hasRole("ADMIN")
                        .requestMatchers("/products/*/toggle-stock").hasRole("ADMIN")
                        .requestMatchers("/products/*/images/**").hasRole("ADMIN")
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
                if (mobileNumbers == null || mobileNumbers.isBlank() || adminPassword == null || adminPassword.isBlank()) {
                    log.warn("ADMIN_MOBILES or ADMIN_PASSWORD environment variable is not set. Skipping initial admin seeding.");
                    return;
                }

                String[] mobiles = mobileNumbers.split(",");
                String[] chats = (chatIds != null && !chatIds.isBlank()) ? chatIds.split(",") : new String[0];

                for (int i = 0; i < mobiles.length; i++) {
                    String mobile = mobiles[i].trim();
                    if (mobile.isEmpty()) continue;

                    Long chatId = 0L;
                    if (i < chats.length && !chats[i].trim().isEmpty()) {
                        try {
                            chatId = Long.parseLong(chats[i].trim());
                        } catch (NumberFormatException ignored) {}
                    } else if (chats.length == 1 && !chats[0].trim().isEmpty()) {
                        try {
                            chatId = Long.parseLong(chats[0].trim());
                        } catch (NumberFormatException ignored) {}
                    }

                    final Long resolvedChatId = chatId;
                    userRepository.findByUsername(mobile).ifPresentOrElse(
                            existing -> {
                                log.info("Admin user {} already exists. Refreshing credentials.", mobile);
                                existing.setPassword(passwordEncoder.encode(adminPassword));
                                if (resolvedChatId != 0L) {
                                    existing.setChatId(resolvedChatId);
                                }
                                existing.setEnabled(true);
                                existing.setRole(RoleEnum.ADMIN);
                                userRepository.save(existing);
                            },
                            () -> {
                                UserEntity admin = new UserEntity();
                                admin.setUsername(mobile);
                                admin.setEnabled(true);
                                admin.setRole(RoleEnum.ADMIN);
                                admin.setPassword(passwordEncoder.encode(adminPassword));
                                admin.setChatId(resolvedChatId);
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
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(5));
        factory.setReadTimeout(Duration.ofSeconds(10));
        return new RestTemplate(factory);
    }
}
