package com.techasians.alertservice.service.impl;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/** Quy ước chung cho import và output: năm lấy theo ngày kết thúc tuần. */
public final class ReportSheetNameResolver {
    private ReportSheetNameResolver() { }

    public static String resolve(String pattern, LocalDate weekStart) {
        if (pattern == null || pattern.trim().isEmpty()) throw new IllegalArgumentException("Tên sheet không được để trống");
        if (weekStart == null) throw new IllegalArgumentException("weekStartDate không được để trống");
        LocalDate end = weekStart.plusDays(6);
        int year = end.getYear();
        String period = weekStart.format(DateTimeFormatter.ofPattern("dd")) + "-"
                + end.format(DateTimeFormatter.ofPattern("dd.MM"));
        String name = pattern.trim().replace("{yyyy-1}", Integer.toString(year - 1))
                .replace("{yyyy}", Integer.toString(year)).replace("{dd-dd.MM}", period);
        if (name.contains("{") || name.contains("}")) {
            throw new IllegalArgumentException("Biến tên sheet chưa được hỗ trợ: " + pattern);
        }
        return name;
    }
}
