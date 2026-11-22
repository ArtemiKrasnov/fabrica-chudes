package ru.plusrep.fabrica;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import com.fasterxml.jackson.databind.JsonNode;

@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "*")
public class FactoryController {
    private final PaymentService paymentService;
    private final StarStore starStore;

    public FactoryController(PaymentService paymentService, StarStore starStore) {
        this.paymentService = paymentService;
        this.starStore = starStore;
    }

    @GetMapping("/report")
    public ReportResponse report() {
        return new ReportResponse(
            184750,
            300000,
            "обновляется каждую неделю",
            List.of(
                "Аренда помещения — 42 300 ₽",
                "Продукты для мастерской — 28 900 ₽",
                "Проезд по маршрутам — 19 600 ₽",
                "Оборудование театра — 33 950 ₽"
            )
        );
    }

    @GetMapping("/stars")
    public List<Star> stars() {
        return starStore.list();
    }

    @PostMapping("/payments")
    public ResponseEntity<PaymentService.PaymentCreated> createPayment(
        @Valid @RequestBody PaymentRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(paymentService.create(request));
    }

    @PostMapping("/payments/{paymentId}/confirm")
    public PaymentService.PaymentConfirmed confirmPayment(@PathVariable String paymentId) {
        return paymentService.confirm(paymentId);
    }

    @PostMapping("/payments/webhook")
    public ResponseEntity<Void> paymentWebhook(@RequestBody JsonNode webhook) {
        if (!"payment.succeeded".equals(webhook.path("event").asText())) {
            return ResponseEntity.ok().build();
        }
        String paymentId = webhook.path("object").path("id").asText();
        if (!paymentId.isBlank()) {
            paymentService.confirmWebhook(paymentId);
        }
        return ResponseEntity.ok().build();
    }

    public record ReportResponse(long collected, long goal, String updatedAt, List<String> paid) {}
}
