package com.techasians.alertservice.dto;

import com.techasians.alertservice.entity.TrafficEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class TrafficEventImportRow {
    private int rowIndex;
    private int sheetEventType;
    private int resultColumnIndex;
    private String eventName;
    private TrafficEvent existingEvent;
    private TrafficEventRequest request;
    private TrafficEventScheduleRequest scheduleRequest;
    private final List<String> errors = new ArrayList<>();

    public int getRowIndex() {
        return rowIndex;
    }

    public TrafficEventImportRow setRowIndex(int rowIndex) {
        this.rowIndex = rowIndex;
        return this;
    }

    public int getSheetEventType() {
        return sheetEventType;
    }

    public TrafficEventImportRow setSheetEventType(int sheetEventType) {
        this.sheetEventType = sheetEventType;
        return this;
    }

    public int getResultColumnIndex() {
        return resultColumnIndex;
    }

    public TrafficEventImportRow setResultColumnIndex(int resultColumnIndex) {
        this.resultColumnIndex = resultColumnIndex;
        return this;
    }

    public String getEventName() {
        return eventName;
    }

    public TrafficEventImportRow setEventName(String eventName) {
        this.eventName = eventName;
        return this;
    }

    public TrafficEvent getExistingEvent() {
        return existingEvent;
    }

    public TrafficEventImportRow setExistingEvent(TrafficEvent existingEvent) {
        this.existingEvent = existingEvent;
        return this;
    }

    public TrafficEventRequest getRequest() {
        return request;
    }

    public TrafficEventImportRow setRequest(TrafficEventRequest request) {
        this.request = request;
        return this;
    }

    public TrafficEventScheduleRequest getScheduleRequest() {
        return scheduleRequest;
    }

    public TrafficEventImportRow setScheduleRequest(TrafficEventScheduleRequest scheduleRequest) {
        this.scheduleRequest = scheduleRequest;
        return this;
    }

    public List<String> getErrors() {
        return errors;
    }

    public void addError(String message) {
        errors.add(message);
    }

    public boolean hasErrors() {
        return !errors.isEmpty();
    }

    public String buildResultMessage() {
        return errors.stream()
                .map(error -> "- " + error)
                .collect(Collectors.joining("\n"));
    }
}
