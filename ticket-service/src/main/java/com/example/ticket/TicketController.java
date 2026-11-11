package com.example.ticket;

import java.util.List;
import java.util.stream.IntStream;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class TicketController {

    public record Ticket(int id, String name) {}

    // Giả lập "cơ sở dữ liệu": thực tế mỗi lần gọi là tốn CPU, kết nối, tiền.
    @GetMapping("/api/tickets")
    public List<Ticket> list() {
        return IntStream.rangeClosed(1, 5)
                .mapToObj(i -> new Ticket(i, "Vé hòa nhạc #" + i))
                .toList();
    }
}
