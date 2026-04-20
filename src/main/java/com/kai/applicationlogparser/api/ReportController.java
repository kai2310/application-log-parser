package com.kai.applicationlogparser.api;

import com.kai.applicationlogparser.dto.GenerateReportRequest;
import com.kai.applicationlogparser.dto.GenerateReportResponse;
import com.kai.applicationlogparser.dto.GenerateFolderReportRequest;
import com.kai.applicationlogparser.service.ReportGenerationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.io.IOException;
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
            @ApiResponse(responseCode = "400", description = "Invalid request input"),
            @ApiResponse(responseCode = "500", description = "Unexpected server error")
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
            @ApiResponse(responseCode = "400", description = "Invalid request input"),
            @ApiResponse(responseCode = "500", description = "Unexpected server error")
    })
    public GenerateReportResponse generateReportFromFolder(@RequestBody GenerateFolderReportRequest request) throws IOException {
        return reportGenerationService.generateReportFromFolder(request.getFolderPath());
    }
}
