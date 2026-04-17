package com.applicationlogparser.model;

import java.time.ZonedDateTime;
import java.util.List;

public record IssueRecord(
        IssueType issueType,
        String title,
        String cause,
        List<String> causes,
        List<ZonedDateTime> occurrences,
        List<String> stackTraceLines,
        String logger
) {
}
