-- Chạy sau report-phase5-word-config.sql cho DB đã cấu hình Word 2001.
-- Triển khai cùng template mới: Tình hình chung lấy từ Excel, phần cáp vẫn lấy từ Word 1101.
START TRANSACTION;

UPDATE rpt_section_defs
SET section_name='WORD_STATION_CABLE',
    start_marker='Sự cố đứt cáp, cháy cáp trục, cáp nhánh', end_marker=NULL,
    extract_mode=0, include_start=1, include_end=0
WHERE id=8103 AND source_definition_id=1101;

INSERT INTO rpt_output_block_defs
(id,output_definition_id,block_code,block_name,target_name,render_type,sort_order,status,created_by) VALUES
(8213,2001,'STATION_OVERVIEW','Tình hình chung sự cố trạm','BM_STATION_OVERVIEW',1,13,1,'system'),
(8214,2001,'STATION_INCIDENT_CHART','Xu thế sự cố trạm','BM_STATION_INCIDENT_CHART',1,14,1,'system')
ON DUPLICATE KEY UPDATE block_code=VALUES(block_code),block_name=VALUES(block_name),target_name=VALUES(target_name),
render_type=VALUES(render_type),sort_order=VALUES(sort_order),status=VALUES(status);

INSERT INTO rpt_output_source_maps
(id,output_block_id,source_definition_id,source_role,source_sheet_name,source_section_def_id,sort_order,status,created_by) VALUES
(8315,8213,1102,1,'TH',NULL,1,1,'system'),
(8316,8214,1102,3,'Lũy kế trạm năm {yyyy}',NULL,1,1,'system')
ON DUPLICATE KEY UPDATE source_definition_id=VALUES(source_definition_id),source_role=VALUES(source_role),
source_sheet_name=VALUES(source_sheet_name),source_section_def_id=VALUES(source_section_def_id),
sort_order=VALUES(sort_order),status=VALUES(status);

COMMIT;

SELECT b.block_code,b.target_name,m.source_definition_id,m.source_role,m.source_sheet_name,s.start_marker
FROM rpt_output_block_defs b JOIN rpt_output_source_maps m ON m.output_block_id=b.id
LEFT JOIN rpt_section_defs s ON s.id=m.source_section_def_id
WHERE b.output_definition_id=2001 AND b.block_code IN ('STATION_INCIDENT','STATION_OVERVIEW','STATION_INCIDENT_CHART')
AND b.status=1 AND m.status=1 ORDER BY b.sort_order,m.sort_order;
