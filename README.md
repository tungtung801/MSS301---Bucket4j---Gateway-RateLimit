# 🛡️ Two-Layer Distributed Rate Limiting & IP Spoofing Mitigation Demo

Dự án minh họa kiến trúc **Rate Limiting hai lớp (Defense-in-Depth)** trong hệ thống Microservices sử dụng **Spring Cloud Gateway (Redis Lua Script)** kết hợp với **Bucket4j** tại tầng Service, đồng thời phân tích và khắc phục lỗ hổng **giả mạo địa chỉ IP (`X-Forwarded-For` Spoofing)** thông qua cấu hình **Nginx Reverse Proxy**.

---

## 1. Kiến trúc hệ thống (Architecture Overview)

```text
                       ┌──────────────────────────────────────────┐
                       │          Client / Postman Runner         │
                       └────────────────────┬─────────────────────┘
                                            │
              ┌─────────────────────────────┼─────────────────────────────┐
              ▼ (Port 8001: Lỗi tin XFF)    ▼ (Port 8002: Vá lỗi XFF)     ▼ (Port 8080: Direct)
   ┌─────────────────────┐       ┌─────────────────────┐                  │
   │    Nginx Proxy      │       │    Nginx Proxy      │                  │
   │ $proxy_add_x_forw.. │       │    $remote_addr     │                  │
   └──────────┬──────────┘       └──────────┬──────────┘                  │
              └─────────────────────────────┼─────────────────────────────┘
                                            ▼
                       ┌──────────────────────────────────────────┐
                       │      Spring Cloud Gateway (Port 8080)    │
                       │  • Filter: RequestRateLimiter (Redis)    │
                       │  • Token Bucket: Burst=8, Rate=1, Cost=2 │
                       │  • Header: X-RateLimit-Remaining         │
                       │  • Header: X-Resolved-Client-IP          │
                       └────────────────────┬─────────────────────┘
                                            │ (Chỉ cho phép request hợp lệ)
                                            ▼
                       ┌──────────────────────────────────────────┐
                       │         Ticket Service (Port 8081)       │
                       │  • Filter: Bucket4j (In-Memory)          │
                       │  • Token Bucket: Capacity=10             │
                       │  • Header: X-Service-RateLimit-Remaining │
                       └──────────────────────────────────────────┘
```

### Điểm nhấn kỹ thuật
1. **Lớp 1 — API Gateway (Distributed Rate Limiting):** Sử dụng Redis Lua Script thực thi nguyên tử (Atomic) thuật toán Token Bucket theo IP client (`burstCapacity: 8`, `replenishRate: 1`, `requestedTokens: 2`), chặn đứng lưu lượng spam ngay tại biên trước khi chạm tới Backend.
2. **Lớp 2 — Ticket Service (Service-level Protection):** Sử dụng thư viện `Bucket4j` (`capacity: 10`) làm chốt chặn dự phòng độc lập, tránh xung đột header thông qua tên định danh riêng `X-Service-RateLimit-Remaining`.
3. **Bảo mật lớp Proxy (Anti IP Spoofing):** So sánh trực quan giữa cấu hình nối tiếp header `$proxy_add_x_forwarded_for` (Port `8001`) và ghi đè địa chỉ kết nối TCP thật `$remote_addr` (Port `8002`) trên Nginx, kiểm chứng thông qua response header `X-Resolved-Client-IP`.

---

## 2. Yêu cầu môi trường (Prerequisites)

* **Java Development Kit (JDK):** 17 hoặc 21
* **Maven:** 3.8+ (hoặc sử dụng Maven Wrapper `./mvnw` đi kèm)
* **Docker & Docker Compose:** Để khởi chạy Redis và Nginx Reverse Proxy
* **Postman:** Để chạy kịch bản kiểm thử tự động (Collection Runner)

---

## 3. Hướng dẫn cài đặt & Khởi chạy (Setup & Run)

### Bước 1: Clone repository
```bash
git clone https://github.com/tungtung801/MSS301---Bucket4j---Gateway-RateLimit.git
cd MSS301---Bucket4j---Gateway-RateLimit
```

