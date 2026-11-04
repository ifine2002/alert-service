-- Chạy sau cấu hình Word 2001 và triển khai cùng template/handler TV360 mới.
-- Hai mapping cùng Excel 1112: sort_order 1=NDPA, 2=NNLOI. Workbook chỉ tải/mở một lần.
START TRANSACTION;

UPDATE rpt_output_block_defs
SET render_type=1,target_name='BM_TV360',status=1
WHERE id=8211 AND output_definition_id=2001 AND block_code='TV360';

UPDATE rpt_output_source_maps
SET source_definition_id=1112,source_role=1,source_sheet_name='NDPA',
    source_section_def_id=NULL,sort_order=1,status=1
WHERE id=8311 AND output_block_id=8211;

INSERT INTO rpt_output_source_maps
(id,output_block_id,source_definition_id,source_role,source_sheet_name,source_section_def_id,sort_order,status,created_by) VALUES
(8318,8211,1112,1,'NNLOI',NULL,2,1,'system')
ON DUPLICATE KEY UPDATE source_definition_id=VALUES(source_definition_id),source_role=VALUES(source_role),
source_sheet_name=VALUES(source_sheet_name),source_section_def_id=NULL,sort_order=VALUES(sort_order),status=VALUES(status);

COMMIT;

SELECT b.block_code,b.target_name,b.render_type,m.source_definition_id,m.source_role,m.source_sheet_name,m.sort_order
FROM rpt_output_block_defs b JOIN rpt_output_source_maps m ON m.output_block_id=b.id
WHERE b.output_definition_id=2001 AND b.block_code='TV360' AND b.status=1 AND m.status=1 ORDER BY m.sort_order;
