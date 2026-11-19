package ru.plusrep.fabrica;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class FactoryControllerTests {
    private static final Path STARS_FILE = Path.of(
        System.getProperty("java.io.tmpdir"),
        "fabrica-chudes-tests-" + UUID.randomUUID() + ".json"
    );

    @Autowired
    private MockMvc mockMvc;

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("fabrica.stars-file", STARS_FILE::toString);
    }

    @AfterAll
    static void removeTestData() throws IOException {
        Files.deleteIfExists(STARS_FILE);
    }

    @Test
    void servesReportAndCompletesTestDonationOnce() throws Exception {
        mockMvc.perform(get("/api/report"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.collected").value(184750))
            .andExpect(jsonPath("$.paid", hasSize(4)));

        mockMvc.perform(get("/api/stars"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(0)));

        mockMvc.perform(post("/api/payments")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"amount": 99, "project": "Проект", "name": "Проверка"}
                    """))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error").exists());

        String response = mockMvc.perform(post("/api/payments")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"amount": 1200, "project": "Тёплый дом", "name": "  Добрый жертвователь  "}
                    """))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.status").value("test"))
            .andExpect(jsonPath("$.amount").value(1200))
            .andReturn()
            .getResponse()
            .getContentAsString();
        String paymentId = com.fasterxml.jackson.databind.json.JsonMapper.builder()
            .build()
            .readTree(response)
            .get("paymentId")
            .asText();

        String confirmation = mockMvc.perform(post("/api/payments/{paymentId}/confirm", paymentId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("succeeded"))
            .andExpect(jsonPath("$.test").value(true))
            .andExpect(jsonPath("$.star.name").value("Добрый жертвователь"))
            .andReturn()
            .getResponse()
            .getContentAsString();

        mockMvc.perform(post("/api/payments/{paymentId}/confirm", paymentId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.star.id").value(
                com.fasterxml.jackson.databind.json.JsonMapper.builder()
                    .build()
                    .readTree(confirmation)
                    .get("star")
                    .get("id")
                    .asText()
            ));

        mockMvc.perform(get("/api/stars"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(1)))
            .andExpect(jsonPath("$[0].project").value("Тёплый дом"));
        org.junit.jupiter.api.Assertions.assertTrue(Files.exists(STARS_FILE));
    }
}
