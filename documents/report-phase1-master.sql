-- ==========================================================
-- Phase 1: Master data bao cao tuan
-- Chay thu cong sau khi sao luu DB
-- Su dung ID co dinh, ON DUPLICATE KEY UPDATE de idempotent
-- ==========================================================

START TRANSACTION;

-- -------------------------------------------------------
-- 1. rpt_definitions
--    direction: 0=INPUT, 1=OUTPUT
--    node_type: 0=GROUP, 1=FILE
--    source_mode: 0=IMPORT, 1=GENERATE
--    file_kind:  0=NONE, 1=WORD, 2=EXCEL
-- -------------------------------------------------------
INSERT INTO rpt_definitions
  (id, name, direction, node_type, source_mode, file_kind,
   allowed_extension, template_relative_path, sort_order, status, created_by)
VALUES
-- 10 group INPUT
  (1001, N'Báo Cáo Lỗi Phát Sinh Tuần',             0,0, 0,0, '', NULL,  1,1, 'system'),
  (1002, N'Báo Cáo PAKH Dịch Vụ Di Động Tuần',  0,0, 0,0, '', NULL,  2,1, 'system'),
  (1003, N'Báo Cáo PAKH Dịch Vụ Chất Lượng Mạng Tuần',          0,0, 0,0, '', NULL,  3,1, 'system'),
  (1004, N'Báo Cáo PAKH Dịch Vụ SME Tuần',                         0,0, 0,0, '', NULL,  4,1, 'system'),
  (1005, N'Báo Cáo PAKH Dịch Vụ Cố Định Tuần',              0,0, 0,0, '', NULL,  5,1, 'system'),
  (1006, N'Báo Cáo Thu Thập Thông Tin Tuần Tại VTT',                    0,1, 0,1, 'docx', NULL,  6,1, 'system'),
  (1007, N'Báo Cáo Hoạt Động HappyCall Tuần',                    0,0, 0,0, '', NULL,  7,1, 'system'),
  (1008, N'Báo Cáo Chương Trình Viettel++',                          0,1, 0,1, 'docx', NULL,  8,1, 'system'),
  (1009, N'Báo Cáo Chất Lượng Tổng Đài',         0,0, 0,0, '', NULL,  9,1, 'system'),
  (1010, N'Báo Cáo Bất Cập Về Sản Phẩm/Dịch Vụ Trong Tuần',    0,1, 0,2, 'xlsx', NULL, 10,1, 'system'),
-- 21 file INPUT
  (1101, N'Báo Cáo Word Lỗi Phát Sinh Tuần',              0,1, 0,1, 'docx', NULL,  1,1, 'system'),
  (1102, N'Báo Cáo Excel Lỗi Phát Sinh Tuần',             0,1, 0,2, 'xlsx', NULL,  2,1, 'system'),
  (1103, N'Báo Cáo Word Dịch Vụ Di Động Tuần',          0,1, 0,1, 'docx', NULL,  3,1, 'system'),
  (1104, N'Báo Cáo Excel Dịch Vụ Di Động tuần',         0,1, 0,2, 'xlsx', NULL,  4,1, 'system'),
  (1105, N'Báo Cáo Word Dịch Vụ Chất Lượng Mạng Tuần',         0,1, 0,1, 'docx', NULL,  5,1, 'system'),
  (1106, N'Báo Cáo Excel 1 Dịch Vụ Chất Lượng Mạng Tuần',        0,1, 0,2, 'xlsx', NULL,  6,1, 'system'),
  (1107, N'Báo Cáo Excel 2 Dịch Vụ Chất Lượng Mạng Tuần',             0,1, 0,2, 'xlsx', NULL,  7,1, 'system'),
  (1108, N'Báo Cáo Word Dịch Vụ SME Tuần',              0,1, 0,1, 'docx', NULL,  8,1, 'system'),
  (1109, N'Báo Cáo Excel Dịch Vụ SME Tuần',             0,1, 0,2, 'xlsx', NULL,  9,1, 'system'),
  (1110, N'Báo Cáo Word Dịch Vụ Cố Định Tuần',         0,1, 0,1, 'docx', NULL,  10,1, 'system'),
  (1111, N'Báo Cáo Excel 1 Dịch Vụ Cố Định Tuần',        0,1, 0,2, 'xlsx', NULL,  11,1, 'system'),
  (1112, N'Báo Cáo Excel 2 Dịch Vụ Cố Định Tuần',             0,1, 0,2, 'xlsx', NULL,  12,1, 'system'),
  (1114, N'Báo Cáo Word Hoạt Động HappyCall Tuần',         0,1, 0,1, 'docx', NULL,  13,1, 'system'),
  (1115, N'Báo Cáo Excel 1 Hoạt Động HappyCall Tuần',        0,1, 0,2, 'xlsx', NULL,  14,1, 'system'),
  (1116, N'Báo Cáo Excel 2 Hoạt Động HappyCall Tuần',             0,1, 0,2, 'xlsx', NULL,  15,1, 'system'),
  (1117, N'Báo Cáo Excel 3 Hoạt Động HappyCall Tuần',             0,1, 0,2, 'xlsx', NULL,  16,1, 'system'),
  (1119, N'Báo Cáo Word Chất Lượng Tổng Đài',         0,1, 0,1, 'docx', NULL,  17,1, 'system'),
  (1120, N'Báo Cáo Excel 1 Chất Lượng Tổng Đài',        0,1, 0,2, 'xlsx', NULL,  18,1, 'system'),
  (1121, N'Báo Cáo Excel 2 Chất Lượng Tổng Đài',             0,1, 0,2, 'xlsx', NULL,  19,1, 'system'),
