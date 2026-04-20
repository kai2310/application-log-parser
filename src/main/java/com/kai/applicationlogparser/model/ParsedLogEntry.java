package com.kai.applicationlogparser.model;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public record ParsedLogEntry(
        OffsetDateTime timestamp,
        String thread,
        String cid,
        String level,
        String logger,
        String message,
        String sourceFile,
        int sourceLine,
        List<String> continuationLines) {

    public ParsedLogEntry {
        continuationLines = continuationLines == null
                ? new ArrayList<>()
                : new ArrayList<>(continuationLines);
    }

    @Override
    public List<String> continuationLines() {
        return Collections.unmodifiableList(continuationLines);
    }
}
