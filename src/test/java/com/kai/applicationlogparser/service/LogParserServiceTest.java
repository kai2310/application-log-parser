package com.kai.applicationlogparser.service;

import com.kai.applicationlogparser.model.ParsedLogEntry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LogParserServiceTest {

    private final LogParserService service = new LogParserService();

    @TempDir
    Path tempDir;

    @ParameterizedTest
    @MethodSource("timestampFormats")
    void parseFiles_parsesMultipleTimestampFormats(String timestamp, int expectedSecond) throws IOException {
        Path file = tempDir.resolve("logs-" + expectedSecond + ".log");
        String line = timestamp + " [main] cid-1 ERROR com.example.Logger - boom happened";
        Files.writeString(file, line + System.lineSeparator());

        List<ParsedLogEntry> entries = service.parseFiles(List.of(file));

        assertEquals(1, entries.size());
        assertEquals("ERROR", entries.get(0).level());
        assertEquals(expectedSecond, entries.get(0).timestamp().getSecond());
    }

    static Stream<Arguments> timestampFormats() {
        return Stream.of(
                Arguments.of("2026-01-02T03:04:05Z", 5),
                Arguments.of("2026-01-02 03:04:06", 6),
                Arguments.of("2026-01-02 03:04:07,123", 7),
                Arguments.of("2026-01-02 03:04:08.456", 8)
        );
    }

    @Test
    void parseFiles_usesProvidedTimezoneForOffsetlessTimestamp() throws IOException {
        Path file = tempDir.resolve("timezone.log");
        String line = "2026-01-02 03:04:05 [main] cid-1 ERROR com.example.Logger - boom happened";
        Files.writeString(file, line + System.lineSeparator());

        List<ParsedLogEntry> entries = service.parseFiles(List.of(file), ZoneId.of("America/New_York"));

        assertEquals(1, entries.size());
        assertEquals(OffsetDateTime.parse("2026-01-02T03:04:05-05:00"), entries.get(0).timestamp());
    }

    @Test
    void parseFiles_appendsContinuationLinesToCurrentEntry() throws IOException {
        Path file = tempDir.resolve("continuation.log");
        String content = String.join(System.lineSeparator(),
                "2026-01-02 03:04:05 [main] cid-1 ERROR com.example.Logger - failed request",
                "at com.example.Service.run(Service.java:12)",
                "Caused by: java.lang.IllegalStateException: bad state",
                "2026-01-02 03:04:06 [main] cid-2 INFO com.example.Logger - recovered");
        Files.writeString(file, content + System.lineSeparator());

        List<ParsedLogEntry> entries = service.parseFiles(List.of(file));

        assertEquals(2, entries.size());
        assertEquals(2, entries.get(0).continuationLines().size());
        assertTrue(entries.get(0).continuationLines().get(0).startsWith("at "));
        assertEquals("INFO", entries.get(1).level());
        assertTrue(entries.get(1).continuationLines().isEmpty());
    }

    @Test
    void parseFiles_skipsLeadingInvalidLinesBeforeFirstLogEntry() throws IOException {
        Path file = tempDir.resolve("invalid-prefix.log");
        String content = String.join(System.lineSeparator(),
                "this line should be ignored",
                "another ignored line",
                "2026-01-02 03:04:05 [main] cid-1 WARN com.example.Logger - warn message");
        Files.writeString(file, content + System.lineSeparator());

        List<ParsedLogEntry> entries = service.parseFiles(List.of(file));

        assertEquals(1, entries.size());
        assertEquals("WARN", entries.get(0).level());
        assertEquals(3, entries.get(0).sourceLine());
        assertTrue(entries.get(0).continuationLines().isEmpty());
    }
}
