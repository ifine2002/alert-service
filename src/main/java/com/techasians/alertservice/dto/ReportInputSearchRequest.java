package com.techasians.alertservice.dto;

import com.techasians.alertservice.enums.RptInstanceStatus;

import java.time.LocalDate;
import java.util.List;

public class ReportInputSearchRequest {
    private LocalDate weekStartDate;
    private String keyword;
    private List<RptInstanceStatus> statuses;

    public LocalDate getWeekStartDate() { return weekStartDate; }
    public ReportInputSearchRequest setWeekStartDate(LocalDate value) { this.weekStartDate = value; return this; }
    public String getKeyword() { return keyword; }
    public ReportInputSearchRequest setKeyword(String value) { this.keyword = value; return this; }
    public List<RptInstanceStatus> getStatuses() { return statuses; }
    public ReportInputSearchRequest setStatuses(List<RptInstanceStatus> value) { this.statuses = value; return this; }
}