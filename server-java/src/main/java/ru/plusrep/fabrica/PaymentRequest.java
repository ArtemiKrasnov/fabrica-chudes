package ru.plusrep.fabrica;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record PaymentRequest(
    @NotNull(message = "Укажите сумму от 100 до 1 000 000 рублей.")
    @DecimalMin(value = "100", message = "Укажите сумму от 100 до 1 000 000 рублей.")
    @DecimalMax(value = "1000000", message = "Укажите сумму от 100 до 1 000 000 рублей.")
    BigDecimal amount,
    @NotBlank(message = "Не выбран проект.")
    String project,
    @NotBlank(message = "Укажите ник длиной не более 32 символов.")
    @Size(max = 32, message = "Укажите ник длиной не более 32 символов.")
    String name
) {}
