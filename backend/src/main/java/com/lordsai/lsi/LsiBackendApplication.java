package com.lordsai.lsi;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableScheduling;

// UserDetailsServiceAutoConfiguration is excluded because authentication is JWT-based against the
// users table; Spring's default in-memory user (and its "generated security password" log) is never used.
@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
@ConfigurationPropertiesScan
@EnableScheduling
public class LsiBackendApplication {

    public static void main(String[] args) {
        SpringApplication.run(LsiBackendApplication.class, args);
    }
}
