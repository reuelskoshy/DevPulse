package com.devpulse;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class DevPulseApplication {

    public static void main(String[] args) {
        SpringApplication.run(DevPulseApplication.class, args);
    }
}
