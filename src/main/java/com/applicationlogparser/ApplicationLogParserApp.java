package com.applicationlogparser;

import com.applicationlogparser.api.ReportApiServer;
import com.applicationlogparser.service.IssueAnalyzerService;
import com.applicationlogparser.service.LogParserService;
import com.applicationlogparser.service.ReportGenerationService;

public final class ApplicationLogParserApp {

    private ApplicationLogParserApp() {
    }

    public static void main(String[] args) throws Exception {
        int port = Integer.parseInt(System.getenv().getOrDefault("PORT", "8080"));

        LogParserService logParserService = new LogParserService();
        IssueAnalyzerService issueAnalyzerService = new IssueAnalyzerService();
        ReportGenerationService reportGenerationService =
                new ReportGenerationService(logParserService, issueAnalyzerService);

        ReportApiServer reportApiServer = new ReportApiServer(port, reportGenerationService);
        reportApiServer.start();

        System.out.println("Application Log Parser API started on port " + port);
        System.out.println("Endpoints:");
        System.out.println("  GET  /api/health");
        System.out.println("  POST /api/reports");
    }
}
