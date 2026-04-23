package com.kai.applicationlogparser.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Request payload for generating a report by scanning a folder for .log files.")
public class GenerateFolderReportRequest {

    @Schema(
            description = "Absolute or relative path to a folder that contains .log files.",
            example = "/workspace/sample-logs"
    )
    private String folderPath;

    @Schema(
            description = "Timezone used to interpret log timestamps without an explicit offset. "
                    + "Must be a valid java.time.ZoneId; defaults to UTC when omitted.",
            example = "America/Los_Angeles"
    )
    private String timezone;

    public String getFolderPath() {
        return folderPath;
    }

    public void setFolderPath(String folderPath) {
        this.folderPath = folderPath;
    }

    public String getTimezone() {
        return timezone;
    }

    public void setTimezone(String timezone) {
        this.timezone = timezone;
    }
}
