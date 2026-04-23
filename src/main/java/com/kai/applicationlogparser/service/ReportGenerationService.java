package com.kai.applicationlogparser.service;

import com.kai.applicationlogparser.api.InvalidTimezoneException;
import com.kai.applicationlogparser.dto.GenerateReportResponse;
import com.kai.applicationlogparser.model.IssueRecord;
import com.kai.applicationlogparser.model.IssueType;

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
import java.util.Locale;
import java.util.stream.Stream;
import org.springframework.stereotype.Service;

@Service
public final class ReportGenerationService {

    private static final DateTimeFormatter FILE_NAME_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");
    private static final DateTimeFormatter DISPLAY_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss XXX");
    private static final ZoneId REPORT_TIMEZONE = ZoneId.of("America/Los_Angeles");

    private final LogAnalysisService logAnalysisService;

    public ReportGenerationService(LogAnalysisService logAnalysisService) {
        this.logAnalysisService = logAnalysisService;
    }

    public GenerateReportResponse generateReport(List<String> filePaths) throws IOException {
        return generateReport(filePaths, null);
    }

    public GenerateReportResponse generateReport(List<String> filePaths, String timezone) throws IOException {
        if (filePaths == null || filePaths.isEmpty()) {
            throw new IllegalArgumentException("filePaths must contain at least one log file path.");
        }

        ZoneId parsingFallbackZone = resolveParsingFallbackZone(timezone);
        return generateReport(filePaths, List.of(), parsingFallbackZone);
    }

    private GenerateReportResponse generateReport(List<String> filePaths, List<String> preIgnoredFiles, ZoneId parsingFallbackZone) throws IOException {
        List<Path> validPaths = new ArrayList<>();
        List<String> ignoredFiles = new ArrayList<>(preIgnoredFiles);
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

        return generateReportFromValidPaths(validPaths, ignoredFiles, parsingFallbackZone);
    }

    private GenerateReportResponse generateReportFromValidPaths(List<Path> validPaths, List<String> ignoredFiles, ZoneId parsingFallbackZone) throws IOException {
        LogAnalysisService.AnalysisBundle analysisBundle = logAnalysisService.analyze(
                validPaths,
                parsingFallbackZone,
                REPORT_TIMEZONE
        );

        List<IssueRecord> allIssues = new ArrayList<>(analysisBundle.criticalIssues());
        allIssues.addAll(analysisBundle.errorIssues());
        allIssues.addAll(analysisBundle.warningIssues());

        Path reportPath = writeReport(
                allIssues,
                validPaths.stream().map(Path::toString).toList(),
                REPORT_TIMEZONE
        );

        return new GenerateReportResponse(
                reportPath.toString(),
                analysisBundle.totalEntries(),
                analysisBundle.criticalIssueGroups(),
                analysisBundle.errorIssueGroups(),
                analysisBundle.warningIssueGroups(),
                ignoredFiles,
                "Report generated successfully"
        );
    }

    public GenerateReportResponse generateReportFromFolder(String folderPath) throws IOException {
        return generateReportFromFolder(folderPath, null);
    }

    public GenerateReportResponse generateReportFromFolder(String folderPath, String timezone) throws IOException {
        if (folderPath == null || folderPath.isBlank()) {
            throw new IllegalArgumentException("folderPath must be provided.");
        }

        ZoneId parsingFallbackZone = resolveParsingFallbackZone(timezone);
        Path resolvedFolderPath = Paths.get(folderPath).toAbsolutePath().normalize();
        if (!Files.isDirectory(resolvedFolderPath)) {
            throw new IllegalArgumentException("folderPath must point to an existing directory.");
        }

        List<String> discoveredLogFilePaths = new ArrayList<>();
        List<String> ignoredFilePaths = new ArrayList<>();
        try (Stream<Path> paths = Files.list(resolvedFolderPath)) {
            List<Path> sortedPaths = paths
                    .map(path -> path.toAbsolutePath().normalize())
                    .sorted()
                    .toList();

            for (Path path : sortedPaths) {
                if (!Files.isRegularFile(path)) {
                    continue;
                }
                if (!isLogFile(path)) {
                    ignoredFilePaths.add(path.toString());
                    continue;
                }
                discoveredLogFilePaths.add(path.toString());
            }
        }

        if (discoveredLogFilePaths.isEmpty()) {
            throw new IllegalArgumentException("No .log files were found in the provided folderPath.");
        }

        return generateReport(discoveredLogFilePaths, ignoredFilePaths, parsingFallbackZone);
    }

    private boolean isLogFile(Path path) {
        String fileName = path.getFileName().toString();
        return fileName.toLowerCase(Locale.ROOT).endsWith(".log");
    }

    private Path writeReport(List<IssueRecord> issues, List<String> inputFiles, ZoneId targetZone) throws IOException {
        ZonedDateTime now = ZonedDateTime.now(targetZone);
        String fileName = "application-logs-report-" + FILE_NAME_FORMATTER.format(now) + ".txt";

        Path reportsDir = Paths.get("reports");
        Files.createDirectories(reportsDir);

        Path reportPath = reportsDir.resolve(fileName);
        String reportContent = buildReport(issues, inputFiles, now, targetZone);
        Files.writeString(reportPath, reportContent, StandardCharsets.UTF_8);
        return reportPath.toAbsolutePath();
    }

    private ZoneId resolveParsingFallbackZone(String timezone) {
        if (timezone == null || timezone.isBlank()) {
            return ZoneId.of("UTC");
        }
        try {
            return ZoneId.of(timezone.trim());
        } catch (RuntimeException ex) {
            throw new InvalidTimezoneException(timezone, ex);
        }
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
        List<IssueRecord> warningIssues = issues.stream()
                .filter(issue -> issue.issueType() == IssueType.WARNING)
                .toList();

        appendSection(sb, "1) CRITICAL ISSUES", criticalIssues, "No critical issues detected.");
        sb.append(System.lineSeparator());
        appendSection(sb, "2) ERROR ISSUES", errorIssues, "No error issues detected.");
        sb.append(System.lineSeparator());
        appendSection(sb, "3) WARNING ISSUES", warningIssues, "No warning issues detected.");

        if (criticalIssues.isEmpty() && errorIssues.isEmpty() && warningIssues.isEmpty()) {
            sb.append(System.lineSeparator());
            sb.append("SUMMARY").append(System.lineSeparator());
            sb.append("No critical issues, errors, or warnings were detected in the provided logs.").append(System.lineSeparator());
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
