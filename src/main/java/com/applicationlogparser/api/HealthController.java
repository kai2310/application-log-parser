package com.applicationlogparser.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;

public final class HealthController implements HttpHandler {

    private final ObjectMapper objectMapper;

    public HealthController(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
            byte[] body = objectMapper.writeValueAsBytes(Map.of("status", "Method Not Allowed"));
            exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
            exchange.sendResponseHeaders(405, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
            return;
        }

        byte[] body = objectMapper.writeValueAsString(Map.of("status", "UP")).getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.sendResponseHeaders(200, body.length);
        exchange.getResponseBody().write(body);
        exchange.close();
    }
}
