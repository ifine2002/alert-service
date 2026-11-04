package com.techasians.alertservice.util;

import org.apache.poi.ss.usermodel.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Optional;

public class ExcelPoiHelper {

    public static BigDecimal readDecimalCell(Cell cell) {
        if (cell == null) {
            return null;
        }

        switch (cell.getCellType()) {
            case NUMERIC:
                return BigDecimal.valueOf(cell.getNumericCellValue());
            case STRING:
                return parseStringToDecimal(cell.getStringCellValue());
            case FORMULA:
                return readCachedFormulaValueAsDecimal(cell);
            default:
                return null;
        }
    }

    private static BigDecimal readCachedFormulaValueAsDecimal(Cell cell) {
        CellType cachedType = cell.getCachedFormulaResultType();

        if (cachedType == CellType.NUMERIC) {
            return BigDecimal.valueOf(cell.getNumericCellValue());
        }

        if (cachedType == CellType.STRING) {
            return parseStringToDecimal(cell.getStringCellValue());
        }

        return null;
    }

    private static BigDecimal parseStringToDecimal(String strVal) {
        if (strVal == null || strVal.trim().isEmpty()) {
            return null;
        }
        strVal = strVal.trim().replace(",", ".").replace("%", "");
        try {
            return new BigDecimal(strVal);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public static Integer readIntegerCell(Cell cell) {
        BigDecimal val = readDecimalCell(cell);
        return val != null ? val.intValue() : null;
    }

    public static String readStringCell(Cell cell) {
        if (cell == null) {
            return null;
        }

        if (cell.getCellType() == CellType.FORMULA) {
            return readCachedFormulaValueAsString(cell);
        }

        return cell.toString().trim();
    }

    private static String readCachedFormulaValueAsString(Cell cell) {
        CellType cachedType = cell.getCachedFormulaResultType();

        if (cachedType == CellType.NUMERIC) {
            return String.valueOf(cell.getNumericCellValue());
        }

        if (cachedType == CellType.STRING) {
            return cell.getStringCellValue().trim();
        }

        if (cachedType == CellType.BOOLEAN) {
            return String.valueOf(cell.getBooleanCellValue());
        }

        return null;
    }

    public static BigDecimal roundToTwoDecimalPlaces(BigDecimal value) {
        if (value == null) {
            return null;
        }
        return value.setScale(2, RoundingMode.HALF_UP);
    }

    public static boolean isRowEmpty(Row row) {
        if (row == null) {
            return true;
        }
        for (int c = row.getFirstCellNum(); c < row.getLastCellNum(); c++) {
            Cell cell = row.getCell(c);
            if (cell != null && cell.getCellType() != CellType.BLANK) {
                return false;
            }
        }
        return true;
    }
}
