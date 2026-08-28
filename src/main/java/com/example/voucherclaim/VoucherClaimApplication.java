package com.example.voucherclaim;

import com.example.voucherclaim.config.AppProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@SpringBootApplication
@EnableConfigurationProperties(AppProperties.class)
public class VoucherClaimApplication {

    public static void main(String[] args) {
        SpringApplication.run(VoucherClaimApplication.class, args);
    }
}
