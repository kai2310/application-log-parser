# application-log-parser

Java 25 + Docker application that parses multiple log files and generates issue reports on demand through an API.

## What this app does

- Accepts multiple log file paths in one request.
- Parses logs using this format:
  - `%d{ISO8601} [%thread] %X{CID} %-5level %logger{36} - %msg%n`
- Detects and reports:
  1. **Critical issues first** (heap/memory leak related issues such as `OutOfMemoryError`, `Java heap space`, `GC overhead limit exceeded`, `Metaspace`, etc.).
  2. **Error issues second** (`ERROR` level entries), grouped by same issue signature.
- For each grouped issue, report includes:
  - Cause (best effort from message / "Caused by")
  - All occurrence times
  - Stack trace lines (when available)
- If no critical/error issues exist, still generates a report with a "nothing found" summary.
- Saves report locally in:
  - `reports/application-logs-report-<datetime>.txt`
  - Datetime is generated in current system/user timezone.

## Project structure

- `src/main/java/com/applicationlogparser/api` - HTTP server and controllers
- `src/main/java/com/applicationlogparser/service` - parsing, analysis, and report generation
- `src/main/java/com/applicationlogparser/model` - domain models
- `src/main/java/com/applicationlogparser/dto` - API request/response DTOs
- `reports/` - generated at runtime (gitignored)

## Requirements

- Java 25
- Maven 3.9+
- (Optional) Docker

## Run locally

1. Build:

```bash
mvn clean package
```

2. Start API server:

```bash
java -jar target/application-log-parser-1.0.0-jar-with-dependencies.jar
```

Default port is `8080`. Override with:

```bash
PORT=9090 java -jar target/application-log-parser-1.0.0-jar-with-dependencies.jar
```

Health check:

```bash
curl http://localhost:8080/api/health
```

## Generate reports using API

Endpoint:

- `POST /api/reports`

Request body:

```json
{
  "filePaths": [
    "/absolute/path/to/app-1.log",
    "/absolute/path/to/app-2.log"
  ]
}
```

Example:

```bash
curl -X POST "http://localhost:8080/api/reports" \
  -H "Content-Type: application/json" \
  -d '{
    "filePaths": [
      "/workspace/sample-logs/application.log",
      "/workspace/sample-logs/application-2.log"
    ]
  }'
```

Example response:

```json
{
  "reportPath": "/workspace/reports/application-logs-report-20260416-142455.txt",
  "totalEntriesProcessed": 3589,
  "criticalIssueGroups": 2,
  "errorIssueGroups": 4,
  "ignoredFiles": [],
  "message": "Report generated successfully"
}
```

You can call this endpoint repeatedly while the app is running to generate multiple reports at different times.

## Report format

Example report structure:

```text
APPLICATION LOG ANALYSIS REPORT
Generated At: 2026-04-16 14:24:55 -07:00
Timezone: America/Los_Angeles
Input Files:
  - /workspace/sample-logs/application.log
  - /workspace/sample-logs/application-2.log

1) CRITICAL ISSUES
1. java.lang.OutOfMemoryError: Java heap space
   Cause: OutOfMemoryError
   Occurrences: 3
   Times:
     - 2026-04-16 10:01:22 -07:00
     - 2026-04-16 10:12:14 -07:00
     - 2026-04-16 10:13:45 -07:00
   Stack Trace:
     at com.example.MemoryIntensiveService.run(...)
     at ...

2) ERROR ISSUES
1. NullPointerException: Failed to process order
   Cause: NullPointerException
   Occurrences: 7
   Times:
     - 2026-04-16 11:45:01 -07:00
     - ...
   Stack Trace:
     at com.example.OrderService.process(...)
     at ...
```

If nothing is found:

```text
SUMMARY
No critical issues or errors were detected in the provided logs.
```

## Run with Docker

Build image:

```bash
docker build -t application-log-parser:latest .
```

Run container:

```bash
docker run --rm -p 8080:8080 \
  -v /absolute/path/to/your/logs:/logs:ro \
  -v /absolute/path/to/your/project/reports:/app/reports \
  application-log-parser:latest
```

Then call API with file paths inside the container, for example:

```json
{
  "filePaths": [
    "/logs/application.log",
    "/logs/application-2.log"
  ]
}
```
