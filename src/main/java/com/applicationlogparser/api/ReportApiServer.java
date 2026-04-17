package com.applicationlogparser.api;

import com.applicationlogparser.service.ReportGenerationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.net.InetSocketAddress;

public final class ReportApiServer {

    private final HttpServer httpServer;

    public ReportApiServer(int port, ReportGenerationService reportGenerationService) throws IOException {
        this.httpServer = HttpServer.create(new InetSocketAddress(port), 0);
        ObjectMapper objectMapper = new ObjectMapper();
        ReportController reportController = new ReportController(objectMapper, reportGenerationService);
        HealthController healthController = new HealthController(objectMapper);

        httpServer.createContext("/api/reports", reportController);
        httpServer.createContext("/api/health", healthController);
        httpServer.setExecutor(null);
    }

    public void start() {
        httpServer.start();
    }
}
