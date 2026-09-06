package com.wattwise;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class WattWiseApplication {

    public static void main(String[] args) {
        SpringApplication.run(WattWiseApplication.class, args);
    }
}
