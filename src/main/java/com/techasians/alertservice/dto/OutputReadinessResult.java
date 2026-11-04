package com.techasians.alertservice.dto;

import java.util.ArrayList;
import java.util.List;

/** Kết quả kiểm tra các nguồn bắt buộc của một báo cáo đầu ra. */
public class OutputReadinessResult {
    private boolean configured;
    private boolean readyToGenerate;
    private List<Long> missingSourceIds = new ArrayList<>();

    public boolean isConfigured() { return configured; }
    public OutputReadinessResult setConfigured(boolean configured) { this.configured = configured; return this; }
    public boolean isReadyToGenerate() { return readyToGenerate; }
    public OutputReadinessResult setReadyToGenerate(boolean readyToGenerate) { this.readyToGenerate = readyToGenerate; return this; }
    public List<Long> getMissingSourceIds() { return missingSourceIds; }
    public OutputReadinessResult setMissingSourceIds(List<Long> missingSourceIds) { this.missingSourceIds = missingSourceIds; return this; }
}
