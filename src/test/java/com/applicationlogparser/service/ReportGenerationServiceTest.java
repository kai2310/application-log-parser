package com.applicationlogparser.service;

import com.applicationlogparser.dto.GenerateReportResponse;
import com.applicationlogparser.model.IssueRecord;
import com.applicationlogparser.model.IssueType;
import com.applicationlogparser.model.ParsedLogEntry;
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
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ReportGenerationServiceTest {

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
        when(logParserService.parseFiles(anyList())).thenReturn(parsedEntries);

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
        when(issueAnalyzerService.analyze(parsedEntries))
                .thenReturn(new IssueAnalyzerService.AnalysisResult(List.of(criticalIssue), List.of(errorIssue)));

        GenerateReportResponse response = service.generateReport(List.of(validLogFile.toString(), blankPath, missingPath));

        assertNotNull(response.reportPath());
        assertTrue(response.reportPath().contains("reports/application-logs-report-"));
        assertEquals(parsedEntries.size(), response.totalEntriesProcessed());
        assertEquals(1, response.criticalIssueGroups());
        assertEquals(1, response.errorIssueGroups());
        assertEquals(List.of(blankPath, missingPath), response.ignoredFiles());
        assertEquals("Report generated successfully", response.message());

        ArgumentCaptor<List<Path>> validPathCaptor = ArgumentCaptor.forClass(List.class);
        verify(logParserService, times(1)).parseFiles(validPathCaptor.capture());
        List<Path> passedPaths = validPathCaptor.getValue();
        assertEquals(1, passedPaths.size());
        assertEquals(validLogFile.toAbsolutePath().normalize(), passedPaths.getFirst());

        Path reportPath = Paths.get(response.reportPath());
        assertTrue(Files.exists(reportPath));
        String reportText = Files.readString(reportPath, StandardCharsets.UTF_8);
        assertTrue(reportText.contains("1) CRITICAL ISSUES"));
        assertTrue(reportText.contains("2) ERROR ISSUES"));
        assertTrue(reportText.contains("memory leak detected"));
        assertTrue(reportText.contains("NullPointerException happened"));
        assertFalse(reportText.contains("No critical issues or errors were detected"));
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
        when(logParserService.parseFiles(anyList())).thenReturn(parsedEntries);
        when(issueAnalyzerService.analyze(parsedEntries))
                .thenReturn(new IssueAnalyzerService.AnalysisResult(List.of(), List.of()));

        GenerateReportResponse response = service.generateReport(List.of(validLogFile.toString()));
        String reportText = Files.readString(Paths.get(response.reportPath()), StandardCharsets.UTF_8);

        assertTrue(reportText.contains("No critical issues detected."));
        assertTrue(reportText.contains("No error issues detected."));
        assertTrue(reportText.contains("No critical issues or errors were detected in the provided logs."));
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
