package com.techasians.alertservice.service.impl;

import org.junit.jupiter.api.Test;
import java.time.LocalDate;
import static org.junit.jupiter.api.Assertions.*;

class ReportSheetNameResolverTest {
    @Test
    void resolvesYearsAndWeekRangeTogetherAcrossNewYear() {
        LocalDate week = LocalDate.of(2026, 12, 28);
        assertEquals("LPS năm 2027", ReportSheetNameResolver.resolve("LPS năm {yyyy}", week));
        assertEquals("LPS năm 2026", ReportSheetNameResolver.resolve("LPS năm {yyyy-1}", week));
        assertEquals("LPS tuần 28-03.01", ReportSheetNameResolver.resolve(" LPS tuần {dd-dd.MM} ", week));
    }

    @Test
    void rejectsMissingContextAndUnresolvedTokens() {
        assertThrows(IllegalArgumentException.class, () -> ReportSheetNameResolver.resolve("Năm {abc}", LocalDate.of(2026, 9, 1)));
        assertThrows(IllegalArgumentException.class, () -> ReportSheetNameResolver.resolve(" ", LocalDate.of(2026, 9, 1)));
        assertThrows(IllegalArgumentException.class, () -> ReportSheetNameResolver.resolve("TH", null));
    }
}
