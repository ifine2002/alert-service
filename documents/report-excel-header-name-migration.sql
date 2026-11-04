-- Chạy một lần trên DB hiện tại trước khi triển khai code validate header theo tên.
-- Dữ liệu header_name, sheet_def_id và status được giữ nguyên.
ALTER TABLE rpt_excel_header_defs
    DROP COLUMN row_index,
    DROP COLUMN column_index;
