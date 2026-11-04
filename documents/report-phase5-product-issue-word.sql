-- Cập nhật riêng phần Góp ý/Bất cập của Word 2001, không chạy lại toàn bộ phase5.
-- Đếm theo cột Ngày phát hiện, không theo nhãn Lũy kế tuần.
START TRANSACTION;
UPDATE rpt_output_block_defs
SET render_type=1, target_name='BM_PRODUCT_ISSUE'
WHERE output_definition_id=2001 AND block_code='PRODUCT_ISSUE';
UPDATE rpt_output_source_maps m
JOIN rpt_output_block_defs b ON b.id=m.output_block_id
SET m.source_role=1, m.source_sheet_name='Bất cập DV', m.source_section_def_id=NULL
WHERE b.output_definition_id=2001 AND b.block_code='PRODUCT_ISSUE'
  AND m.source_definition_id=1010;
COMMIT;
