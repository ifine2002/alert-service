-- Chuyển riêng SYSTEM_INCIDENT sang Excel 1102, sheet TH.
-- Chạy sau report-phase5-word-config.sql cho DB đã có cấu hình Word.
START TRANSACTION;

UPDATE rpt_output_block_defs
SET render_type=1
WHERE id=8201 AND output_definition_id=2001 AND block_code='SYSTEM_INCIDENT';

UPDATE rpt_output_source_maps
SET source_definition_id=1102, source_role=1, source_sheet_name='TH', source_section_def_id=NULL
WHERE id=8301 AND output_block_id=8201;

COMMIT;

SELECT b.block_code,b.render_type,m.source_definition_id,m.source_role,
       m.source_sheet_name,m.source_section_def_id
FROM rpt_output_block_defs b
JOIN rpt_output_source_maps m ON m.output_block_id=b.id
WHERE b.id=8201 AND m.id=8301;
