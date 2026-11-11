# Demo: Bucket4j + Spring Cloud Gateway Rate Limiter

Hai lớp rate limit trong cùng một hệ thống, trình tự demo từ ngoài vào trong:

1. api-gateway/    : Spring Cloud Gateway + RequestRateLimiter + Redis   -> cổng 8080 (lớp ngoài)
2. ticket-service/ : API bán vé (Spring Boot 3, Java 17) + Bucket4j Filter -> cổng 8081 (lớp trong)
3. docker-compose.yml: chạy Redis (cổng 6379)
4. scripts/spam.sh : bắn 40 request để quan sát mã 429

## Cấu hình rate limit

Hai lớp dùng cùng đơn vị và cùng con số:

| Lớp | Thư viện | Nạp lại | Dung lượng xô | Token tốn/request | Vị trí lưu trạng thái |
|---|---|---|---|---|---|
| Gateway :8080 | RedisRateLimiter (Spring Cloud Gateway) | 1 token/giây | 10 token | 1 | Redis — dùng chung mọi instance gateway |
| Service :8081 | Bucket4j | 1 token/giây | 10 token | 1 | Bộ nhớ trong — mỗi instance một bộ riêng |

## Chạy nhanh
```bash
docker compose up -d                 # bật Redis
cd ticket-service && ./mvnw spring-boot:run   # hoặc: mvn spring-boot:run
cd api-gateway    && ./mvnw spring-boot:run
./scripts/spam.sh                    # xem 200 ... rồi 429
./scripts/spam.sh http://localhost:8081/api/tickets   # bắn thẳng service
```

## Luồng xử lý

### Luồng 1 — Gọi qua gateway (:8080)
```
Request -> Route matching (Path=/api/tickets/**)
        -> KeyResolver: lấy IP client từ remoteAddress
        -> RedisRateLimiter: trừ token trong Redis (Lua script, atomic)
              còn token   -> forward xuống ticket-service
              hết token   -> 429 ngay tại gateway
        -> ticket-service xử lý, phản hồi đi ngược về client
```

### Luồng 2 — Gọi thẳng service (:8081)
```
Request -> RateLimitFilter (chỉ áp dụng URI bắt đầu bằng /api/)
        -> clientKey: khóa (key) của xô
        -> computeIfAbsent: tạo xô mới nếu key chưa có
        -> tryConsumeAndReturnRemaining:
              còn token   -> 200 + header X-RateLimit-Remaining
              hết token   -> 429 + header Retry-After + body JSON
        -> TicketController
```

## Phân biệt 429 đến từ lớp nào

| Tín hiệu | 429 từ Gateway | 429 từ Service |
|---|---|---|
| Body | rỗng | `{"error":"Too Many Requests","retryAfterSeconds":N}` |
| Header Retry-After | không | có |
| Header X-RateLimit-Remaining / Burst-Capacity / Replenish-Rate | do RedisRateLimiter trả | không |
| Header X-RateLimit-Remaining khi thành công (200) | do RedisRateLimiter trả | do Bucket4j trả |

## Danh tính client ở tầng service

- Request đến trực tiếp: RateLimitFilter dùng `getRemoteAddr()` — không thể giả mạo.
- Request đến từ gateway: gateway ghi IP client thật vào header `X-Forwarded-For`.
  Filter chỉ đọc header này khi địa chỉ kết nối thuộc `TRUSTED_PROXIES` (gateway chạy trên cùng máy).
  Header cùng tên từ nguồn khác bị bỏ qua.
- Kết quả: mỗi client (theo IP) có xô riêng ở tầng service, kể cả khi đi qua gateway.

## Giới hạn đã biết

- `Map<String, Bucket>` trong RateLimitFilter không tự xóa entry cũ; số entry tăng theo số IP unique. Trong production nên dùng Caffeine với thời hạn sống cho mỗi entry.
- Trạng thái Bucket4j nằm trong bộ nhớ instance: nếu service chạy nhiều instance, mỗi instance sẽ có một bộ xô riêng.
- Gateway vẫn yêu cầu cấu hình `lb://ticket-service` nếu muốn chuyển sang LoadBalancer thay vì URI cứng.

## Redis không khả dụng

- Tầng gateway không thực hiện check token được — request qua gateway sẽ gặp lỗi (mã 500) cho đến khi Redis hoạt động trở lại.
- Tầng service hoạt động bình thường, không phụ thuộc Redis.

## Lưu ý phiên bản
- Spring Cloud 2023.0.x (Boot 3.3.x) dùng tiền tố  spring.cloud.gateway.routes
- Từ Spring Cloud 2025.0 tiền tố có thể đổi thành spring.cloud.gateway.server.webflux.routes
  -> luôn đối chiếu tài liệu đúng phiên bản bạn dùng.
