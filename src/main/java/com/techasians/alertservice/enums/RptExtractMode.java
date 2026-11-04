package com.techasians.alertservice.enums;

/** Thứ tự enum phải khớp trực tiếp với rpt_section_defs.extract_mode. */
public enum RptExtractMode {
    TEXT_RANGE(0),
    WHOLE_DOCUMENT(1),
    PARAGRAPH_MARKER(2),
    NEXT_PARAGRAPHS(3),
    NEXT_LIST_ITEMS(4);

    private final int value;

    RptExtractMode(int value) {
        this.value = value;
    }

    public int getValue() {
        return value;
    }
}
