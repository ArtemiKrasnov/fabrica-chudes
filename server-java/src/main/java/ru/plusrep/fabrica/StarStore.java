package ru.plusrep.fabrica;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;

@Component
public class StarStore {
    private final Path file;
    private final ObjectMapper objectMapper;
    private final List<Star> stars = new ArrayList<>();

    public StarStore(
        ObjectMapper objectMapper,
        @Value("${fabrica.stars-file:./server/data/stars.json}") String file
    ) {
        this.objectMapper = objectMapper;
        this.file = Path.of(file).toAbsolutePath().normalize();
        load();
    }

    public synchronized List<Star> list() {
        return List.copyOf(stars);
    }

    public synchronized void add(Star star) {
        List<Star> updated = new ArrayList<>(stars);
        updated.add(star);
        Path temporaryFile = null;
        try {
            Files.createDirectories(file.getParent());
            temporaryFile = Files.createTempFile(file.getParent(), "stars-", ".tmp");
            Files.writeString(
                temporaryFile,
                objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(updated) + System.lineSeparator(),
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
            stars.clear();
            stars.addAll(updated);
        } catch (IOException exception) {
            throw new StarStorageException("Не удалось сохранить звезду донатера.", exception);
        } finally {
            if (temporaryFile != null) {
                try {
                    Files.deleteIfExists(temporaryFile);
                } catch (IOException exception) {
                    System.err.println("Не удалось удалить временный файл звезды: " + exception.getMessage());
                }
            }
        }
    }

    private void load() {
        try {
            Files.createDirectories(file.getParent());
            try {
                Files.writeString(
                    file,
                    "[]" + System.lineSeparator(),
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE_NEW,
                    StandardOpenOption.WRITE
                );
            } catch (FileAlreadyExistsException exception) {
                // Another server process created the store before this one.
            }

            List<Star> saved = objectMapper.readValue(file.toFile(), new TypeReference<>() {});
            if (saved == null || saved.stream().anyMatch(star -> star == null)) {
                throw new IOException("Хранилище звёзд должно быть JSON-массивом объектов.");
            }
            stars.addAll(saved);
        } catch (IOException exception) {
            throw new StarStorageException("Не удалось загрузить хранилище звёзд.", exception);
        }
    }

    public static final class StarStorageException extends RuntimeException {
        public StarStorageException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
