package ru.plusrep.fabrica;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;

public final class FactoryWonderServer {
    private static final int MAX_REQUEST_BYTES = 32 * 1024;
    private static final Map<String, Payment> PAYMENTS = new ConcurrentHashMap<>();
    private static final StarStore STAR_STORE = new StarStore(
        Path.of(System.getProperty("user.dir"), "server", "data", "stars.json")
    );
    private static final Map<String, Object> REPORT = Map.of(
        "collected", 184750,
        "goal", 300000,
        "updatedAt", "обновляется каждую неделю",
        "paid", List.of(
            "Аренда помещения — 42 300 ₽",
            "Продукты для мастерской — 28 900 ₽",
            "Проезд по маршрутам — 19 600 ₽",
            "Оборудование театра — 33 950 ₽"
        )
    );

    private FactoryWonderServer() {}

    public static void main(String[] args) throws IOException {
        int port = Integer.parseInt(System.getenv().getOrDefault("PORT", "3001"));
        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
        server.createContext("/api", FactoryWonderServer::handle);
        server.setExecutor(Executors.newFixedThreadPool(8));
        server.start();
        System.out.println("Фабрика чудес Java API: http://localhost:" + port);
    }

    private static void handle(HttpExchange exchange) throws IOException {
        addCorsHeaders(exchange);
        if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
            exchange.sendResponseHeaders(204, -1);
            exchange.close();
            return;
        }

