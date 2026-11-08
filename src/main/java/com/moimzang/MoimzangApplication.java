package com.moimzang;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class MoimzangApplication {

    public static void main(String[] args) {
        SpringApplication.run(MoimzangApplication.class, args);
    }
}
