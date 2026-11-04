-- Chạy sau cấu hình Word 2001 và triển khai cùng template/handler FIXED mới.
-- Dùng các cột DB hiện có. DATA=1; sort_order: 1=N.Xet tuan, 2=TH.
-- Cùng workbook 1106 chỉ tải/mở một lần. Nhãn dòng/cột và ngày tuần xác định vị trí số liệu.
-- TB tiếp nhận = (HTDV + sự cố chuyển BO + HTDV + sự cố chuyển KT) / 7.
-- SC/ngày = dòng SỰ CỐ chuyển KT / 7; biến động so với cùng dòng tuần trước.
-- TLPA và chênh lệch PA đọc dòng TLPA/10K, cột tuần hiện tại và cùng kỳ tuần trước trong N.Xet tuan.
-- TLPA GPON/THS đọc dòng FTTH GPON/THS trong N.Xet tuan; KPI đọc TH, không cố định 12/5 trong Java.
-- Xác định bảng và cột theo nhãn/ngày tuần, bỏ qua cột lịch sử; không dùng TPLA tuần N hay Tổng DVCĐ.
-- Công thức nội bộ được tính lại; công thức không hỗ trợ/liên kết ngoài dùng cache kèm cảnh báo chưa xác minh độ mới.
START TRANSACTION;

UPDATE rpt_output_block_defs SET render_type=1,target_name='BM_FIXED',status=1
WHERE id=8210 AND output_definition_id=2001 AND block_code='FIXED';

INSERT INTO rpt_output_source_maps
(id,output_block_id,source_definition_id,source_role,source_sheet_name,source_section_def_id,sort_order,status,created_by) VALUES
(8310,8210,1106,1,'N.Xet tuan',NULL,1,1,'system'),
(8313,8210,1106,1,'TH',NULL,2,1,'system')
ON DUPLICATE KEY UPDATE source_definition_id=VALUES(source_definition_id),source_role=VALUES(source_role),
source_sheet_name=VALUES(source_sheet_name),source_section_def_id=NULL,sort_order=VALUES(sort_order),status=VALUES(status);

UPDATE rpt_output_source_maps SET status=0 WHERE output_block_id=8210 AND id NOT IN (8310,8313);

COMMIT;

SELECT b.block_code,b.target_name,b.render_type,m.source_definition_id,m.source_sheet_name,m.source_role,m.sort_order
FROM rpt_output_block_defs b JOIN rpt_output_source_maps m ON m.output_block_id=b.id
WHERE b.output_definition_id=2001 AND b.block_code='FIXED' AND b.status=1 AND m.status=1 ORDER BY m.sort_order;
