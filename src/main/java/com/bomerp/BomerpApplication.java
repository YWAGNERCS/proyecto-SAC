package com.bomerp;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class BomerpApplication {

    public static void main(String[] args) {
        SpringApplication.run(BomerpApplication.class, args);
    }

}
