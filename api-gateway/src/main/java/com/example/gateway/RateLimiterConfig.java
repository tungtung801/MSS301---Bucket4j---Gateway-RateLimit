package com.example.gateway;

import java.util.Optional;
import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import reactor.core.publisher.Mono;

@Configuration
public class RateLimiterConfig {

    @Bean
    KeyResolver ipKeyResolver() {
        return exchange -> {
            // Khi dùng proxy, đọc header từ proxy
            String xff = exchange.getRequest().getHeaders().getFirst("X-Forwarded-For");

            if(xff != null && !xff.isBlank()){
                return Mono.just(xff.split(",")[0].trim());
            }

            // Fallback khi run thẳng vào service bỏ qua proxy
            var remoteAddress = exchange.getRequest().getRemoteAddress();
            if(remoteAddress != null && remoteAddress.getAddress() != null){
                return Mono.just(remoteAddress.getAddress().getHostAddress());
            }

            return Mono.just("anonymous-client");
        };
    }
}
