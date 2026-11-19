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

    private String name = "فروشگاه اکسسوری";
    private String phone = "09123456789";
    private String telegram = "accessory_store";
    private String instagram = "accessory_store";
    private String bale = "accessory_store";
    private String whatsapp = "09123456789";
    private String address = "تهران، بازار بزرگ، پاساژ اکسسوری";
}
