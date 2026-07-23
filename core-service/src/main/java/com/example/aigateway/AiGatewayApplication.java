package com.example.aigateway;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

/** Bootstraps the Spring Boot AI gateway application. */
@MapperScan("com.example.aigateway.mapper")
@SpringBootApplication
@EnableDiscoveryClient
public class AiGatewayApplication {

    public static void main(String[] args) {
        SpringApplication.run(AiGatewayApplication.class, args);
    }
}
