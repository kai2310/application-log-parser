package com.kai.applicationlogparser.service;

import com.kai.applicationlogparser.model.ParsedLogEntry;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

@Service
public final class LogAnalysisService {
    private final LogParserService logParserService;
    private final IssueAnalyzerService issueAnalyzerService;

    public LogAnalysisService(LogParserService logParserService, IssueAnalyzerService issueAnalyzerService) {
        this.logParserService = logParserService;
        this.issueAnalyzerService = issueAnalyzerService;
    }

    public AnalysisBundle analyze(List<Path> inputFiles) throws IOException {
        List<ParsedLogEntry> entries = logParserService.parseFiles(inputFiles);
        IssueAnalyzerService.AnalysisResult analysisResult = issueAnalyzerService.analyze(entries);

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
            List<com.kai.applicationlogparser.model.IssueRecord> criticalIssues,
            List<com.kai.applicationlogparser.model.IssueRecord> errorIssues,
            List<com.kai.applicationlogparser.model.IssueRecord> warningIssues
    ) {
    }
}
