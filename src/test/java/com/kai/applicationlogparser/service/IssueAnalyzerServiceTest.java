package com.kai.applicationlogparser.service;

import com.kai.applicationlogparser.model.IssueRecord;
import com.kai.applicationlogparser.model.IssueType;
import com.kai.applicationlogparser.model.ParsedLogEntry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IssueAnalyzerServiceTest {

    private static final OffsetDateTime BASE_TIME = OffsetDateTime.parse("2026-04-17T10:15:30Z");

    private final IssueAnalyzerService subject = new IssueAnalyzerService();

    @ParameterizedTest
    @MethodSource("criticalMessages")
    void analyze_detectsCriticalIssues(String message) {
        ParsedLogEntry entry = entry("WARN", message, List.of());

        IssueAnalyzerService.AnalysisResult result = subject.analyze(List.of(entry));

        assertEquals(1, result.criticalIssues().size());
        assertEquals(0, result.errorIssues().size());
        assertEquals(0, result.warningIssues().size());
        assertEquals(IssueType.CRITICAL, result.criticalIssues().get(0).issueType());
    }

    @Test
    void analyze_groupsErrorEntriesWithSameSignature() {
        ParsedLogEntry first = entry("ERROR", "java.lang.IllegalStateException: order 123 failed", List.of("at a.b.C.run(C.java:10)"));
        ParsedLogEntry second = entry("ERROR", "java.lang.IllegalStateException: order 987 failed", List.of("at a.b.C.run(C.java:10)", "Caused by: java.io.IOException: disk full"));

        IssueAnalyzerService.AnalysisResult result = subject.analyze(List.of(first, second));

        assertEquals(1, result.errorIssues().size());
        assertEquals(0, result.warningIssues().size());
        IssueRecord issue = result.errorIssues().get(0);
        assertEquals(IssueType.ERROR, issue.issueType());
        assertEquals(2, issue.occurrences().size());
        assertEquals("app.logger", issue.logger());
        assertTrue(issue.title().contains("IllegalStateException"));
        assertTrue(issue.stackTraceLines().contains("at a.b.C.run(C.java:10)"));
        assertTrue(issue.stackTraceLines().stream().anyMatch(line -> line.contains("IOException")));
        assertEquals("IllegalStateException", issue.cause());
    }

    @Test
    void analyze_ignoresNonErrorAndNonCriticalEntries() {
        ParsedLogEntry info = entry("INFO", "operation completed", List.of());
        ParsedLogEntry debug = entry("DEBUG", "cache warm-up in progress", List.of());

        IssueAnalyzerService.AnalysisResult result = subject.analyze(List.of(info, debug));

        assertTrue(result.criticalIssues().isEmpty());
        assertTrue(result.errorIssues().isEmpty());
        assertTrue(result.warningIssues().isEmpty());
    }

    @Test
    void analyze_groupsWarningEntriesWithSameSignature() {
        ParsedLogEntry first = entry("WARN", "Cache nearing capacity: 80%", List.of("at a.b.CacheMonitor.check(CacheMonitor.java:10)"));
        ParsedLogEntry second = entry("WARNING", "Cache nearing capacity: 92%", List.of("at a.b.CacheMonitor.check(CacheMonitor.java:10)"));

        IssueAnalyzerService.AnalysisResult result = subject.analyze(List.of(first, second));

        assertEquals(1, result.warningIssues().size());
        IssueRecord issue = result.warningIssues().get(0);
        assertEquals(IssueType.WARNING, issue.issueType());
        assertEquals(2, issue.occurrences().size());
        assertEquals("app.logger", issue.logger());
        assertTrue(issue.title().contains("Cache nearing capacity"));
        assertTrue(issue.stackTraceLines().contains("at a.b.CacheMonitor.check(CacheMonitor.java:10)"));
    }

    private static Stream<String> criticalMessages() {
        return Stream.of(
                "OutOfMemoryError while processing request",
                "GC overhead limit exceeded in worker",
                "Unable to create new native thread"
        );
    }

    private static ParsedLogEntry entry(String level, String message, List<String> continuation) {
        return new ParsedLogEntry(
                BASE_TIME,
                "main",
                "cid-1",
                level,
                "app.logger",
                message,
                "/tmp/app.log",
                1,
                continuation
        );
    }
}