        try {
            route(exchange);
        } catch (IllegalArgumentException exception) {
            sendJson(exchange, 400, Map.of("error", exception.getMessage()));
        } catch (IOException exception) {
            System.err.println("Ошибка API Фабрики чудес: " + exception.getMessage());
            sendJson(exchange, 500, Map.of("error", "Не удалось обработать запрос."));
        } catch (RuntimeException exception) {
            exception.printStackTrace(System.err);
            sendJson(exchange, 500, Map.of("error", "Внутренняя ошибка сервера."));
        } finally {
            exchange.close();
        }
    }

    private static void route(HttpExchange exchange) throws IOException {
        String method = exchange.getRequestMethod();
        String path = exchange.getRequestURI().getPath();

        if ("GET".equalsIgnoreCase(method) && "/api/report".equals(path)) {
            sendJson(exchange, 200, REPORT);
            return;
        }
        if ("GET".equalsIgnoreCase(method) && "/api/stars".equals(path)) {
            sendJson(exchange, 200, STAR_STORE.list());
            return;
        }
        if ("POST".equalsIgnoreCase(method) && "/api/payments".equals(path)) {
            createPayment(exchange);
            return;
        }
        String confirmPrefix = "/api/payments/";
        String confirmSuffix = "/confirm";
        if ("POST".equalsIgnoreCase(method) && path.startsWith(confirmPrefix) && path.endsWith(confirmSuffix)) {
            String paymentId = path.substring(confirmPrefix.length(), path.length() - confirmSuffix.length());
            confirmPayment(exchange, paymentId);
            return;
        }
        sendJson(exchange, 404, Map.of("error", "Маршрут не найден."));
    }

    private static void createPayment(HttpExchange exchange) throws IOException {
        Map<String, Object> request = readJsonObject(exchange);
        Object amountValue = request.get("amount");
        if (!(amountValue instanceof Number amountNumber)) {
            throw new IllegalArgumentException("Укажите сумму от 100 до 1 000 000 рублей.");
        }
        long amount = amountNumber.longValue();
        if (amountNumber.doubleValue() != amount || amount < 100 || amount > 1_000_000) {
            throw new IllegalArgumentException("Укажите сумму от 100 до 1 000 000 рублей.");
        }
        String project = stringValue(request.get("project")).trim();
        String name = stringValue(request.get("name")).trim();
        if (project.isEmpty()) throw new IllegalArgumentException("Не выбран проект.");
        if (name.isEmpty() || name.length() > 32) {
            throw new IllegalArgumentException("Укажите ник длиной не более 32 символов.");
        }

        String paymentId = UUID.randomUUID().toString();
        PAYMENTS.put(paymentId, new Payment(amount, project, name));
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("paymentId", paymentId);
        response.put("status", "test");
        response.put("amount", amount);
        response.put("confirmationUrl", null);
        sendJson(exchange, 201, response);
    }

    private static void confirmPayment(HttpExchange exchange, String paymentId) throws IOException {
        Payment payment = PAYMENTS.get(paymentId);
        if (payment == null) {
            sendJson(exchange, 404, Map.of("error", "Платёж не найден."));
            return;
        }
        synchronized (payment) {
            if (payment.star == null) {
                Map<String, Object> star = createStar(payment);
                STAR_STORE.add(star);
                payment.star = star;
            }
            sendJson(exchange, 200, Map.of(
                "paymentId", paymentId,
                "status", "succeeded",
                "test", true,
                "star", payment.star
            ));
        }
    }

    private static Map<String, Object> createStar(Payment payment) {
        double x = Math.random() < 0.5
            ? 5 + Math.random() * 15
            : 80 + Math.random() * 15;
        Map<String, Object> star = new LinkedHashMap<>();
        star.put("id", UUID.randomUUID().toString());
        star.put("name", payment.name);
        star.put("project", payment.project);
        star.put("amount", payment.amount);
        star.put("x", x);
        star.put("y", 8 + Math.random() * 50);
        star.put("createdAt", Instant.now().toString());
        return star;
    }

    private static Map<String, Object> readJsonObject(HttpExchange exchange) throws IOException {
        byte[] body = exchange.getRequestBody().readNBytes(MAX_REQUEST_BYTES + 1);
        if (body.length > MAX_REQUEST_BYTES) {
            throw new IllegalArgumentException("Размер запроса превышает допустимый.");
        }
        Object parsed;
        try {
            parsed = Json.parse(new String(body, StandardCharsets.UTF_8));
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Некорректный JSON.");
        }
        if (!(parsed instanceof Map<?, ?> map)) {
            throw new IllegalArgumentException("Ожидался JSON-объект.");
        }
        Map<String, Object> result = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            if (entry.getKey() instanceof String key) result.put(key, entry.getValue());
        }
        return result;
    }

    private static String stringValue(Object value) {
        return value instanceof String text ? text : "";
    }

    private static void addCorsHeaders(HttpExchange exchange) {
        exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        exchange.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
        exchange.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type");
    }

    private static void sendJson(HttpExchange exchange, int status, Object body) throws IOException {
        byte[] bytes = Json.stringify(body).getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
    }

    private static final class Payment {
        private final long amount;
        private final String project;
        private final String name;
        private Map<String, Object> star;

        private Payment(long amount, String project, String name) {
            this.amount = amount;
            this.project = project;
            this.name = name;
        }
    }

    private static final class StarStore {
        private final Path file;
        private final List<Map<String, Object>> stars = new ArrayList<>();

        private StarStore(Path file) {
            this.file = file;
            try {
                Files.createDirectories(file.getParent());
                if (!Files.exists(file)) Files.writeString(file, "[]\n", StandardCharsets.UTF_8);
                Object saved = Json.parse(Files.readString(file, StandardCharsets.UTF_8));
                if (!(saved instanceof List<?> list)) {
                    throw new IOException("Хранилище звёзд должно быть JSON-массивом.");
                }
                for (Object item : list) {
                    if (!(item instanceof Map<?, ?> map)) {
                        throw new IOException("Запись звезды должна быть JSON-объектом.");
                    }
                    Map<String, Object> star = new LinkedHashMap<>();
                    for (Map.Entry<?, ?> entry : map.entrySet()) {
                        if (entry.getKey() instanceof String key) star.put(key, entry.getValue());
                    }
                    stars.add(star);
                }
            } catch (IOException | IllegalArgumentException exception) {
                throw new IllegalStateException("Не удалось загрузить хранилище звёзд: " + exception.getMessage(), exception);
            }
        }

        private synchronized List<Map<String, Object>> list() {
            return new ArrayList<>(stars);
        }

        private synchronized void add(Map<String, Object> star) throws IOException {
            stars.add(star);
            Path temporaryFile = null;
            try {
                temporaryFile = Files.createTempFile(file.getParent(), "stars-", ".tmp");
                Files.writeString(
                    temporaryFile,
                    Json.stringify(stars) + System.lineSeparator(),
                    StandardCharsets.UTF_8
                );
                try {
                    Files.move(
                        temporaryFile,
                        file,
                        StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING
                    );
                } catch (AtomicMoveNotSupportedException exception) {
                    Files.move(temporaryFile, file, StandardCopyOption.REPLACE_EXISTING);
                }
            } catch (IOException exception) {
                stars.remove(stars.size() - 1);
                System.err.println("Не удалось сохранить звезду в " + file + ": " + exception.getMessage());
                throw exception;
            } finally {
                if (temporaryFile != null) Files.deleteIfExists(temporaryFile);
            }
        }
    }
}
