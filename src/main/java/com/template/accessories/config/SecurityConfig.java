package com.template.accessories.config;

import com.template.accessories.entity.UserEntity;
import com.template.accessories.enums.RoleEnum;
import com.template.accessories.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.annotation.web.configurers.HeadersConfigurer;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.List;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private static final Logger logger = LoggerFactory.getLogger(SecurityConfig.class);

    @Value("${app.admin.mobile}")
    private String mobileNumbers;

    @Value("${app.admin.password}")
    private String adminPassword;

    @Value("${app.admin.chat-id}")
    private String chatIds;

    static List<String> permitURI = new ArrayList<>();

    static {
        permitURI.add("/");
        permitURI.add("/login");
        permitURI.add("/otp/send");
        permitURI.add("/otp/verify");
        permitURI.add("/css/**");
        permitURI.add("/js/**");
        permitURI.add("/fonts/**");
        permitURI.add("/images/**");
        permitURI.add("/h2-console/**");
        permitURI.add("/products/**");
        permitURI.add("/api/products/**");
        permitURI.add("/uploads/**");
        permitURI.add("/icon.png");
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) {
        http
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(permitURI.toArray(new String[0])).permitAll()
                        .requestMatchers("/admin/**").hasRole("ADMIN")
                        .anyRequest().authenticated()
                )
                .logout(logout -> logout
                        .logoutSuccessUrl("/")
                        .invalidateHttpSession(true)
                        .deleteCookies("JSESSIONID")
                )
                .csrf(AbstractHttpConfigurer::disable)
                .headers(headers -> headers
                        .frameOptions(HeadersConfigurer.FrameOptionsConfig::disable)
                )
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(Customizer.withDefaults());

        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    CommandLineRunner createAdmin(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        return args -> {
            String[] mobiles = mobileNumbers.split(",");
            String[] chats = chatIds.split(",");

            if (mobiles.length != chats.length) {
                throw new IllegalStateException("Application running failure.");
            }

            for (int i = 0; i < mobiles.length; i++) {
                String mobile = mobiles[i];
                Long chatId = Long.valueOf(chats[i]);

                if (userRepository.findByUsername(mobile).isEmpty()) {
                    UserEntity admin = new UserEntity();
                    admin.setUsername(mobile);
                    admin.setEnabled(true);
                    admin.setRole(RoleEnum.ADMIN);
                    admin.setPassword(passwordEncoder.encode(adminPassword));
                    admin.setChatId(chatId);
                    userRepository.save(admin);
                } else {
                    logger.warn("User with mobile {} already exists.", mobile);
                }
            }
        };
    }

    @Bean
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }
}
