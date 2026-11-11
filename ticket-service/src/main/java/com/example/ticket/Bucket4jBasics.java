package com.example.ticket;

import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import java.time.Duration;

/** Chạy main() để thấy token bucket hoạt động: capacity 5, nạp 1 token / giây. */
public class Bucket4jBasics {

    public static void main(String[] args) throws Exception {
        Bucket bucket = Bucket.builder()
                .addLimit(limit -> limit.capacity(5).refillGreedy(1, Duration.ofSeconds(1)))
                .build();

        for (int i = 1; i <= 7; i++) {
            ConsumptionProbe p = bucket.tryConsumeAndReturnRemaining(1);
            System.out.printf("request %d -> %s | còn %d token | đợi %d ms%n",
                    i,
                    p.isConsumed() ? "QUA " : "429 ",
                    p.getRemainingTokens(),
                    p.getNanosToWaitForRefill() / 1_000_000);
        }

        Thread.sleep(1100); // một giây trôi qua -> 1 token rơi vào xô
        System.out.println("sau 1.1 giây: " + (bucket.tryConsume(1) ? "QUA" : "429"));
    }
}
