package com.kai.applicationlogparser.service;

import com.kai.applicationlogparser.dto.GenerateReportResponse;
import com.kai.applicationlogparser.api.InvalidTimezoneException;
import com.kai.applicationlogparser.model.IssueRecord;
import com.kai.applicationlogparser.model.IssueType;
import com.kai.applicationlogparser.model.ParsedLogEntry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ReportGenerationServiceTest {
    private static final ZoneId REPORT_ZONE = ZoneId.of("America/Los_Angeles");

    @TempDir
    Path tempDir;

    @AfterEach
    void cleanupReportsDirectory() throws IOException {
        Path reportsDir = Paths.get("reports");
        if (Files.exists(reportsDir)) {
            Files.walk(reportsDir)
                    .sorted((a, b) -> b.getNameCount() - a.getNameCount())
                    .forEach(path -> {
                        try {
                            Files.deleteIfExists(path);
                        } catch (IOException ignored) {
                            // Best effort cleanup for tests that write report files.
                        }
                    });
        }
    }

    @ParameterizedTest
    @MethodSource("invalidFilePathInputs")
    void generateReportRejectsInvalidFilePaths(List<String> inputPaths, String expectedMessage) {
        ReportGenerationService service = new ReportGenerationService(
                new LogAnalysisService(new LogParserService(), new IssueAnalyzerService())
        );

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> service.generateReport(inputPaths)
        );

        assertEquals(expectedMessage, exception.getMessage());
    }

    @Test
    void generateReportReturnsSummaryAndIgnoredFiles() throws Exception {
        LogParserService logParserService = mock(LogParserService.class);
        IssueAnalyzerService issueAnalyzerService = mock(IssueAnalyzerService.class);
        ReportGenerationService service = new ReportGenerationService(
                new LogAnalysisService(logParserService, issueAnalyzerService)
        );

        Path validLogFile = Files.createFile(tempDir.resolve("application.log"));
        String blankPath = " ";
        String missingPath = tempDir.resolve("missing.log").toString();

        List<ParsedLogEntry> parsedEntries = List.of(
                parsedEntry("ERROR", "memory leak detected", "logger-a"),
                parsedEntry("ERROR", "NullPointerException happened", "logger-b")
        );
        when(logParserService.parseFiles(anyList(), any(ZoneId.class))).thenReturn(parsedEntries);

        IssueRecord criticalIssue = new IssueRecord(
                IssueType.CRITICAL,
                "memory leak detected",
                "memory leak",
                List.of("memory leak"),
                List.of(zonedNow()),
                List.of("at com.app.Memory.consume(Memory.java:18)"),
                "logger-a"
        );
        IssueRecord errorIssue = new IssueRecord(
                IssueType.ERROR,
                "NullPointerException happened",
                "NullPointerException",
                List.of("NullPointerException"),
                List.of(zonedNow()),
                List.of(),
                "logger-b"
        );
        IssueRecord warningIssue = new IssueRecord(
                IssueType.WARNING,
                "Connection pool usage is high",
                "Cause not explicitly available in logs",
                List.of(),
                List.of(zonedNow()),
                List.of("at com.app.pool.ConnectionPool.monitor(ConnectionPool.java:42)"),
                "logger-c"
        );
        when(issueAnalyzerService.analyze(parsedEntries, REPORT_ZONE))
                .thenReturn(new IssueAnalyzerService.AnalysisResult(List.of(criticalIssue), List.of(errorIssue), List.of(warningIssue)));

        GenerateReportResponse response = service.generateReport(
                List.of(validLogFile.toString(), blankPath, missingPath),
                "America/New_York"
        );

        assertNotNull(response.reportPath());
        assertTrue(response.reportPath().contains("reports/application-logs-report-"));
        assertEquals(parsedEntries.size(), response.totalEntriesProcessed());
        assertEquals(1, response.criticalIssueGroups());
        assertEquals(1, response.errorIssueGroups());
        assertEquals(1, response.warningIssueGroups());
        assertEquals(List.of(blankPath, missingPath), response.ignoredFiles());
        assertEquals("Report generated successfully", response.message());

        ArgumentCaptor<List<Path>> validPathCaptor = ArgumentCaptor.forClass(List.class);
        ArgumentCaptor<ZoneId> zoneCaptor = ArgumentCaptor.forClass(ZoneId.class);
        verify(logParserService, times(1)).parseFiles(validPathCaptor.capture(), zoneCaptor.capture());
        List<Path> passedPaths = validPathCaptor.getValue();
        assertEquals(1, passedPaths.size());
        assertEquals(validLogFile.toAbsolutePath().normalize(), passedPaths.getFirst());
        assertEquals(ZoneId.of("America/New_York"), zoneCaptor.getValue());

        Path reportPath = Paths.get(response.reportPath());
        assertTrue(Files.exists(reportPath));
        String reportText = Files.readString(reportPath, StandardCharsets.UTF_8);
        assertTrue(reportText.contains("1) CRITICAL ISSUES"));
        assertTrue(reportText.contains("2) ERROR ISSUES"));
        assertTrue(reportText.contains("3) WARNING ISSUES"));
        assertTrue(reportText.contains("memory leak detected"));
        assertTrue(reportText.contains("NullPointerException happened"));
        assertTrue(reportText.contains("Connection pool usage is high"));
        assertFalse(reportText.contains("No critical issues, errors, or warnings were detected"));
        assertTrue(reportText.contains("Timezone: America/Los_Angeles"));
    }

    @Test
    void generateReportWritesSummaryWhenNoIssuesFound() throws Exception {
        LogParserService logParserService = mock(LogParserService.class);
        IssueAnalyzerService issueAnalyzerService = mock(IssueAnalyzerService.class);
        ReportGenerationService service = new ReportGenerationService(
                new LogAnalysisService(logParserService, issueAnalyzerService)
        );

        Path validLogFile = Files.createFile(tempDir.resolve("clean.log"));
        List<ParsedLogEntry> parsedEntries = List.of(parsedEntry("INFO", "Startup finished", "logger-clean"));
        when(logParserService.parseFiles(anyList(), any(ZoneId.class))).thenReturn(parsedEntries);
        when(issueAnalyzerService.analyze(parsedEntries, REPORT_ZONE))
                .thenReturn(new IssueAnalyzerService.AnalysisResult(List.of(), List.of(), List.of()));

        GenerateReportResponse response = service.generateReport(List.of(validLogFile.toString()), null);
        String reportText = Files.readString(Paths.get(response.reportPath()), StandardCharsets.UTF_8);

        assertTrue(reportText.contains("No critical issues detected."));
        assertTrue(reportText.contains("No error issues detected."));
        assertTrue(reportText.contains("No warning issues detected."));
        assertTrue(reportText.contains("No critical issues, errors, or warnings were detected in the provided logs."));
    }

    @Test
    void generateReportFromFolderRejectsInvalidFolderPath() {
        ReportGenerationService service = new ReportGenerationService(
                new LogAnalysisService(new LogParserService(), new IssueAnalyzerService())
        );

        IllegalArgumentException blankPathException = assertThrows(
                IllegalArgumentException.class,
                () -> service.generateReportFromFolder(" ")
        );
        assertEquals("folderPath must be provided.", blankPathException.getMessage());

        IllegalArgumentException missingFolderException = assertThrows(
                IllegalArgumentException.class,
                () -> service.generateReportFromFolder(tempDir.resolve("missing-folder").toString())
        );
        assertEquals("folderPath must point to an existing directory.", missingFolderException.getMessage());
    }

    @Test
    void generateReportFromFolderRejectsFolderWithoutLogFiles() throws Exception {
        ReportGenerationService service = new ReportGenerationService(
                new LogAnalysisService(new LogParserService(), new IssueAnalyzerService())
        );
        Path folder = Files.createDirectory(tempDir.resolve("input-folder"));
        Files.writeString(folder.resolve("notes.txt"), "not a log");

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> service.generateReportFromFolder(folder.toString())
        );

        assertEquals("No .log files were found in the provided folderPath.", exception.getMessage());
    }

    @Test
    void generateReportFromFolderUsesOnlyLogFilesAndTracksIgnoredFiles() throws Exception {
        LogParserService logParserService = mock(LogParserService.class);
        IssueAnalyzerService issueAnalyzerService = mock(IssueAnalyzerService.class);
        ReportGenerationService service = new ReportGenerationService(
                new LogAnalysisService(logParserService, issueAnalyzerService)
        );

        Path folder = Files.createDirectory(tempDir.resolve("logs-folder"));
        Path firstLog = Files.createFile(folder.resolve("a.log"));
        Path secondLog = Files.createFile(folder.resolve("b.LOG"));
        Path ignoredTextFile = folder.resolve("skip.txt");
        Path ignoredJsonFile = folder.resolve("skip.json");
        Files.writeString(ignoredTextFile, "ignore me");
        Files.writeString(ignoredJsonFile, "{\"skip\": true}");
        Files.createDirectory(folder.resolve("nested"));

        List<ParsedLogEntry> parsedEntries = List.of(parsedEntry("INFO", "Startup finished", "logger-info"));
        when(logParserService.parseFiles(anyList(), any(ZoneId.class))).thenReturn(parsedEntries);
        when(issueAnalyzerService.analyze(parsedEntries, REPORT_ZONE))
                .thenReturn(new IssueAnalyzerService.AnalysisResult(List.of(), List.of(), List.of()));

        GenerateReportResponse response = service.generateReportFromFolder(folder.toString(), "UTC");

        ArgumentCaptor<List<Path>> validPathCaptor = ArgumentCaptor.forClass(List.class);
        verify(logParserService, times(1)).parseFiles(validPathCaptor.capture(), eq(ZoneId.of("UTC")));
        List<Path> passedPaths = validPathCaptor.getValue();
        assertEquals(2, passedPaths.size());
        assertEquals(firstLog.toAbsolutePath().normalize(), passedPaths.get(0));
        assertEquals(secondLog.toAbsolutePath().normalize(), passedPaths.get(1));

        assertEquals(
                List.of(
                        ignoredJsonFile.toAbsolutePath().normalize().toString(),
                        ignoredTextFile.toAbsolutePath().normalize().toString()
                ),
                response.ignoredFiles()
        );
        assertEquals("Report generated successfully", response.message());
        assertTrue(Files.exists(Paths.get(response.reportPath())));
    }

    @Test
    void generateReportUsesUtcWhenTimezoneMissing() throws Exception {
        LogParserService logParserService = mock(LogParserService.class);
        IssueAnalyzerService issueAnalyzerService = mock(IssueAnalyzerService.class);
        ReportGenerationService service = new ReportGenerationService(
                new LogAnalysisService(logParserService, issueAnalyzerService)
        );

        Path validLogFile = Files.createFile(tempDir.resolve("utc-default.log"));
        List<ParsedLogEntry> parsedEntries = List.of(parsedEntry("INFO", "Startup finished", "logger-info"));
        when(logParserService.parseFiles(anyList(), any(ZoneId.class))).thenReturn(parsedEntries);
        when(issueAnalyzerService.analyze(parsedEntries, REPORT_ZONE))
                .thenReturn(new IssueAnalyzerService.AnalysisResult(List.of(), List.of(), List.of()));

        GenerateReportResponse response = service.generateReport(List.of(validLogFile.toString()), null);

        String reportText = Files.readString(Paths.get(response.reportPath()), StandardCharsets.UTF_8);
        assertTrue(reportText.contains("Timezone: America/Los_Angeles"));
        verify(issueAnalyzerService, times(1)).analyze(parsedEntries, REPORT_ZONE);
    }

    @Test
    void generateReportRejectsInvalidTimezone() {
        ReportGenerationService service = new ReportGenerationService(
                new LogAnalysisService(new LogParserService(), new IssueAnalyzerService())
        );

        InvalidTimezoneException exception = assertThrows(
                InvalidTimezoneException.class,
                () -> service.generateReport(List.of("/tmp/app.log"), "Mars/Phobos")
        );
        assertTrue(exception.getMessage().contains("timezone must be a valid java.time.ZoneId"));
    }

    private static Stream<Arguments> invalidFilePathInputs() {
        return Stream.of(
                Arguments.of(null, "filePaths must contain at least one log file path."),
                Arguments.of(List.of(), "filePaths must contain at least one log file path."),
                Arguments.of(Arrays.asList(" ", null, "\t"), "None of the provided filePaths points to an existing file.")
        );
    }

    private static ParsedLogEntry parsedEntry(String level, String message, String logger) {
        return new ParsedLogEntry(
                OffsetDateTime.now(ZoneId.of("UTC")),
                "main",
                "cid-1",
                level,
                logger,
                message,
                "/tmp/app.log",
                1,
                List.of()
        );
    }

    private static ZonedDateTime zonedNow() {
        return ZonedDateTime.now(ZoneId.of("UTC"));
    }
}
