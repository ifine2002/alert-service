-- Chạy sau cấu hình Word 2001 và triển khai cùng template/code mới.
-- Nội dung cáp lấy bảng so sánh TH; top tỉnh và biểu đồ dùng lũy kế cùng file Excel 1102.
START TRANSACTION;

UPDATE rpt_output_block_defs
SET block_name='Sự cố đứt/cháy cáp',target_name='BM_STATION_INCIDENT',render_type=1,status=1
WHERE id=8204 AND output_definition_id=2001 AND block_code='STATION_INCIDENT';

UPDATE rpt_output_source_maps
SET source_definition_id=1102,source_role=1,source_sheet_name='TH',source_section_def_id=NULL,status=1
WHERE id=8304 AND output_block_id=8204;

INSERT INTO rpt_output_block_defs
(id,output_definition_id,block_code,block_name,target_name,render_type,sort_order,status,created_by) VALUES
(8215,2001,'CABLE_INCIDENT_CHART','Xu thế sự cố đứt/cháy cáp','BM_CABLE_INCIDENT_CHART',1,15,1,'system')
ON DUPLICATE KEY UPDATE block_code=VALUES(block_code),block_name=VALUES(block_name),target_name=VALUES(target_name),
render_type=VALUES(render_type),sort_order=VALUES(sort_order),status=VALUES(status);

INSERT INTO rpt_output_source_maps
(id,output_block_id,source_definition_id,source_role,source_sheet_name,source_section_def_id,sort_order,status,created_by) VALUES
(8317,8215,1102,3,'Lũy kế trạm năm {yyyy}',NULL,1,1,'system'),
(8321,8215,1102,3,'Tổng trạm {yyyy-1}',NULL,2,1,'system'),
(8322,8204,1102,1,'Lũy kế trạm năm {yyyy}',NULL,2,1,'system'),
(8323,8204,1102,1,'Tổng trạm {yyyy-1}',NULL,3,1,'system')
ON DUPLICATE KEY UPDATE source_definition_id=VALUES(source_definition_id),source_role=VALUES(source_role),
source_sheet_name=VALUES(source_sheet_name),source_section_def_id=VALUES(source_section_def_id),
sort_order=VALUES(sort_order),status=VALUES(status);

COMMIT;

SELECT b.block_code,b.target_name,b.render_type,m.source_definition_id,m.source_role,m.source_sheet_name
FROM rpt_output_block_defs b JOIN rpt_output_source_maps m ON m.output_block_id=b.id
WHERE b.output_definition_id=2001 AND b.block_code IN ('STATION_INCIDENT','CABLE_INCIDENT_CHART')
AND b.status=1 AND m.status=1 ORDER BY b.sort_order,m.sort_order;
