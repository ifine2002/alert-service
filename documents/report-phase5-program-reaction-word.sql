-- Chạy sau cấu hình Word 2001 để cập nhật riêng hai phần II/III.
-- COPY_SECTION=0, TEXT=0, TEXT_RANGE=0. Cả hai dùng cùng file Word 1119 của tuần báo cáo.
-- Bỏ tiêu đề I/II nguồn vì template đã có tiêu đề II/III tương ứng.
-- Phần I lấy đến trước phần II; phần II lấy đến hết tài liệu nguồn.
START TRANSACTION;

INSERT INTO rpt_section_defs
(id,source_definition_id,section_name,extract_mode,start_marker,end_marker,item_count,include_start,include_end,status,created_by) VALUES
(8104,1119,'WORD_PROGRAM',0,'CÁC CHƯƠNG TRÌNH/SỰ KIỆN TRỌNG ĐIỂM, NỔI BẬT','PHẢN ỨNG KH VỀ CÁC CHƯƠNG TRÌNH/CHÍNH SÁCH MỚI/ĐIỀU CHỈNH',NULL,0,0,1,'system'),
(8105,1119,'WORD_REACTION',0,'PHẢN ỨNG KH VỀ CÁC CHƯƠNG TRÌNH/CHÍNH SÁCH MỚI/ĐIỀU CHỈNH',NULL,NULL,0,0,1,'system')
ON DUPLICATE KEY UPDATE source_definition_id=VALUES(source_definition_id),extract_mode=VALUES(extract_mode),
start_marker=VALUES(start_marker),end_marker=VALUES(end_marker),item_count=NULL,
include_start=VALUES(include_start),include_end=VALUES(include_end),status=VALUES(status);

UPDATE rpt_output_block_defs
SET render_type=0,target_name='BM_PROGRAM_EVENT',status=1
WHERE id=8205 AND output_definition_id=2001 AND block_code='PROGRAM_EVENT';
UPDATE rpt_output_block_defs
SET render_type=0,target_name='BM_CUSTOMER_REACTION',status=1
WHERE id=8206 AND output_definition_id=2001 AND block_code='CUSTOMER_REACTION';

INSERT INTO rpt_output_source_maps
(id,output_block_id,source_definition_id,source_role,source_sheet_name,source_section_def_id,sort_order,status,created_by) VALUES
(8305,8205,1119,0,NULL,8104,1,1,'system'),
(8306,8206,1119,0,NULL,8105,1,1,'system')
ON DUPLICATE KEY UPDATE source_definition_id=VALUES(source_definition_id),source_role=VALUES(source_role),
source_sheet_name=NULL,source_section_def_id=VALUES(source_section_def_id),sort_order=VALUES(sort_order),status=VALUES(status);

UPDATE rpt_output_source_maps SET status=0 WHERE output_block_id=8205 AND id<>8305;
UPDATE rpt_output_source_maps SET status=0 WHERE output_block_id=8206 AND id<>8306;

COMMIT;

SELECT b.block_code,b.target_name,b.render_type,m.source_definition_id,s.start_marker,s.end_marker,
       s.include_start,s.include_end
FROM rpt_output_block_defs b JOIN rpt_output_source_maps m ON m.output_block_id=b.id
JOIN rpt_section_defs s ON s.id=m.source_section_def_id
WHERE b.output_definition_id=2001 AND b.id IN (8205,8206) AND b.status=1 AND m.status=1
ORDER BY b.sort_order,m.sort_order;
