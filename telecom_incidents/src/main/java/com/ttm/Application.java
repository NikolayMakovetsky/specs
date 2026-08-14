package com.ttm;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.web.reactive.function.client.WebClient;

@SpringBootApplication
public class Application {

    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
        System.out.println("✅ TTM Lite запущен!");
        System.out.println("📊 Web: http://localhost:8081");
        System.out.println("💾 H2: http://localhost:8081/h2-console");
        System.out.println("⚙️  Camunda: http://localhost:8080");
    }

    @Bean
    public WebClient webClient() {
        return WebClient.builder()
            .baseUrl("http://localhost:8080/engine-rest")
            .build();
    }
}