-- Sinh từ weekly-report-excel-requirements.json; chỉ cấu hình sheet bắt buộc.
SET NAMES utf8mb4;
START TRANSACTION;
CREATE TEMPORARY TABLE weekly_required_sheets (definition_id BIGINT, sheet_name VARCHAR(255), sort_order INT);
INSERT INTO weekly_required_sheets (definition_id,sheet_name,sort_order) VALUES
(1010,N'Bất cập DV',1),
(1102,N'TH',1),
(1102,N'Lũy kế lỗi phát sinh năm {yyyy}',2),
(1102,N'LPS năm {yyyy-1}',3),
(1102,N'Lũy kế trạm năm {yyyy}',4),
(1102,N'Tổng trạm {yyyy-1}',5),
(1102,N'LPS tuần',6),
(1102,N'Trạm tuần',7),
(1102,N'Đứt cáp cháy cáp',8),
(1104,N'PL02-So lieu TN&XL',1),
(1106,N'N.Xet tuan',1),
(1106,N'TH',2),
(1107,N'Tỷ lệ PA tuần',1),
(1112,N'NDPA',1),
(1112,N'NNLOI',2);
UPDATE rpt_excel_sheet_defs s
LEFT JOIN weekly_required_sheets r ON r.definition_id=s.definition_id AND BINARY r.sheet_name=BINARY s.sheet_name
SET s.status=0
WHERE s.definition_id IN (1010,1102,1104,1106,1107,1112) AND r.definition_id IS NULL;

INSERT INTO rpt_excel_sheet_defs (definition_id,sheet_name,sort_order,status,created_by)
SELECT r.definition_id,r.sheet_name,r.sort_order,1,'system'
FROM weekly_required_sheets r
WHERE NOT EXISTS (SELECT 1 FROM rpt_excel_sheet_defs s WHERE s.definition_id=r.definition_id AND BINARY s.sheet_name=BINARY r.sheet_name)
ORDER BY r.definition_id,r.sort_order;

UPDATE rpt_excel_sheet_defs s
JOIN weekly_required_sheets r ON r.definition_id=s.definition_id AND BINARY r.sheet_name=BINARY s.sheet_name
SET s.status=1,s.sort_order=r.sort_order;

-- Dùng năm kết thúc tuần cho nguồn lũy kế Phụ lục I.
UPDATE rpt_output_source_maps m JOIN rpt_output_block_defs b ON b.id=m.output_block_id
SET m.source_sheet_name='Lũy kế lỗi phát sinh năm {yyyy}'
WHERE b.output_definition_id=2002 AND b.block_code='INCIDENT_YEAR' AND m.source_definition_id=1102;
UPDATE rpt_output_source_maps m JOIN rpt_output_block_defs b ON b.id=m.output_block_id
SET m.source_sheet_name='Lũy kế trạm năm {yyyy}'
WHERE b.output_definition_id=2002 AND b.block_code='STATION_YEAR' AND m.source_definition_id=1102;

-- Đồng bộ tên sheet với dữ liệu 1010 và template Phụ lục II.
UPDATE rpt_output_source_maps m JOIN rpt_output_block_defs b ON b.id=m.output_block_id
SET m.source_sheet_name='Bất cập DV'
WHERE b.output_definition_id=2003 AND b.block_code='PRODUCT_ISSUE_FILTER' AND m.source_definition_id=1010;
UPDATE rpt_output_block_defs SET target_name='Bất cập DV'
WHERE output_definition_id=2003 AND block_code='PRODUCT_ISSUE_FILTER';

COMMIT;
DROP TEMPORARY TABLE weekly_required_sheets;

SELECT definition_id,sheet_name
FROM rpt_excel_sheet_defs
WHERE status=1 AND definition_id IN (1010,1102,1104,1106,1107,1112)
ORDER BY definition_id,sort_order,id;
