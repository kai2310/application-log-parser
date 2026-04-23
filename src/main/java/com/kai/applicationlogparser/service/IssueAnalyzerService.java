package com.kai.applicationlogparser.service;

import com.kai.applicationlogparser.model.IssueRecord;
import com.kai.applicationlogparser.model.IssueType;
import com.kai.applicationlogparser.model.ParsedLogEntry;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

@Service
public class IssueAnalyzerService {

    private static final Pattern CAUSE_PATTERN = Pattern.compile("(?i)(?:caused by|root cause|because|due to)[:\\s]+(.+)");
    private static final Pattern EXCEPTION_PATTERN = Pattern.compile(".*\\b(\\w+(?:Exception|Error))\\b.*");
    private static final List<String> CRITICAL_PATTERNS = List.of(
        "outofmemoryerror",
        "java heap space",
        "gc overhead limit exceeded",
        "metaspace",
        "memory leak",
        "unable to create new native thread",
        "killed process"
    );

    public AnalysisResult analyze(List<ParsedLogEntry> entries) {
        return analyze(entries, ZoneId.of("UTC"));
    }

    public AnalysisResult analyze(List<ParsedLogEntry> entries, ZoneId targetZone) {
        Map<String, Aggregation> criticalAggregations = new LinkedHashMap<>();
        Map<String, Aggregation> errorAggregations = new LinkedHashMap<>();
        Map<String, Aggregation> warningAggregations = new LinkedHashMap<>();

        for (ParsedLogEntry entry : entries) {
            String loweredMessage = entry.message().toLowerCase(Locale.ROOT);
            IssueType issueType = classify(entry.level(), loweredMessage);
            if (issueType == null) {
                continue;
            }

            String signature = buildSignature(entry.message(), issueType);
            Aggregation aggregation;
            if (issueType == IssueType.CRITICAL) {
                aggregation = criticalAggregations.computeIfAbsent(signature, ignored -> new Aggregation(signature));
            } else if (issueType == IssueType.ERROR) {
                aggregation = errorAggregations.computeIfAbsent(signature, ignored -> new Aggregation(signature));
            } else {
                aggregation = warningAggregations.computeIfAbsent(signature, ignored -> new Aggregation(signature));
            }

            aggregation.times.add(entry.timestamp());
            aggregation.sampleLogger = aggregation.sampleLogger == null ? entry.logger() : aggregation.sampleLogger;
            aggregation.causes.addAll(extractCauses(entry.message()));
            appendStackTrace(entry, aggregation.stackTraceLines);
        }

        return new AnalysisResult(
            toSortedRecords(criticalAggregations.values(), IssueType.CRITICAL, targetZone),
            toSortedRecords(errorAggregations.values(), IssueType.ERROR, targetZone),
            toSortedRecords(warningAggregations.values(), IssueType.WARNING, targetZone)
        );
    }

    private List<IssueRecord> toSortedRecords(Iterable<Aggregation> aggregations, IssueType issueType, ZoneId targetZone) {
        List<IssueRecord> result = new ArrayList<>();
        for (Aggregation aggregation : aggregations) {
            List<ZonedDateTime> sortedTimes = aggregation.times.stream()
                .sorted()
                .map(offsetDateTime -> offsetDateTime.atZoneSameInstant(targetZone))
                .toList();
            String primaryCause = aggregation.causes.isEmpty()
                    ? inferCauseFromSummary(aggregation.summary)
                    : aggregation.causes.get(0);
            List<String> distinctCauses = aggregation.causes.stream().distinct().toList();
            List<String> stackTrace = aggregation.stackTraceLines.stream().distinct().toList();
            result.add(new IssueRecord(
                issueType,
                aggregation.summary,
                primaryCause,
                distinctCauses,
                sortedTimes,
                stackTrace,
                aggregation.sampleLogger
            ));
        }

        return result.stream()
            .sorted(Comparator.comparing(
                record -> record.occurrences().isEmpty() ? ZonedDateTime.of(9999, 12, 31, 23, 59, 59, 0, targetZone) : record.occurrences().get(0)
            ))
            .collect(Collectors.toList());
    }

    private IssueType classify(String level, String loweredMessage) {
        if (isCritical(loweredMessage)) {
            return IssueType.CRITICAL;
        }

        if ("ERROR".equalsIgnoreCase(level)) {
            return IssueType.ERROR;
        }

        if ("WARN".equalsIgnoreCase(level) || "WARNING".equalsIgnoreCase(level)) {
            return IssueType.WARNING;
        }

        return null;
    }

    private boolean isCritical(String loweredMessage) {
        for (String pattern : CRITICAL_PATTERNS) {
            if (loweredMessage.contains(pattern)) {
                return true;
            }
        }
        return false;
    }

    private String buildSignature(String message, IssueType issueType) {
        String trimmed = message.trim();
        if (issueType == IssueType.CRITICAL) {
            if (trimmed.length() > 180) {
                return trimmed.substring(0, 180);
            }
            return trimmed;
        }

        Matcher exceptionMatcher = EXCEPTION_PATTERN.matcher(trimmed);
        if (exceptionMatcher.matches()) {
            return exceptionMatcher.group(1) + ":" + sanitize(trimmed);
        }

        return sanitize(trimmed);
    }

    private String sanitize(String message) {
        String sanitized = message
            .replaceAll("\\d{4}-\\d{2}-\\d{2}[T\\s]\\d{2}:\\d{2}:\\d{2}(?:[.,]\\d{3})?(?:Z|[+-]\\d{2}:\\d{2})?", "<timestamp>")
            .replaceAll("\\b\\d+\\b", "<num>");
        return sanitized.length() > 240 ? sanitized.substring(0, 240) : sanitized;
    }

    private List<String> extractCauses(String message) {
        List<String> causes = new ArrayList<>();
        Matcher matcher = CAUSE_PATTERN.matcher(message);
        while (matcher.find()) {
            causes.add(matcher.group(1).trim());
        }

        if (causes.isEmpty()) {
            Matcher exceptionMatcher = EXCEPTION_PATTERN.matcher(message);
            if (exceptionMatcher.matches()) {
                causes.add(exceptionMatcher.group(1));
            }
        }
        return causes;
    }

    private String inferCauseFromSummary(String summary) {
        Matcher exceptionMatcher = EXCEPTION_PATTERN.matcher(summary);
        if (exceptionMatcher.matches()) {
            return exceptionMatcher.group(1);
        }
        return "Cause not explicitly available in logs";
    }

    private void appendStackTrace(ParsedLogEntry entry, List<String> stackTraceLines) {
        for (String continuationLine : entry.continuationLines()) {
            String line = continuationLine.trim();
            if (line.startsWith("at ") || line.startsWith("Caused by") || line.matches(".*\\b(\\w+Exception|\\w+Error):.*")) {
                stackTraceLines.add(line);
            }
        }
    }

    public record AnalysisResult(
        List<IssueRecord> criticalIssues,
        List<IssueRecord> errorIssues,
        List<IssueRecord> warningIssues
    ) {
    }

    private static final class Aggregation {
        private final String summary;
        private final List<OffsetDateTime> times;
        private final List<String> causes;
        private final List<String> stackTraceLines;
        private String sampleLogger;

        private Aggregation(String summary) {
            this.summary = summary;
            this.times = new ArrayList<>();
            this.causes = new ArrayList<>();
            this.stackTraceLines = new ArrayList<>();
        }
    }
}
