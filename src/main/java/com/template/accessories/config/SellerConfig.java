package com.template.accessories.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Getter
@Setter
@Configuration
@ConfigurationProperties(prefix = "app.seller")
public class SellerConfig {

    private String name = "nodehi";
    private String phone = "09120001234";
    private String telegram = "mhmd928787";
    private String instagram = "mhmd928787";
    private String bale = "mhmd928787";
    private String whatsapp = "09120001234";
    private String address = "address";
}
