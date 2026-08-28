package com.dotacioneszambrano.inventorio.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.client.RestClient;

@Configuration
public class BeansConfig {

    @Bean
    public BCryptPasswordEncoder passwordEncoder() {
        // Compatible con hashes de flask-bcrypt ($2a$, $2b$)
        return new BCryptPasswordEncoder();
    }

    @Bean
    public RestClient restClient() {
        return RestClient.create();
    }
}
