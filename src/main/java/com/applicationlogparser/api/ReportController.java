package com.applicationlogparser.api;

import com.applicationlogparser.dto.GenerateReportRequest;
import com.applicationlogparser.dto.GenerateReportResponse;
import com.applicationlogparser.service.ReportGenerationService;
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
public class ReportController {
    private final ReportGenerationService reportGenerationService;

    public ReportController(ReportGenerationService reportGenerationService) {
        this.reportGenerationService = reportGenerationService;
    }

    @PostMapping("/reports")
    public GenerateReportResponse generateReport(@RequestBody GenerateReportRequest request) throws IOException {
        return reportGenerationService.generateReport(request.getFilePaths());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<GenerateReportResponse> handleIllegalArgument(IllegalArgumentException ex) {
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(new GenerateReportResponse(null, 0, 0, 0, List.of(), ex.getMessage()));
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
                        List.of(),
                        "Internal error while generating report: " + ex.getMessage()
                ));
    }
}
