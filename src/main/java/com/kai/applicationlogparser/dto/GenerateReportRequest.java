package com.kai.applicationlogparser.dto;

import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(
        name = "GenerateReportRequest",
        description = "Request payload for generating a report from explicit log file paths."
)
public class GenerateReportRequest {

    @ArraySchema(
            schema = @Schema(
                    description = "Absolute path to a log file.",
                    example = "/workspace/sample-logs/application.log"
            ),
            minItems = 1,
            arraySchema = @Schema(
                    description = "List of log file paths to parse."
            )
    )
    private List<String> filePaths;

    @Schema(
            description = "Timezone used to interpret log timestamps without an explicit offset. "
                    + "Must be a valid java.time.ZoneId; defaults to UTC when omitted.",
            example = "America/Los_Angeles"
    )
    private String timezone;

    public List<String> getFilePaths() {
        return filePaths;
    }

    public void setFilePaths(List<String> filePaths) {
        this.filePaths = filePaths;
    }

    public String getTimezone() {
        return timezone;
    }

    public void setTimezone(String timezone) {
        this.timezone = timezone;
    }

}
