-- Sinh từ weekly-report-excel-requirements.json bằng documents/scripts/generate-weekly-excel-validation.ps1.
-- Chạy sau report-excel-header-name-migration.sql trên DB đã có dữ liệu.
-- Thay thế bộ validate của 6 definition dưới đây; metadata cũ ngoài danh mục chuyển status=0, không xóa bản ghi.
SET NAMES utf8mb4;
START TRANSACTION;
CREATE TEMPORARY TABLE weekly_required_sheets (definition_id BIGINT, sheet_name VARCHAR(255), sort_order INT);
CREATE TEMPORARY TABLE weekly_required_headers (definition_id BIGINT, sheet_name VARCHAR(255), header_name VARCHAR(500));
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
INSERT INTO weekly_required_headers (definition_id,sheet_name,header_name) VALUES
(1010,N'Bất cập DV',N'Ngày phát hiện'),
(1010,N'Bất cập DV',N'Tiến độ'),
(1010,N'Bất cập DV',N'Nội dung'),
(1010,N'Bất cập DV',N'Kết quả'),
(1010,N'Bất cập DV',N'Dịch vụ'),
(1102,N'TH',N'Tổng lỗi phát sinh'),
(1102,N'TH',N'So sánh lỗi trạm'),
(1102,N'TH',N'So sánh Sự cố đứt cáp, cháy cáp trục, cáp nhánh'),
(1102,N'TH',N'Số lượng'),
(1102,N'TH',N'PA'),
(1102,N'TH',N'TB ảnh hưởng'),
(1102,N'TH',N'Tỷ lệ tăng giảm'),
(1102,N'Lũy kế lỗi phát sinh năm {yyyy}',N'Năm'),
(1102,N'Lũy kế lỗi phát sinh năm {yyyy}',N'Tháng'),
(1102,N'Lũy kế lỗi phát sinh năm {yyyy}',N'Ngày'),
(1102,N'Lũy kế lỗi phát sinh năm {yyyy}',N'Chủ dịch vụ'),
(1102,N'LPS năm {yyyy-1}',N'Tháng'),
(1102,N'LPS năm {yyyy-1}',N'Ngày'),
(1102,N'LPS năm {yyyy-1}',N'Chủ dịch vụ'),
(1102,N'Lũy kế trạm năm {yyyy}',N'Năm'),
(1102,N'Lũy kế trạm năm {yyyy}',N'Tháng'),
(1102,N'Lũy kế trạm năm {yyyy}',N'Ngày'),
(1102,N'Lũy kế trạm năm {yyyy}',N'Tổng PA'),
(1102,N'Lũy kế trạm năm {yyyy}',N'Tổng ảnh hưởng'),
(1102,N'Lũy kế trạm năm {yyyy}',N'Nguyên nhân cấp 1'),
(1102,N'Lũy kế trạm năm {yyyy}',N'Tỉnh'),
(1102,N'Tổng trạm {yyyy-1}',N'Năm'),
(1102,N'Tổng trạm {yyyy-1}',N'Tháng'),
(1102,N'Tổng trạm {yyyy-1}',N'Ngày'),
(1102,N'Tổng trạm {yyyy-1}',N'Tổng PA'),
(1102,N'Tổng trạm {yyyy-1}',N'Tổng ảnh hưởng'),
(1102,N'Tổng trạm {yyyy-1}',N'Nguyên nhân cấp 1'),
(1102,N'Tổng trạm {yyyy-1}',N'Tỉnh'),
(1102,N'LPS tuần',N'Chủ dịch vụ'),
(1102,N'Trạm tuần',N'Năm'),
(1102,N'Trạm tuần',N'Tháng'),
(1102,N'Trạm tuần',N'Ngày'),
(1102,N'Trạm tuần',N'Mã trạm'),
(1102,N'Trạm tuần',N'Tỉnh'),
(1102,N'Đứt cáp cháy cáp',N'Năm'),
(1102,N'Đứt cáp cháy cáp',N'Tháng'),
(1102,N'Đứt cáp cháy cáp',N'Ngày'),
(1102,N'Đứt cáp cháy cáp',N'Mã trạm'),
(1102,N'Đứt cáp cháy cáp',N'Tỉnh'),
(1104,N'PL02-So lieu TN&XL',N'STT'),
(1104,N'PL02-So lieu TN&XL',N'Dịch vụ'),
(1104,N'PL02-So lieu TN&XL',N'Tăng giảm'),
(1104,N'PL02-So lieu TN&XL',N'Tỷ lệ'),
(1104,N'PL02-So lieu TN&XL',N'Phản ánh báo hỏng dịch vụ'),
(1106,N'N.Xet tuan',N'Cùng kỳ tuần trước'),
(1106,N'N.Xet tuan',N'HTDV + SỰ CỐ tiếp nhận chuyển BO'),
(1106,N'N.Xet tuan',N'HTDV + SỰ CỐ tiếp nhận chuyển KT'),
(1106,N'N.Xet tuan',N'SỰ CỐ tiếp nhận chuyển KT'),
(1106,N'N.Xet tuan',N'TLPA/10K'),
(1106,N'N.Xet tuan',N'FTTH GPON'),
(1106,N'N.Xet tuan',N'THS'),
(1106,N'TH',N'Chỉ tiêu'),
(1106,N'TH',N'KPI'),
(1106,N'TH',N'FTTH_Gpon'),
(1106,N'TH',N'Truyền hình số'),
(1107,N'Tỷ lệ PA tuần',N'Tỷ lệ chung'),
(1107,N'Tỷ lệ PA tuần',N'Chỉ tiêu thoại'),
(1107,N'Tỷ lệ PA tuần',N'Chỉ tiêu Data'),
(1107,N'Tỷ lệ PA tuần',N'PA'),
(1107,N'Tỷ lệ PA tuần',N'Thuê bao'),
(1107,N'Tỷ lệ PA tuần',N'KQ'),
(1107,N'Tỷ lệ PA tuần',N'KPIs'),
(1107,N'Tỷ lệ PA tuần',N'Đánh giá'),
(1107,N'Tỷ lệ PA tuần',N'Nguồn tiếp nhận'),
(1107,N'Tỷ lệ PA tuần',N'TOP 5 Tỉnh Thoại tăng cao nhất Tuần'),
(1107,N'Tỷ lệ PA tuần',N'TOP 5 Tỉnh Data tăng cao nhất Tuần'),
(1107,N'Tỷ lệ PA tuần',N'Tỉnh'),
(1107,N'Tỷ lệ PA tuần',N'Tuần hiện tại'),
(1112,N'NDPA',N'STT'),
(1112,N'NDPA',N'Nội dung phản ánh'),
(1112,N'NDPA',N'TB ngày'),
(1112,N'NDPA',N'Tỷ trọng ngày'),
(1112,N'NDPA',N'DỊCH VỤ TV360'),
(1112,N'NNLOI',N'STT'),
(1112,N'NNLOI',N'Nguyên nhân lỗi'),
(1112,N'NNLOI',N'TB ngày'),
(1112,N'NNLOI',N'Tỷ trọng ngày'),
(1112,N'NNLOI',N'DỊCH VỤ TV360'),
(1112,N'NNLOI',N'Hỗ trợ khách hàng'),
(1112,N'NNLOI',N'Lỗi phía khách hàng');
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

