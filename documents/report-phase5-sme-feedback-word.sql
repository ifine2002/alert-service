-- Chạy sau cấu hình Word 2001, triển khai cùng handler SME và template mới.
-- Không thêm bảng/cột. SME dùng một nguồn Word 1108, TEXT=0, section 8110, không dùng sheet.
-- Marker và include_* lấy từ section; handler dừng trước ảnh đầu tiên hoặc marker kết thúc,
-- rồi gộp thành một paragraph, giữ nhấn mạnh nguồn.
START TRANSACTION;

-- Tạo mặc định nếu chưa có section; giữ marker/include_* đã cấu hình nếu section tồn tại.
INSERT INTO rpt_section_defs
(id,source_definition_id,section_name,extract_mode,start_marker,end_marker,item_count,include_start,include_end,status,created_by) VALUES
(8110,1108,'WORD_SME',0,'Tình hình tiếp nhận và xử lý PAKH:','Xu thế PAKH theo tuần:',NULL,0,0,1,'system')
ON DUPLICATE KEY UPDATE source_definition_id=VALUES(source_definition_id),status=VALUES(status);

UPDATE rpt_output_block_defs
SET render_type=1,target_name='BM_SME',status=1
WHERE id=8212 AND output_definition_id=2001 AND block_code='SME';

INSERT INTO rpt_output_source_maps
(id,output_block_id,source_definition_id,source_role,source_sheet_name,source_section_def_id,sort_order,status,created_by) VALUES
(8312,8212,1108,0,NULL,8110,1,1,'system')
ON DUPLICATE KEY UPDATE source_definition_id=VALUES(source_definition_id),source_role=VALUES(source_role),
source_sheet_name=NULL,source_section_def_id=VALUES(source_section_def_id),sort_order=VALUES(sort_order),status=VALUES(status);

-- Tắt mapping KPI cũ (8314) và mọi nguồn phụ của riêng block SME.
UPDATE rpt_output_source_maps SET status=0 WHERE output_block_id=8212 AND id<>8312;

COMMIT;

SELECT b.block_code,b.target_name,b.render_type,m.source_definition_id,m.source_role,
       m.source_section_def_id,m.source_sheet_name,m.sort_order,
       s.start_marker,s.end_marker,s.include_start,s.include_end,s.extract_mode
FROM rpt_output_block_defs b JOIN rpt_output_source_maps m ON m.output_block_id=b.id
LEFT JOIN rpt_section_defs s ON s.id=m.source_section_def_id
WHERE b.output_definition_id=2001 AND b.block_code='SME' AND b.status=1 AND m.status=1
ORDER BY m.sort_order;
