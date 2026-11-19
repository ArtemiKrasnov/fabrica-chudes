package ru.plusrep.fabrica;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

@RestControllerAdvice
public class ApiExceptionHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, String>> handleValidation(MethodArgumentNotValidException exception) {
        String message = exception.getBindingResult().getFieldErrors().stream()
            .map(error -> error.getDefaultMessage())
            .findFirst()
            .orElse("Некорректные данные запроса.");
        return ResponseEntity.badRequest().body(Map.of("error", message));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, String>> handleUnreadableBody() {
        return ResponseEntity.badRequest().body(Map.of("error", "Некорректный JSON."));
    }

    @ExceptionHandler(PaymentService.InvalidPaymentException.class)
    public ResponseEntity<Map<String, String>> handleInvalidPayment(
        PaymentService.InvalidPaymentException exception
    ) {
        return ResponseEntity.badRequest().body(Map.of("error", exception.getMessage()));
    }

    @ExceptionHandler(PaymentService.PaymentNotFoundException.class)
    public ResponseEntity<Map<String, String>> handleMissingPayment(
        PaymentService.PaymentNotFoundException exception
    ) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", exception.getMessage()));
    }

    @ExceptionHandler(StarStore.StarStorageException.class)
    public ResponseEntity<Map<String, String>> handleStorageFailure(
        StarStore.StarStorageException exception
    ) {
        LOGGER.error("Не удалось прочитать или сохранить хранилище звёзд донатеров.", exception);
        return ResponseEntity.internalServerError()
            .body(Map.of("error", "Не удалось сохранить донат. Попробуйте ещё раз."));
    }
}
