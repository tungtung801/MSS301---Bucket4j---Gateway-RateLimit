import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.concurrent.ThreadLocalRandom;

public class RetryingClient {

    public static void main(String[] args) throws Exception {
        HttpClient client = HttpClient.newHttpClient();
        HttpRequest req = HttpRequest.newBuilder(URI.create("http://localhost:8080/api/tickets")).GET().build();

        long backoffMs = 1000;
        for (int attempt = 1; attempt <= 5; attempt++) {
            HttpResponse<String> res = client.send(req, HttpResponse.BodyHandlers.ofString());
            if (res.statusCode() != 429) {
                System.out.println("OK: " + res.body());
                return;
            }
            long waitMs = res.headers().firstValue("Retry-After")
                    .map(s -> Long.parseLong(s) * 1000)
                    .orElse(backoffMs);
            long jitter = ThreadLocalRandom.current().nextLong(0, 300); // để không ai quay lại cùng lúc
            System.out.printf("lần %d bị 429, đợi %d ms%n", attempt, waitMs + jitter);
            Thread.sleep(waitMs + jitter);
            backoffMs *= 2; // 1s -> 2s -> 4s -> 8s
        }
        System.out.println("Bỏ cuộc sau 5 lần.");
    }
}
