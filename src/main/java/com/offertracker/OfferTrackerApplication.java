package com.offertracker;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@MapperScan("com.offertracker.mapper")
public class OfferTrackerApplication {

    public static void main(String[] args) {
        SpringApplication.run(OfferTrackerApplication.class, args);
    }
}
