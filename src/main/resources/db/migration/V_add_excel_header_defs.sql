-- ============================================================
-- Migration: Tạo bảng rpt_excel_header_defs
-- Schema mới: tìm header theo nguyên tên, không phụ thuộc tọa độ.
-- ============================================================

-- Bảng rpt_excel_sheet_defs giữ nguyên, KHÔNG thêm gì

-- Tạo bảng rpt_excel_header_defs
CREATE TABLE rpt_excel_header_defs (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    sheet_def_id  BIGINT       NOT NULL COMMENT 'FK tới rpt_excel_sheet_defs.id',
    header_name   VARCHAR(500) NOT NULL COMMENT 'Tên header bắt buộc phải có trong một ô bất kỳ của sheet',
    status        INT          NOT NULL DEFAULT 1 COMMENT '1: bắt buộc validate, 0: bỏ qua',
    created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by    VARCHAR(100) NOT NULL DEFAULT '',

    CONSTRAINT fk_header_defs_sheet
        FOREIGN KEY (sheet_def_id) REFERENCES rpt_excel_sheet_defs(id)
        ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
  COMMENT='Định nghĩa tên header bắt buộc trong mỗi sheet Excel';

-- ============================================================
-- Dữ liệu mẫu
-- ============================================================
-- Giả sử bảng rpt_excel_sheet_defs đã có:
--   id=1 → sheet_name='Dịch vụ'    (thuộc definition_id=42)
--   id=2 → sheet_name='Hạ tầng'    (thuộc definition_id=42)
--
-- Cấu trúc file Excel template:
--   Sheet "Dịch vụ":
--     Row 0 (dòng 1): [Title] Báo cáo dịch vụ tuần
--     Row 1 (dòng 2): [trống]
--     Row 2 (dòng 3): [Header] Chủ dịch vụ | Ngày phát hiện | Dịch vụ | ...
--
-- Các tên này có thể nằm ở bất kỳ dòng/cột nào của sheet.

-- INSERT INTO rpt_excel_header_defs (sheet_def_id, header_name, created_by)
-- VALUES
--     (1, 'Chủ dịch vụ', 'system'),
--     (1, 'Ngày phát hiện', 'system'),
--     (1, 'Dịch vụ', 'system');
