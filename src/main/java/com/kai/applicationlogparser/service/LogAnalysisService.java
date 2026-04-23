package com.kai.applicationlogparser.service;

import com.kai.applicationlogparser.model.IssueRecord;
import com.kai.applicationlogparser.model.ParsedLogEntry;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Path;
import java.time.ZoneId;
import java.util.List;

@Service
public final class LogAnalysisService {
    private final LogParserService logParserService;
    private final IssueAnalyzerService issueAnalyzerService;
    private final ZoneId defaultReportZone;

    public LogAnalysisService(
            LogParserService logParserService,
            IssueAnalyzerService issueAnalyzerService,
            @Value("${app.report.timezone:America/Los_Angeles}") String reportTimezone) {
        this.logParserService = logParserService;
        this.issueAnalyzerService = issueAnalyzerService;
        this.defaultReportZone = ZoneId.of(reportTimezone);
    }

    public AnalysisBundle analyze(List<Path> inputFiles) throws IOException {
        return analyze(inputFiles, ZoneId.of("UTC"), defaultReportZone);
    }

    public AnalysisBundle analyze(List<Path> inputFiles, ZoneId parsingFallbackZone) throws IOException {
        return analyze(inputFiles, parsingFallbackZone, defaultReportZone);
    }

    public AnalysisBundle analyze(List<Path> inputFiles, ZoneId parsingFallbackZone, ZoneId reportZone) throws IOException {
        List<ParsedLogEntry> entries = logParserService.parseFiles(inputFiles, parsingFallbackZone);
        IssueAnalyzerService.AnalysisResult analysisResult = issueAnalyzerService.analyze(entries, reportZone);

        return new AnalysisBundle(
                entries.size(),
                analysisResult.criticalIssues().size(),
                analysisResult.errorIssues().size(),
                analysisResult.warningIssues().size(),
                analysisResult.criticalIssues(),
                analysisResult.errorIssues(),
                analysisResult.warningIssues()
        );
    }

    public record AnalysisBundle(
            int totalEntries,
            int criticalIssueGroups,
            int errorIssueGroups,
            int warningIssueGroups,
            List<IssueRecord> criticalIssues,
            List<IssueRecord> errorIssues,
            List<IssueRecord> warningIssues
    ) {
    }
}