UPDATE rpt_excel_header_defs h
JOIN rpt_excel_sheet_defs s ON s.id=h.sheet_def_id
LEFT JOIN weekly_required_headers r ON r.definition_id=s.definition_id AND BINARY r.sheet_name=BINARY s.sheet_name AND BINARY r.header_name=BINARY h.header_name
SET h.status=0
WHERE s.definition_id IN (1010,1102,1104,1106,1107,1112) AND r.definition_id IS NULL;

INSERT INTO rpt_excel_header_defs (sheet_def_id,header_name,status,created_by)
SELECT s.id,r.header_name,1,'system'
FROM weekly_required_headers r
JOIN rpt_excel_sheet_defs s ON s.definition_id=r.definition_id AND BINARY s.sheet_name=BINARY r.sheet_name
WHERE NOT EXISTS (SELECT 1 FROM rpt_excel_header_defs h WHERE h.sheet_def_id=s.id AND BINARY h.header_name=BINARY r.header_name);

UPDATE rpt_excel_header_defs h
JOIN rpt_excel_sheet_defs s ON s.id=h.sheet_def_id
JOIN weekly_required_headers r ON r.definition_id=s.definition_id AND BINARY r.sheet_name=BINARY s.sheet_name AND BINARY r.header_name=BINARY h.header_name
SET h.status=1;

-- Bỏ năm literal của hai nguồn Phụ lục I để cùng quy tắc năm kết thúc tuần.
UPDATE rpt_output_source_maps m JOIN rpt_output_block_defs b ON b.id=m.output_block_id
SET m.source_sheet_name='Lũy kế lỗi phát sinh năm {yyyy}'
WHERE b.output_definition_id=2002 AND b.block_code='INCIDENT_YEAR' AND m.source_definition_id=1102;
UPDATE rpt_output_source_maps m JOIN rpt_output_block_defs b ON b.id=m.output_block_id
SET m.source_sheet_name='Lũy kế trạm năm {yyyy}'
WHERE b.output_definition_id=2002 AND b.block_code='STATION_YEAR' AND m.source_definition_id=1102;

-- Mẫu đầu vào 1010 và template Phụ lục II đều dùng Bất cập DV.
UPDATE rpt_output_source_maps m JOIN rpt_output_block_defs b ON b.id=m.output_block_id
SET m.source_sheet_name='Bất cập DV'
WHERE b.output_definition_id=2003 AND b.block_code='PRODUCT_ISSUE_FILTER' AND m.source_definition_id=1010;
UPDATE rpt_output_block_defs SET target_name='Bất cập DV'
WHERE output_definition_id=2003 AND block_code='PRODUCT_ISSUE_FILTER';

COMMIT;
DROP TEMPORARY TABLE weekly_required_headers;
DROP TEMPORARY TABLE weekly_required_sheets;

SELECT s.definition_id,s.sheet_name,h.header_name
FROM rpt_excel_sheet_defs s LEFT JOIN rpt_excel_header_defs h ON h.sheet_def_id=s.id AND h.status=1
WHERE s.status=1 AND s.definition_id IN (1010,1102,1104,1106,1107,1112)
ORDER BY s.definition_id,s.sort_order,h.id;
