package com.example.backend.shared.infrastructure.config;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ClockConfiguration {

    /** Use cases take the time from this bean so unit tests can pin it. */
    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }
}
