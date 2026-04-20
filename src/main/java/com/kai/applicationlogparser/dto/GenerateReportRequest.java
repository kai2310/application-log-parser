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

    public List<String> getFilePaths() {
        return filePaths;
    }

    public void setFilePaths(List<String> filePaths) {
        this.filePaths = filePaths;
    }

}
