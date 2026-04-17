package com.applicationlogparser.api;

import com.applicationlogparser.dto.GenerateReportRequest;
import com.applicationlogparser.dto.GenerateReportResponse;
import com.applicationlogparser.service.ReportGenerationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

public final class ReportController implements HttpHandler {
    private final ObjectMapper objectMapper;
    private final ReportGenerationService reportGenerationService;

    public ReportController(ObjectMapper objectMapper, ReportGenerationService reportGenerationService) {
        this.objectMapper = objectMapper;
        this.reportGenerationService = reportGenerationService;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        try {
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                writeJson(
                        exchange,
                        405,
                        new GenerateReportResponse(null, 0, 0, 0, List.of(), "Method not allowed. Use POST /api/reports")
                );
                return;
            }

            GenerateReportRequest request = objectMapper.readValue(exchange.getRequestBody(), GenerateReportRequest.class);
            GenerateReportResponse response = reportGenerationService.generateReport(request.getFilePaths());
            writeJson(exchange, 200, response);
        } catch (IllegalArgumentException ex) {
            writeJson(exchange, 400, new GenerateReportResponse(null, 0, 0, 0, List.of(), ex.getMessage()));
        } catch (Exception ex) {
            writeJson(
                    exchange,
                    500,
                    new GenerateReportResponse(null, 0, 0, 0, List.of(), "Internal error while generating report: " + ex.getMessage())
            );
        }
    }

    private void writeJson(HttpExchange exchange, int statusCode, GenerateReportResponse response) throws IOException {
        byte[] body = objectMapper.writeValueAsString(response).getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.sendResponseHeaders(statusCode, body.length);
        exchange.getResponseBody().write(body);
        exchange.close();
    }
}
