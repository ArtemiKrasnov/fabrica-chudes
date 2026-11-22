package ru.plusrep.fabrica;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import java.util.Map;
import java.util.Base64;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

@Service
public class PaymentService {
    private final ConcurrentHashMap<String, Payment> payments = new ConcurrentHashMap<>();
    private final StarStore starStore;
    private final boolean paymentsEnabled;
    private final RestClient yookassaClient;
    private final String returnUrl;
    private final String shopId;

    public PaymentService(
        StarStore starStore,
        @Value("${fabrica.payments.enabled:false}") boolean paymentsEnabled,
        @Value("${fabrica.payments.shop-id:}") String shopId,
        @Value("${fabrica.payments.secret-key:}") String secretKey,
        @Value("${fabrica.payments.return-url:http://localhost:3001/}") String returnUrl
    ) {
        this.starStore = starStore;
        this.paymentsEnabled = paymentsEnabled;
        this.shopId = shopId;
        this.returnUrl = returnUrl;
        this.yookassaClient = paymentsEnabled
            ? RestClient.builder()
                .baseUrl("https://api.yookassa.ru/v3")
                .defaultHeader("Authorization", "Basic " + Base64.getEncoder()
                    .encodeToString((shopId + ":" + secretKey).getBytes(java.nio.charset.StandardCharsets.UTF_8)))
                .build()
            : null;
    }

    public PaymentCreated create(PaymentRequest request) {
        BigDecimal normalizedAmount = request.amount().stripTrailingZeros();
        if (normalizedAmount.scale() > 0) {
            throw new InvalidPaymentException("Укажите сумму от 100 до 1 000 000 рублей.");
        }

        long amount;
        try {
            amount = normalizedAmount.longValueExact();
        } catch (ArithmeticException exception) {
            throw new InvalidPaymentException("Укажите сумму от 100 до 1 000 000 рублей.");
        }

        String project = request.project().trim();
        String name = request.name().trim();
        if (project.isEmpty()) throw new InvalidPaymentException("Не выбран проект.");
        if (name.isEmpty() || name.length() > 32) {
            throw new InvalidPaymentException("Укажите ник длиной не более 32 символов.");
        }

        String paymentId = UUID.randomUUID().toString();
        payments.put(paymentId, new Payment(amount, project, name));

        if (paymentsEnabled) {
            if (shopId.isBlank()) throw new InvalidPaymentException("Платёжный провайдер не настроен.");

            try {
                JsonNode response = yookassaClient.post()
                    .uri("/payments")
                    .header("Idempotence-Key", paymentId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of(
                        "amount", Map.of("value", amount + ".00", "currency", "RUB"),
                        "capture", true,
                        "description", "Пожертвование: " + project,
                        "confirmation", Map.of("type", "redirect", "return_url", returnUrl),
                        "metadata", Map.of("project", project, "name", name)
                    ))
                    .retrieve()
                    .body(JsonNode.class);

                if (response == null) throw new InvalidPaymentException("Платёжный провайдер не ответил.");
                payments.get(paymentId).yookassaPaymentId = response.path("id").asText();
                String confirmationUrl = response.path("confirmation").path("confirmation_url").asText();
                if (confirmationUrl.isBlank()) throw new InvalidPaymentException("Не удалось создать ссылку оплаты.");
                return new PaymentCreated(
                    paymentId,
                    "pending",
                    amount,
                    confirmationUrl
                );
            } catch (RestClientResponseException exception) {
                throw new InvalidPaymentException("Не удалось создать платёж: проверьте настройки ЮKassa.");
            }
        }

        return new PaymentCreated(paymentId, "test", amount, null);
    }

    public PaymentConfirmed confirm(String paymentId) {
        Payment payment = payments.get(paymentId);
        if (payment == null) throw new PaymentNotFoundException();

        synchronized (payment) {
            if (paymentsEnabled) {
                if (payment.yookassaPaymentId == null) {
                    return new PaymentConfirmed(paymentId, "pending", false, null);
                }
                try {
                    JsonNode response = yookassaClient.get()
                        .uri("/payments/{id}", payment.yookassaPaymentId)
                        .retrieve()
                        .body(JsonNode.class);
                    String status = response == null ? "pending" : response.path("status").asText("pending");
                    if (!"succeeded".equals(status)) {
                        return new PaymentConfirmed(paymentId, status, false, null);
                    }
                } catch (RestClientResponseException exception) {
                    throw new InvalidPaymentException("Не удалось проверить статус платежа.");
                }
            }

            if (payment.star == null) {
                Star star = createStar(payment);
                starStore.add(star);
                payment.star = star;
            }
            return new PaymentConfirmed(paymentId, "succeeded", true, payment.star);
        }
    }

    public PaymentConfirmed confirmWebhook(String yookassaPaymentId) {
        for (Map.Entry<String, Payment> entry : payments.entrySet()) {
            if (yookassaPaymentId.equals(entry.getValue().yookassaPaymentId)) {
                return confirm(entry.getKey());
            }
        }
        throw new PaymentNotFoundException();
    }

    private Star createStar(Payment payment) {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        double x = random.nextBoolean()
            ? random.nextDouble(5, 20)
            : random.nextDouble(80, 95);
        return new Star(
            UUID.randomUUID().toString(),
            payment.name,
            payment.project,
            payment.amount,
            x,
            random.nextDouble(8, 58),
            Instant.now()
        );
    }

    public record PaymentCreated(String paymentId, String status, long amount, String confirmationUrl) {}

    public record PaymentConfirmed(String paymentId, String status, boolean test, Star star) {}

    private static final class Payment {
        private final long amount;
        private final String project;
        private final String name;
        private Star star;
        private String yookassaPaymentId;

        private Payment(long amount, String project, String name) {
            this.amount = amount;
            this.project = project;
            this.name = name;
        }
    }

    public static final class InvalidPaymentException extends RuntimeException {
        public InvalidPaymentException(String message) {
            super(message);
        }
    }

    public static final class PaymentNotFoundException extends RuntimeException {
        public PaymentNotFoundException() {
            super("Платёж не найден.");
        }
    }
}
