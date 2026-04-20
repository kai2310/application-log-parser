package com.kai.applicationlogparser.dto;

import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(name = "GenerateReportResponse", description = "Summary payload returned after report generation.")
public record GenerateReportResponse(
        @Schema(
                description = "Absolute path to the generated report file.",
                example = "/workspace/reports/application-logs-report-20260416-142455.txt"
        )
        String reportPath,
        @Schema(description = "Number of parsed log entries.", example = "3589")
        int totalEntriesProcessed,
        @Schema(description = "Count of grouped critical issues in the report.", example = "2")
        int criticalIssueGroups,
        @Schema(description = "Count of grouped error issues in the report.", example = "4")
        int errorIssueGroups,
        @Schema(description = "Count of grouped warning issues in the report.", example = "6")
        int warningIssueGroups,
        @ArraySchema(
                arraySchema = @Schema(description = "Paths skipped because they were invalid or unsupported."),
                schema = @Schema(example = "/workspace/sample-logs/notes.txt")
        )
        List<String> ignoredFiles,
        @Schema(description = "Result message for the request.", example = "Report generated successfully")
        String message
) {
}
