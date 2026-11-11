package com.example.gateway;

import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import reactor.core.publisher.Mono;

@Configuration
public class RateLimiterConfig {

    @Bean
    KeyResolver ipKeyResolver() {
        return exchange -> {
            String resolvedIp = "anonymous-client";

            // Khi dùng proxy, đọc header từ proxy
            String xff = exchange.getRequest().getHeaders().getFirst("X-Forwarded-For");
            if (xff != null && !xff.isBlank()) {
                resolvedIp = xff.split(",")[0].trim();
            } else {
                // Fallback khi run thẳng vào service bỏ qua proxy
                var remoteAddress = exchange.getRequest().getRemoteAddress();
                if (remoteAddress != null && remoteAddress.getAddress() != null) {
                    resolvedIp = remoteAddress.getAddress().getHostAddress();
                }
            }

            // Xuất trực tiếp IP Gateway dùng để đếm token ra Response Header
            exchange.getResponse().getHeaders().set("X-Resolved-Client-IP", resolvedIp);

            return Mono.just(resolvedIp);
        };
    }
}