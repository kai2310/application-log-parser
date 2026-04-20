package com.kai.applicationlogparser.api;

import com.kai.applicationlogparser.dto.GenerateReportRequest;
import com.kai.applicationlogparser.dto.GenerateReportResponse;
import com.kai.applicationlogparser.dto.GenerateFolderReportRequest;
import com.kai.applicationlogparser.service.ReportGenerationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.io.IOException;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
@Tag(name = "Report API", description = "Endpoints for generating log analysis reports.")
public class ReportController {
    private final ReportGenerationService reportGenerationService;

    public ReportController(ReportGenerationService reportGenerationService) {
        this.reportGenerationService = reportGenerationService;
    }

    @PostMapping("/reports")
    @Operation(
            summary = "Generate report from explicit log file paths",
            description = "Accepts one or more absolute log file paths and generates a consolidated issue report."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Report generated successfully"),
            @ApiResponse(
                    responseCode = "400",
                    description = "Invalid request input",
                    content = @Content(
                            examples = @ExampleObject(value = """
                                    {
                                      "reportPath": null,
                                      "totalEntriesProcessed": 0,
                                      "criticalIssueGroups": 0,
                                      "errorIssueGroups": 0,
                                      "warningIssueGroups": 0,
                                      "ignoredFiles": [],
                                      "message": "filePaths must contain at least one log file path."
                                    }
                                    """)
                    )
            ),
            @ApiResponse(
                    responseCode = "500",
                    description = "Unexpected server error",
                    content = @Content(
                            examples = @ExampleObject(value = """
                                    {
                                      "reportPath": null,
                                      "totalEntriesProcessed": 0,
                                      "criticalIssueGroups": 0,
                                      "errorIssueGroups": 0,
                                      "warningIssueGroups": 0,
                                      "ignoredFiles": [],
                                      "message": "Internal error while generating report: <details>"
                                    }
                                    """)
                    )
            )
    })
    public GenerateReportResponse generateReport(@RequestBody GenerateReportRequest request) throws IOException {
        return reportGenerationService.generateReport(request.getFilePaths());
    }

    @PostMapping("/reports/folder")
    @Operation(
            summary = "Generate report by scanning a folder",
            description = "Scans a folder for .log files (non-recursive) and generates a consolidated issue report."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Report generated successfully"),
            @ApiResponse(
                    responseCode = "400",
                    description = "Invalid request input",
                    content = @Content(
                            examples = @ExampleObject(value = """
                                    {
                                      "reportPath": null,
                                      "totalEntriesProcessed": 0,
                                      "criticalIssueGroups": 0,
                                      "errorIssueGroups": 0,
                                      "warningIssueGroups": 0,
                                      "ignoredFiles": [],
                                      "message": "folderPath must point to an existing directory."
                                    }
                                    """)
                    )
            ),
            @ApiResponse(
                    responseCode = "500",
                    description = "Unexpected server error",
                    content = @Content(
                            examples = @ExampleObject(value = """
                                    {
                                      "reportPath": null,
                                      "totalEntriesProcessed": 0,
                                      "criticalIssueGroups": 0,
                                      "errorIssueGroups": 0,
                                      "warningIssueGroups": 0,
                                      "ignoredFiles": [],
                                      "message": "Internal error while generating report: <details>"
                                    }
                                    """)
                    )
            )
    })
    public GenerateReportResponse generateReportFromFolder(@RequestBody GenerateFolderReportRequest request) throws IOException {
        return reportGenerationService.generateReportFromFolder(request.getFolderPath());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<GenerateReportResponse> handleIllegalArgument(IllegalArgumentException ex) {
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(new GenerateReportResponse(null, 0, 0, 0, 0, List.of(), ex.getMessage()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<GenerateReportResponse> handleException(Exception ex) {
        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new GenerateReportResponse(
                        null,
                        0,
                        0,
                        0,
                        0,
                        List.of(),
                        "Internal error while generating report: " + ex.getMessage()
                ));
    }
}
