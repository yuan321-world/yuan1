package com.englishhero;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration;

// Identity is explicitly configured; never generate a fallback demo user/password.
@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
public class EnglishHeroApplication {
    public static void main(String[] args) {
        SpringApplication.run(EnglishHeroApplication.class, args);
    }
}
