package com.applicationlogparser.service;

import com.applicationlogparser.model.IssueRecord;
import com.applicationlogparser.model.IssueType;
import com.applicationlogparser.model.ParsedLogEntry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LogAnalysisServiceTest {

    @Mock
    private LogParserService logParserService;

    @Mock
    private IssueAnalyzerService issueAnalyzerService;

    private LogAnalysisService logAnalysisService;

    @BeforeEach
    void setUp() {
        logAnalysisService = new LogAnalysisService(logParserService, issueAnalyzerService);
    }

    @ParameterizedTest
    @MethodSource("analysisScenarios")
    void analyzeShouldReturnExpectedSummaryCounts(int totalEntries, int criticalGroups, int errorGroups, int warningGroups) throws IOException {
        List<Path> inputFiles = List.of(Path.of("/tmp/app-1.log"), Path.of("/tmp/app-2.log"));
        List<ParsedLogEntry> parsedEntries = IntStream.range(0, totalEntries)
                .mapToObj(this::entryAt)
                .toList();
        List<IssueRecord> criticalIssues = IntStream.range(0, criticalGroups)
                .mapToObj(index -> issueAt(IssueType.CRITICAL, index))
                .toList();
        List<IssueRecord> errorIssues = IntStream.range(0, errorGroups)
                .mapToObj(index -> issueAt(IssueType.ERROR, index))
                .toList();
        List<IssueRecord> warningIssues = IntStream.range(0, warningGroups)
                .mapToObj(index -> issueAt(IssueType.WARNING, index))
                .toList();
        IssueAnalyzerService.AnalysisResult analysisResult = new IssueAnalyzerService.AnalysisResult(
                criticalIssues,
                errorIssues,
                warningIssues
        );

        when(logParserService.parseFiles(inputFiles)).thenReturn(parsedEntries);
        when(issueAnalyzerService.analyze(parsedEntries)).thenReturn(analysisResult);

        LogAnalysisService.AnalysisBundle bundle = logAnalysisService.analyze(inputFiles);

        assertEquals(totalEntries, bundle.totalEntries());
        assertEquals(criticalGroups, bundle.criticalIssueGroups());
        assertEquals(errorGroups, bundle.errorIssueGroups());
        assertEquals(warningGroups, bundle.warningIssueGroups());
        assertEquals(criticalIssues, bundle.criticalIssues());
        assertEquals(errorIssues, bundle.errorIssues());
        assertEquals(warningIssues, bundle.warningIssues());
        verify(logParserService).parseFiles(inputFiles);
        verify(issueAnalyzerService).analyze(parsedEntries);
    }

    @Test
    void analyzeShouldPropagateIOExceptionFromParser() throws IOException {
        List<Path> inputFiles = List.of(Path.of("/tmp/failing.log"));
        when(logParserService.parseFiles(inputFiles)).thenThrow(new IOException("Cannot read file"));

        IOException exception = assertThrows(IOException.class, () -> logAnalysisService.analyze(inputFiles));

        assertEquals("Cannot read file", exception.getMessage());
    }

    private static Stream<Arguments> analysisScenarios() {
        return Stream.of(
                Arguments.of(0, 0, 0, 0),
                Arguments.of(2, 1, 0, 1),
                Arguments.of(5, 2, 3, 4)
        );
    }

    private ParsedLogEntry entryAt(int index) {
        return new ParsedLogEntry(
                OffsetDateTime.parse("2026-04-10T10:15:30Z").plusMinutes(index),
                "main",
                "cid-" + index,
                "ERROR",
                "app.Logger",
                "Failure " + index,
                "/tmp/app.log",
                index + 1,
                List.of("at com.example.Service.run(Service.java:10)")
        );
    }

    private IssueRecord issueAt(IssueType issueType, int index) {
        ZonedDateTime now = ZonedDateTime.of(2026, 4, 10, 10, 20, 0, 0, ZoneOffset.UTC).plusMinutes(index);
        String title = issueType + " issue " + index;
        String cause = "cause-" + index;
        return new IssueRecord(
                issueType,
                title,
                cause,
                List.of(cause),
                List.of(now),
                List.of("at com.example.Service.method(Service.java:10)"),
                "app.Logger"
        );
    }
}
