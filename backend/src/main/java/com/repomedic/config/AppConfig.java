package com.repomedic.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@EnableAsync
public class AppConfig {
    @Bean
    WebMvcConfigurer corsConfigurer(@Value("${repomedic.cors-origin}") String origin) {
        return new WebMvcConfigurer() {
            @Override public void addCorsMappings(CorsRegistry registry) {
                registry.addMapping("/api/**").allowedOrigins(origin).allowedMethods("GET", "POST", "DELETE", "OPTIONS").allowedHeaders("Content-Type");
            }
        };
    }
}
