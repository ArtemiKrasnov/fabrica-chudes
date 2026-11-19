package ru.plusrep.fabrica;

import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

@Service
public class PaymentService {
    private final ConcurrentHashMap<String, Payment> payments = new ConcurrentHashMap<>();
    private final StarStore starStore;

    public PaymentService(StarStore starStore) {
        this.starStore = starStore;
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
        return new PaymentCreated(paymentId, "test", amount, null);
    }

    public PaymentConfirmed confirm(String paymentId) {
        Payment payment = payments.get(paymentId);
        if (payment == null) throw new PaymentNotFoundException();

        synchronized (payment) {
            if (payment.star == null) {
                Star star = createStar(payment);
                starStore.add(star);
                payment.star = star;
            }
            return new PaymentConfirmed(paymentId, "succeeded", true, payment.star);
        }
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
