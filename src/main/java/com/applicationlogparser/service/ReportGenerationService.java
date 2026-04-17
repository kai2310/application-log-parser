package com.applicationlogparser.service;

import com.applicationlogparser.dto.GenerateReportResponse;
import com.applicationlogparser.model.IssueRecord;
import com.applicationlogparser.model.IssueType;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

public final class ReportGenerationService {

    private static final DateTimeFormatter FILE_NAME_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");
    private static final DateTimeFormatter DISPLAY_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss XXX");

    private final LogAnalysisService logAnalysisService;

    public ReportGenerationService(LogParserService logParserService, IssueAnalyzerService issueAnalyzerService) {
        this.logAnalysisService = new LogAnalysisService(logParserService, issueAnalyzerService);
    }

    public GenerateReportResponse generateReport(List<String> filePaths) throws IOException {
        if (filePaths == null || filePaths.isEmpty()) {
            throw new IllegalArgumentException("filePaths must contain at least one log file path.");
        }

        List<Path> validPaths = new ArrayList<>();
        List<String> ignoredFiles = new ArrayList<>();
        for (String filePath : filePaths) {
            if (filePath == null || filePath.isBlank()) {
                ignoredFiles.add(String.valueOf(filePath));
                continue;
            }

            Path resolvedPath = Paths.get(filePath).toAbsolutePath().normalize();
            if (!Files.isRegularFile(resolvedPath)) {
                ignoredFiles.add(filePath);
                continue;
            }
            validPaths.add(resolvedPath);
        }

        if (validPaths.isEmpty()) {
            throw new IllegalArgumentException("None of the provided filePaths points to an existing file.");
        }

        LogAnalysisService.AnalysisBundle analysisBundle = logAnalysisService.analyze(validPaths);

        List<IssueRecord> allIssues = new ArrayList<>(analysisBundle.criticalIssues());
        allIssues.addAll(analysisBundle.errorIssues());

        Path reportPath = writeReport(
                allIssues,
                validPaths.stream().map(Path::toString).toList()
        );

        return new GenerateReportResponse(
                reportPath.toString(),
                analysisBundle.totalEntries(),
                analysisBundle.criticalIssueGroups(),
                analysisBundle.errorIssueGroups(),
                ignoredFiles,
                "Report generated successfully"
        );
    }

    private Path writeReport(List<IssueRecord> issues, List<String> inputFiles) throws IOException {
        ZoneId systemZone = ZoneId.systemDefault();
        ZonedDateTime now = ZonedDateTime.now(systemZone);
        String fileName = "application-logs-report-" + FILE_NAME_FORMATTER.format(now) + ".txt";

        Path reportsDir = Paths.get("reports");
        Files.createDirectories(reportsDir);

        Path reportPath = reportsDir.resolve(fileName);
        String reportContent = buildReport(issues, inputFiles, now, systemZone);
        Files.writeString(reportPath, reportContent, StandardCharsets.UTF_8);
        return reportPath.toAbsolutePath();
    }

    private String buildReport(List<IssueRecord> issues, List<String> inputFiles, ZonedDateTime generatedAt, ZoneId zoneId) {
        StringBuilder sb = new StringBuilder();
        sb.append("APPLICATION LOG ANALYSIS REPORT").append(System.lineSeparator());
        sb.append("Generated At: ").append(DISPLAY_TIME_FORMATTER.format(generatedAt)).append(System.lineSeparator());
        sb.append("Timezone: ").append(zoneId).append(System.lineSeparator());
        sb.append("Input Files: ").append(System.lineSeparator());
        for (String inputFile : inputFiles) {
            sb.append("  - ").append(inputFile).append(System.lineSeparator());
        }
        sb.append(System.lineSeparator());

        List<IssueRecord> criticalIssues = issues.stream()
                .filter(issue -> issue.issueType() == IssueType.CRITICAL)
                .toList();
        List<IssueRecord> errorIssues = issues.stream()
                .filter(issue -> issue.issueType() == IssueType.ERROR)
                .toList();

        appendSection(sb, "1) CRITICAL ISSUES", criticalIssues, "No critical issues detected.");
        sb.append(System.lineSeparator());
        appendSection(sb, "2) ERROR ISSUES", errorIssues, "No error issues detected.");

        if (criticalIssues.isEmpty() && errorIssues.isEmpty()) {
            sb.append(System.lineSeparator());
            sb.append("SUMMARY").append(System.lineSeparator());
            sb.append("No critical issues or errors were detected in the provided logs.").append(System.lineSeparator());
        }

        return sb.toString();
    }

    private void appendSection(StringBuilder sb, String title, List<IssueRecord> issues, String emptyMessage) {
        sb.append(title).append(System.lineSeparator());
        if (issues.isEmpty()) {
            sb.append(emptyMessage).append(System.lineSeparator());
            return;
        }

        int index = 1;
        for (IssueRecord issue : issues) {
            sb.append(index++).append(". ").append(issue.title()).append(System.lineSeparator());
            sb.append("   Logger: ").append(issue.logger()).append(System.lineSeparator());
            sb.append("   Cause: ").append(issue.cause() == null || issue.cause().isBlank() ? "Unknown" : issue.cause()).append(System.lineSeparator());
            if (!issue.causes().isEmpty()) {
                sb.append("   Causes Seen: ").append(String.join(" | ", issue.causes())).append(System.lineSeparator());
            }
            sb.append("   Occurrences: ").append(issue.occurrences().size()).append(System.lineSeparator());
            sb.append("   Times: ").append(System.lineSeparator());
            for (ZonedDateTime timestamp : issue.occurrences()) {
                sb.append("     - ").append(DISPLAY_TIME_FORMATTER.format(timestamp)).append(System.lineSeparator());
            }
            if (!issue.stackTraceLines().isEmpty()) {
                sb.append("   Stack Trace:").append(System.lineSeparator());
                for (String line : issue.stackTraceLines()) {
                    sb.append("     ").append(line).append(System.lineSeparator());
                }
            }
            sb.append(System.lineSeparator());
        }
    }
}
