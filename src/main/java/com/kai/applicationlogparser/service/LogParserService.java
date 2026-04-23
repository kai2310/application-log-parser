package com.kai.applicationlogparser.service;

import com.kai.applicationlogparser.model.ParsedLogEntry;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;

@Service
public final class LogParserService {
    private static final DateTimeFormatter LOGBACK_ISO_8601_MILLIS = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss,SSS");
    private static final DateTimeFormatter LOGBACK_ISO_8601_MILLIS_DOT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");
    private static final DateTimeFormatter LOGBACK_ISO_8601_SECONDS = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final Pattern LOG_PATTERN = Pattern.compile(
            "^(?<timestamp>\\d{4}-\\d{2}-\\d{2}[ T]\\d{2}:\\d{2}:\\d{2}(?:[.,]\\d{3})?(?:Z|[+-]\\d{2}:?\\d{2})?) "
                    + "\\[(?<thread>[^\\]]+)]\\s+(?<cid>\\S*)\\s+(?<level>TRACE|DEBUG|INFO|WARN|ERROR|FATAL)\\s+"
                    + "(?<logger>\\S+) - (?<message>.*)$"
    );

    public List<ParsedLogEntry> parseFiles(List<Path> filePaths) throws IOException {
        return parseFiles(filePaths, ZoneId.of("UTC"));
    }

    public List<ParsedLogEntry> parseFiles(List<Path> filePaths, ZoneId fallbackZone) throws IOException {
        List<ParsedLogEntry> entries = new ArrayList<>();
        for (Path filePath : filePaths) {
            entries.addAll(parseFile(filePath, fallbackZone));
        }
        return entries;
    }

    private List<ParsedLogEntry> parseFile(Path filePath, ZoneId fallbackZone) throws IOException {
        List<ParsedLogEntry> entries = new ArrayList<>();
        MutableParsedLogEntry currentEntry = null;
        int lineNumber = 0;

        try (BufferedReader reader = Files.newBufferedReader(filePath, StandardCharsets.UTF_8)) {
            String line;
            while ((line = reader.readLine()) != null) {
                lineNumber++;
                Matcher matcher = LOG_PATTERN.matcher(line);
                if (matcher.matches()) {
                    if (currentEntry != null) {
                        entries.add(currentEntry.toImmutable());
                    }

                    currentEntry = new MutableParsedLogEntry(
                            parseTimestamp(matcher.group("timestamp"), fallbackZone),
                            matcher.group("thread").trim(),
                            matcher.group("cid") == null ? "" : matcher.group("cid").trim(),
                            matcher.group("level").trim(),
                            matcher.group("logger").trim(),
                            matcher.group("message"),
                            filePath.toAbsolutePath().toString(),
                            lineNumber
                    );
                } else if (currentEntry != null) {
                    currentEntry.continuationLines.add(line);
                }
            }
        }

        if (currentEntry != null) {
            entries.add(currentEntry.toImmutable());
        }

        return entries;
    }

    private OffsetDateTime parseTimestamp(String timestamp, ZoneId fallbackZone) {
        try {
            return OffsetDateTime.parse(timestamp, DateTimeFormatter.ISO_OFFSET_DATE_TIME);
        } catch (DateTimeParseException ex) {
            try {
                return OffsetDateTime.parse(timestamp + "Z", DateTimeFormatter.ISO_OFFSET_DATE_TIME);
            } catch (DateTimeParseException ignored) {
                try {
                    LocalDateTime localDateTime = LocalDateTime.parse(timestamp, LOGBACK_ISO_8601_MILLIS);
                    return localDateTime.atZone(fallbackZone).toOffsetDateTime();
                } catch (DateTimeParseException ignoredMillisComma) {
                    try {
                        LocalDateTime localDateTime = LocalDateTime.parse(timestamp, LOGBACK_ISO_8601_MILLIS_DOT);
                        return localDateTime.atZone(fallbackZone).toOffsetDateTime();
                    } catch (DateTimeParseException ignoredMillisDot) {
                        try {
                            LocalDateTime localDateTime = LocalDateTime.parse(timestamp, LOGBACK_ISO_8601_SECONDS);
                            return localDateTime.atZone(fallbackZone).toOffsetDateTime();
                        } catch (DateTimeParseException ignoredSeconds) {
                            return OffsetDateTime.now(fallbackZone);
                        }
                    }
                }
            }
        }
    }

    private static final class MutableParsedLogEntry {
        private final OffsetDateTime timestamp;
        private final String thread;
        private final String cid;
        private final String level;
        private final String logger;
        private final String message;
        private final String sourceFile;
        private final int sourceLine;
        private final List<String> continuationLines = new ArrayList<>();

        private MutableParsedLogEntry(
                OffsetDateTime timestamp,
                String thread,
                String cid,
                String level,
                String logger,
                String message,
                String sourceFile,
                int sourceLine) {
            this.timestamp = timestamp;
            this.thread = thread;
            this.cid = cid;
            this.level = level;
            this.logger = logger;
            this.message = message;
            this.sourceFile = sourceFile;
            this.sourceLine = sourceLine;
        }

        private ParsedLogEntry toImmutable() {
            return new ParsedLogEntry(
                    timestamp,
                    thread,
                    cid,
                    level,
                    logger,
                    message,
                    sourceFile,
                    sourceLine,
                    continuationLines
            );
        }
    }
}
