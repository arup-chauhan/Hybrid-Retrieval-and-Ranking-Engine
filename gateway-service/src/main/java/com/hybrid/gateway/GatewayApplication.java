package com.hybrid.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
@EnableDiscoveryClient
public class GatewayApplication {
    public static void main(String[] args) {
        SpringApplication.run(GatewayApplication.class, args);
    }

    @Bean
    public RouteLocator routeLocator(RouteLocatorBuilder builder) {
        return builder.routes()
                .route("query_service", r -> r.path("/query/**").uri("http://query-service:8083"))
                .route("ranking_service", r -> r.path("/rank/**").uri("http://ranking-service:8085"))
                .route("fusion_service", r -> r.path("/fusion/**").uri("http://fusion-service:8087"))
                .build();
    }
}
