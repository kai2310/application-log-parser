package com.kai.applicationlogparser.service;

import com.kai.applicationlogparser.model.IssueRecord;
import com.kai.applicationlogparser.model.ParsedLogEntry;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Path;
import java.time.ZoneId;
import java.util.List;

@Service
public final class LogAnalysisService {
    private static final ZoneId DEFAULT_REPORT_ZONE = ZoneId.of("America/Los_Angeles");

    private final LogParserService logParserService;
    private final IssueAnalyzerService issueAnalyzerService;

    public LogAnalysisService(LogParserService logParserService, IssueAnalyzerService issueAnalyzerService) {
        this.logParserService = logParserService;
        this.issueAnalyzerService = issueAnalyzerService;
    }

    public AnalysisBundle analyze(List<Path> inputFiles) throws IOException {
        return analyze(inputFiles, ZoneId.of("UTC"), DEFAULT_REPORT_ZONE);
    }

    public AnalysisBundle analyze(List<Path> inputFiles, ZoneId parsingFallbackZone) throws IOException {
        return analyze(inputFiles, parsingFallbackZone, DEFAULT_REPORT_ZONE);
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
