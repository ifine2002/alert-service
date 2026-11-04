-- Chạy sau cấu hình Word 2001 và triển khai cùng template/handler CLM mới.
-- Chuyển phần Phản ánh Chất lượng mạng từ Word 1105 sang Excel 1107.
START TRANSACTION;

UPDATE rpt_output_block_defs
SET render_type=1,target_name='BM_CLM',status=1
WHERE id=8209 AND output_definition_id=2001 AND block_code='CLM';

UPDATE rpt_output_source_maps
SET source_definition_id=1107,source_role=1,source_sheet_name='Tỷ lệ PA tuần',
    source_section_def_id=NULL,status=1
WHERE id=8309 AND output_block_id=8209;

COMMIT;

SELECT b.block_code,b.target_name,b.render_type,m.source_definition_id,m.source_role,m.source_sheet_name
FROM rpt_output_block_defs b JOIN rpt_output_source_maps m ON m.output_block_id=b.id
WHERE b.output_definition_id=2001 AND b.block_code='CLM' AND b.status=1 AND m.status=1;
