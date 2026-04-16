package com.genaicbi;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class GenAiCbiApplication {

    public static void main(String[] args) {
        SpringApplication.run(GenAiCbiApplication.class, args);
    }
}
