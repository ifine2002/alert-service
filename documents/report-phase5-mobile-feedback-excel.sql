-- Chạy sau cấu hình Word 2001, triển khai cùng template và handler MOBILE mới.
-- Bỏ copy section Word 1103; đọc bảng so sánh tuần trong Excel/XLSM 1104.
START TRANSACTION;

UPDATE rpt_output_block_defs
SET render_type=1,target_name='BM_MOBILE',status=1
WHERE id=8208 AND output_definition_id=2001 AND block_code='MOBILE';

UPDATE rpt_output_source_maps
SET source_definition_id=1104,source_role=1,source_sheet_name='PL02-So lieu TN&XL',
    source_section_def_id=NULL,status=1
WHERE id=8308 AND output_block_id=8208;

COMMIT;

SELECT b.block_code,b.target_name,b.render_type,m.source_definition_id,m.source_role,m.source_sheet_name
FROM rpt_output_block_defs b JOIN rpt_output_source_maps m ON m.output_block_id=b.id
WHERE b.output_definition_id=2001 AND b.block_code='MOBILE' AND b.status=1 AND m.status=1;
