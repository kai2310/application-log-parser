package com.kai.applicationlogparser.dto;

import java.util.List;

public record GenerateReportResponse(
        String reportPath,
        int totalEntriesProcessed,
        int criticalIssueGroups,
        int errorIssueGroups,
        int warningIssueGroups,
        List<String> ignoredFiles,
        String message
) {
}
