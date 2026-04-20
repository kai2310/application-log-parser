package com.kai.applicationlogparser.dto;

import java.util.List;

public class GenerateReportRequest {

    private List<String> filePaths;

    public List<String> getFilePaths() {
        return filePaths;
    }

    public void setFilePaths(List<String> filePaths) {
        this.filePaths = filePaths;
    }

}
