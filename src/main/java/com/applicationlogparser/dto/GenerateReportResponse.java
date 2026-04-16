package com.applicationlogparser.dto;

import java.util.List;

public record GenerateReportResponse(
        String reportPath,
        int totalEntriesProcessed,
        int criticalIssueGroups,
        int errorIssueGroups,
        List<String> ignoredFiles,
        String message
) {
}
