# application-log-parser

Spring Boot + Java 25 + Docker application that parses multiple log files and generates issue reports on demand through an API.

## What this app does

- Accepts multiple log file paths in one request.
- Parses logs using this format:
  - `%d{ISO8601} [%thread] %X{CID} %-5level %logger{36} - %msg%n`
- Detects and reports:
  1. **Critical issues first** (heap/memory leak related issues such as `OutOfMemoryError`, `Java heap space`, `GC overhead limit exceeded`, `Metaspace`, etc.).
  2. **Error issues second** (`ERROR` level entries), grouped by same issue signature.
  3. **Warning issues third** (`WARN` / `WARNING` level entries), grouped by same issue signature.
- For each grouped issue, report includes:
  - Cause (best effort from message / "Caused by")
  - All occurrence times
  - Stack trace lines (when available)
- If no critical/error/warning issues exist, still generates a report with a "nothing found" summary.
- Saves report locally in:
  - `reports/application-logs-report-<datetime>.txt`
  - Datetime is generated in current system/user timezone.

## Project structure

- `src/main/java/com/kai/applicationlogparser/api` - Spring MVC REST controllers
- `src/main/java/com/kai/applicationlogparser/service` - parsing, analysis, and report generation
- `src/main/java/com/kai/applicationlogparser/model` - domain models
- `src/main/java/com/kai/applicationlogparser/dto` - API request/response DTOs
- `src/main/resources/application.properties` - Spring Boot configuration
- `start.sh` - start the app with Maven/Spring Boot
- `docker-start.sh` - build and start the app with Docker
- `reports/` - generated at runtime (gitignored)

## Requirements

- Java 25
- Maven 3.9+
- (Optional) Docker

## Run locally

1. Start the Spring Boot app with Maven:

```bash
./start.sh
```

This script runs:

```bash
mvn spring-boot:run
```

Application API port is `8080`. Override with:

```bash
PORT=9091 ./start.sh
```

The Spring Boot Actuator management port is fixed at `9090` and exposes:

- `/actuator/health`
- `/actuator/info`

Health check:

```bash
curl http://localhost:9090/actuator/health
```

Info endpoint:

```bash
curl http://localhost:9090/actuator/info
```

## Generate reports using API

Endpoint:

- `POST /api/reports`
- `POST /api/reports/folder`

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
  "warningIssueGroups": 6,
  "ignoredFiles": [],
  "message": "Report generated successfully"
}
```

You can call this endpoint repeatedly while the app is running to generate multiple reports at different times.

Generate report by scanning one folder for `.log` files:

```bash
curl -X POST "http://localhost:8080/api/reports/folder" \
  -H "Content-Type: application/json" \
  -d '{
    "folderPath": "/workspace/sample-logs"
  }'
```

Example response (same response schema as `POST /api/reports`):

```json
{
  "reportPath": "/workspace/reports/application-logs-report-20260416-143212.txt",
  "totalEntriesProcessed": 3589,
  "criticalIssueGroups": 2,
  "errorIssueGroups": 4,
  "warningIssueGroups": 6,
  "ignoredFiles": [
    "/workspace/sample-logs/notes.txt"
  ],
  "message": "Report generated successfully"
}
```

Request body for folder endpoint:

```json
{
  "folderPath": "/absolute/path/to/folder/with/log-files"
}
```

Behavior:

- Reads only regular files ending with `.log` (case-insensitive) from the provided folder.
- Uses the same response payload shape as `POST /api/reports`.
- Includes non-`.log` regular files from that folder in `ignoredFiles`.
- Does not recurse into subfolders.
- Returns `400` if folder path is blank, does not exist, or contains no `.log` files.

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

3) WARNING ISSUES
1. Connection pool usage is high: active 92%
   Cause: Cause not explicitly available in logs
   Occurrences: 5
   Times:
     - 2026-04-16 11:55:15 -07:00
     - ...
   Stack Trace:
     at com.example.ConnectionPool.monitor(...)
     at ...
```

If nothing is found:

```text
SUMMARY
No critical issues, errors, or warnings were detected in the provided logs.
```

## Run with Docker

Build image and run container:

```bash
./docker-start.sh
```

With optional environment variables:

```bash
HOST_PORT=8080 CONTAINER_PORT=8080 \
ACTUATOR_HOST_PORT=9090 ACTUATOR_CONTAINER_PORT=9090 \
LOGS_DIR=/absolute/path/to/your/logs \
REPORTS_DIR=/absolute/path/to/your/project/reports \
./docker-start.sh
```

This script runs:

```bash
docker build -t application-log-parser:latest .
docker run --rm -p 8080:8080 -p 9090:9090 \
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