-- 3 file OUTPUT (generate)
  (2001, N'Báo cáo tuần VCX',           1,1, 1,1, 'docx', NULL, 1,1, 'system'),
  (2002, N'Phụ lục 1',                 1,1, 1,2, 'xlsx', 'templates/phu-luc-1-template.xlsx', 2,1, 'system'),
  (2003, N'Phụ lục 2',                 1,1, 1,2, 'xlsx', 'templates/phu-luc-2-template.xlsx', 3,1, 'system')
ON DUPLICATE KEY UPDATE
  name                  = VALUES(name),
  direction             = VALUES(direction),
  node_type             = VALUES(node_type),
  source_mode           = VALUES(source_mode),
  file_kind             = VALUES(file_kind),
  allowed_extension     = VALUES(allowed_extension),
  template_relative_path= VALUES(template_relative_path),
  sort_order            = VALUES(sort_order),
  status                = VALUES(status);

-- -------------------------------------------------------
-- 2. rpt_definition_links
-- -------------------------------------------------------
INSERT INTO rpt_definition_links
  (id, parent_definition_id, child_definition_id, sort_order, status, created_by)
VALUES
-- 1001 Lo phat sinh: 1101 Word, 1102 Excel phu luc
  (3001, 1001, 1101, 1, 1, 'system'),
  (3002, 1001, 1102, 2, 1, 'system'),
-- 1002 Di dong: 1103 Word, 1104 Excel
  (3003, 1002, 1103, 1, 1, 'system'),
  (3004, 1002, 1104, 2, 1, 'system'),
-- 1003 CLM Mang: 1108 Word (dung chung), 1116 Excel SLDH thang, 1109 Excel toan quoc tuan
  (3005, 1003, 1105, 1, 1, 'system'),
  (3006, 1003, 1106, 2, 1, 'system'),
  (3007, 1003, 1107, 3, 1, 'system'),
-- 1004 SME: 1108 Word (dung chung), 1110 Excel SME
  (3008, 1004, 1108, 1, 1, 'system'),
  (3009, 1004, 1109, 2, 1, 'system'),
-- 1005 Co dinh: 1105 Word, 1106 Excel co dinh, 1107 Excel TV360
  (3010, 1005, 1110, 1, 1, 'system'),
  (3011, 1005, 1111, 2, 1, 'system'),
  (3012, 1005, 1112, 3, 1, 'system'),
-- 1007 Hoạt Động HappyCall Tuần
  (3014, 1007, 1114, 1, 1, 'system'),
  (3015, 1007, 1115, 2, 1, 'system'),
  (3016, 1007, 1116, 3, 1, 'system'),
  (3017, 1007, 1117, 4, 1, 'system'),
-- 1009 Chất Lượng Tổng Đài
  (3019, 1009, 1119, 1, 1, 'system'),
  (3020, 1009, 1120, 2, 1, 'system'),
  (3021, 1009, 1121, 3, 1, 'system'),
ON DUPLICATE KEY UPDATE
  parent_definition_id = VALUES(parent_definition_id),
  child_definition_id  = VALUES(child_definition_id),
  sort_order           = VALUES(sort_order),
  status               = VALUES(status);

