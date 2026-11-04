package com.techasians.alertservice.service;

import java.nio.file.Path;
import java.time.LocalDate;

/** Sinh file Excel cho một definition output trong một tuần báo cáo. */
public interface ExcelOutputGenerator {
    boolean supports(Long outputDefinitionId);

    Path generate(Long outputDefinitionId, LocalDate weekStartDate);
}
