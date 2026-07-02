package com.rana.cardlens;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class CardLensApplication {
    public static void main(String[] args) {
        SpringApplication.run(CardLensApplication.class, args);
    }
}