-- -------------------------------------------------------
-- 3. Cau hinh generate Excel Phu luc 1 va Phu luc 2
-- source_role: 0=TEXT, 1=DATA, 2=CHART
-- -------------------------------------------------------
INSERT INTO rpt_output_block_defs
  (id, output_definition_id, block_code, block_name, target_name, render_type, sort_order, status, created_by)
VALUES
  (5101,2002,'INCIDENT_WEEK',N'Lỗi phát sinh tuần',N'LPS_Tuần',1,1,1,'system'),
  (5102,2002,'STATION_WEEK',N'Lỗi trạm tuần',N'Lỗi Trạm_Tuần',1,2,1,'system'),
  (5103,2002,'CABLE_WEEK',N'Đứt cáp, cháy cáp tuần',N'Đứt cáp cháy cáp_Tuần',1,3,1,'system'),
  (5104,2002,'INCIDENT_YEAR',N'Lỗi phát sinh năm',N'Lỗi phát sinh_Năm',1,4,1,'system'),
  (5105,2002,'STATION_YEAR',N'Lỗi trạm năm',N'Lỗi trạm_Năm',1,5,1,'system'),
  (5201,2003,'PRODUCT_ISSUE_FILTER',N'Bất cập sản phẩm/dịch vụ trong tuần',N'Bất cập DV',1,1,1,'system')
ON DUPLICATE KEY UPDATE output_definition_id=VALUES(output_definition_id), block_code=VALUES(block_code),
  block_name=VALUES(block_name), target_name=VALUES(target_name), render_type=VALUES(render_type),
  sort_order=VALUES(sort_order), status=VALUES(status);

INSERT INTO rpt_output_source_maps
  (id, output_block_id, source_definition_id, source_role, source_sheet_name, source_section_def_id, sort_order, status, created_by)
VALUES
  (6101,5101,1102,1,N'LPS_Tuần',NULL,1,1,'system'),
  (6102,5102,1102,1,N'Lỗi Trạm_Tuần',NULL,1,1,'system'),
  (6103,5103,1102,1,N'Đứt cáp cháy cáp_Tuần',NULL,1,1,'system'),
  (6104,5104,1102,1,N'Lũy kế lỗi phát sinh năm',NULL,1,1,'system'),
  (6105,5105,1102,1,N'Lũy kế trạm năm',NULL,1,1,'system'),
  (6201,5201,1122,1,N'Bất cập DV',NULL,1,1,'system')
ON DUPLICATE KEY UPDATE output_block_id=VALUES(output_block_id), source_definition_id=VALUES(source_definition_id),
  source_role=VALUES(source_role), source_sheet_name=VALUES(source_sheet_name),
  source_section_def_id=VALUES(source_section_def_id), sort_order=VALUES(sort_order), status=VALUES(status);

-- -------------------------------------------------------
-- 4. Validate 2 file Excel input tao Phu luc 1 va Phu luc 2
-- Chi validate sheet bat buoc ton tai trong workbook.
-- Bo cau hinh cu; chay report-weekly-excel-validation-config.sql sau cac phase de dong bo danh muc hien tai.
-- -------------------------------------------------------
INSERT INTO rpt_excel_sheet_defs
  (id, definition_id, sheet_name, sort_order, status, created_by)
VALUES
  -- Definition 1102: Phu luc I - Danh sach su co phat sinh
  (7001,1102,N'LPS tuần 17-23.03',          1,1,'system'),
  (7002,1102,N'LPS năm 2026',              2,1,'system'),
  (7003,1102,N'Lỗi trạm tuần 17-23.03',     3,1,'system'),
  (7004,1102,N'Đứt cáp cháy cáp_Tuần',      4,1,'system'),
  (7005,1102,N'Lỗi Trạm năm 2026',          5,1,'system'),
  -- Definition 1122: Phu luc II - Tong hop cac bat cap
  (7006,1122,N'Bất cập',                    1,1,'system')
ON DUPLICATE KEY UPDATE
  definition_id=VALUES(definition_id), sheet_name=VALUES(sheet_name),
  sort_order=VALUES(sort_order), status=VALUES(status);

COMMIT;

-- -------------------------------------------------------
-- Kiem tra nhanh sau khi insert
-- -------------------------------------------------------
SELECT direction, node_type, COUNT(*) AS total
FROM rpt_definitions
WHERE id BETWEEN 1001 AND 2003
GROUP BY direction, node_type;

SELECT COUNT(*) AS total_links
FROM rpt_definition_links
WHERE id BETWEEN 3001 AND 3022;
