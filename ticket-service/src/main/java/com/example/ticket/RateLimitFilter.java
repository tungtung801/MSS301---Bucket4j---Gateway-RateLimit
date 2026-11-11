package com.example.ticket;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class RateLimitFilter extends OncePerRequestFilter {

    /**
     * Danh sách IP được phép đứng "nguồn cung" X-Forwarded-For: chỉ có gateway (chạy cùng máy).
     * Request từ địa chỉ khác không được đọc header này -> chống giả mạo danh tính.
     */
    private static final Set<String> TRUSTED_PROXIES = Set.of("127.0.0.1", "0:0:0:0:0:0:0:1", "::1");

    // Mỗi key (IP) một xô riêng. Production nên dùng Caffeine để tự dọn xô cũ.
    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/api/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {

        String key = clientKey(request);
        Bucket bucket = buckets.computeIfAbsent(key, k -> createBucket());

        ConsumptionProbe probe = bucket.tryConsumeAndReturnRemaining(1);

        if (probe.isConsumed()) {
            response.setHeader("X-RateLimit-Remaining", String.valueOf(probe.getRemainingTokens()));
            chain.doFilter(request, response);
            return;
        }

        long waitSeconds = Math.max(1, (long) Math.ceil(probe.getNanosToWaitForRefill() / 1_000_000_000.0));
        response.setStatus(429); // Too Many Requests
        response.setHeader("Retry-After", String.valueOf(waitSeconds));
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write("{\"error\":\"Too Many Requests\",\"retryAfterSeconds\":" + waitSeconds + "}");
    }

    private Bucket createBucket() {
        // capacity(10): xô chứa tối đa 10 token (cho phép burst 10)
        // refillGreedy(1, 1 giây): nạp đều 1 token mỗi giây (đồng bộ với replenishRate ở gateway)
        return Bucket.builder()
                .addLimit(limit -> limit.capacity(10).refillGreedy(1, Duration.ofSeconds(1)))
                .build();
    }

    /**
     * Key = danh tính client.
     * - Request đến trực tiếp: dùng getRemoteAddr() (không thể giả mạo).
     * - Request đến từ gateway (nằm trong TRUSTED_PROXIES): gateway đã ghi IP client thật
     *   vào X-Forwarded-For, lấy entry đầu tiên là IP gốc. Header từ nguồn khác bị bỏ qua.
     */
    private String clientKey(HttpServletRequest request) {
        String remoteAddr = request.getRemoteAddr();
        if (TRUSTED_PROXIES.contains(remoteAddr)) {
            String forwarded = request.getHeader("X-Forwarded-For");
            if (forwarded != null && !forwarded.isBlank()) {
                return forwarded.split(",")[0].trim();
            }
        }
        return remoteAddr;
    }
}