### Bước 2: Khởi động hạ tầng Docker (Redis & Nginx)
Chạy lệnh sau tại thư mục gốc chứa file `docker-compose.yml` và `nginx.conf`:
```bash
docker-compose up -d
```
Kiểm tra các container đang hoạt động:
* `rate-limit-redis`: Cổng `6379` (Lưu trữ trạng thái Token Bucket của Gateway)
* `nginx-proxy`:
  * Cổng `8001`: Reverse Proxy cấu hình **mắc lỗi** (`X-Forwarded-For $proxy_add_x_forwarded_for`)
  * Cổng `8002`: Reverse Proxy cấu hình **an toàn** (`X-Forwarded-For $remote_addr`)

### Bước 3: Khởi chạy các Microservices (Spring Boot)
Mở 2 cửa sổ Terminal riêng biệt để chạy **Ticket Service** và **API Gateway**:

**1. Chạy Ticket Service (Port 8081):**
```bash
cd ticket-service
./mvnw spring-boot:run
```

**2. Chạy Spring Cloud Gateway (Port 8080):**
```bash
cd api-gateway
./mvnw spring-boot:run
```

---

## 4. Kịch bản Demo & Kiểm thử trên Postman (4 Cases)

Import file Postman Collection trong thư mục `/postman` (nếu có) hoặc thiết lập **Collection Runner** (`Iterations = 10`, `Delay = 0 ms`) để kiểm chứng lần lượt 4 kịch bản:

| Case | Endpoint | Mô tả kịch bản | Kết quả kỳ vọng |
| :--- | :--- | :--- | :--- |
| **Case 1 & 2** | `GET http://localhost:8080/api/tickets` | Bắn trực tiếp 10 request liên tiếp vào Gateway. Xô có `8` token, mỗi request tốn `2` token. | **4 request đầu `200 OK`** (`Remaining`: `6` → `4` → `2` → `0`). Từ **lần 5 trở đi bị chặn `429 Too Many Requests`** với body rỗng (`content-length: 0`). |
| **Case 3** | `GET http://localhost:8001/api/tickets` | Gửi 10 request qua Nginx cổng `8001` kèm header giả mạo `X-Forwarded-For: 10.0.0.1` → `10.0.0.10`. | **10/10 request đều lọt `200 OK`**. Header `X-Resolved-Client-IP` nhận nhầm từng IP giả nên Redis tạo 10 xô mới tinh (`Remaining` luôn là `6`). |
| **Case 4** | `GET http://localhost:8002/api/tickets` | Gửi y hệt 10 request giả mạo IP trên qua Nginx cổng `8002` (`$remote_addr`). | Header `X-Resolved-Client-IP` khóa cứng về duy nhất IP mạng thật (VD: `172.18.0.1`). **Chỉ 4 request đầu `200 OK`, từ lần 5 bị chặn `429 Blocked`**. |

### Script Postman tự động tăng IP giả (Dùng cho Case 3 & Case 4)
* **Tab `Headers`:** Thêm key `X-Forwarded-For` với giá trị `{{fakeIp}}`
* **Tab `Scripts` → `Before request`:**
  ```javascript
  const iter = (typeof pm.info.iteration === 'number') ? (pm.info.iteration + 1) : 1;
  pm.variables.set("fakeIp", `10.0.0.${iter}`);
  ```
* **Tab `Scripts` → `After response`:**
  ```javascript
  const iter = (typeof pm.info.iteration === 'number') ? (pm.info.iteration + 1) : 1;
  const fakeIp = pm.variables.get("fakeIp") || `10.0.0.${iter}`;
  const remaining = pm.response.headers.get("X-RateLimit-Remaining");

  if (pm.response.code === 200) {
    pm.test(`Lần ${iter} [IP giả: ${fakeIp}]: (200 OK) - Gateway còn ${remaining} token`, () => {
        pm.expect(pm.response.code).to.equal(200);
    });
  } else {
    pm.test(`Lần ${iter} [IP giả: ${fakeIp}]: (429 Blocked)`, () => {
        pm.expect(pm.response.code).to.equal(429);
    });
  }
  ```


