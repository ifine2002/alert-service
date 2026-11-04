-- Triển khai cùng handler mới. Không thêm source_key, không đổi schema/source_role.
-- Quy ước sort_order cho CUSTOM_HANDLER:
--   TV360: 1=tiếp nhận/top dịch vụ, 2=nguyên nhân lỗi.
--   *_INCIDENT_CHART: 1=năm báo cáo, 2=năm trước (chỉ đọc nếu khoảng 8 tuần vắt năm).
--   STATION_INCIDENT: 1=TH, 2=top tỉnh năm báo cáo, 3=top tỉnh năm trước (chỉ đọc nếu tuần vắt năm).
-- {yyyy}=năm ngày kết thúc tuần; {yyyy-1}=năm trước đó.
-- Các mapping năm trước vẫn trỏ tới file của tuần báo cáo, không tải file của tuần/năm trước.
START TRANSACTION;

UPDATE rpt_output_block_defs SET render_type=1
WHERE output_definition_id=2001 AND block_code IN
('TV360','SYSTEM_INCIDENT_CHART','STATION_INCIDENT_CHART','CABLE_INCIDENT_CHART','STATION_INCIDENT');

INSERT INTO rpt_output_source_maps
(id,output_block_id,source_definition_id,source_role,source_sheet_name,source_section_def_id,sort_order,status,created_by) VALUES
(8311,8211,1112,1,'NDPA',NULL,1,1,'system'),
(8318,8211,1112,1,'NNLOI',NULL,2,1,'system'),
(8302,8202,1102,3,'Lũy kế lỗi phát sinh năm {yyyy}',NULL,1,1,'system'),
(8319,8202,1102,3,'LPS năm {yyyy-1}',NULL,2,1,'system'),
(8316,8214,1102,3,'Lũy kế trạm năm {yyyy}',NULL,1,1,'system'),
(8320,8214,1102,3,'Tổng trạm {yyyy-1}',NULL,2,1,'system'),
(8317,8215,1102,3,'Lũy kế trạm năm {yyyy}',NULL,1,1,'system'),
(8321,8215,1102,3,'Tổng trạm {yyyy-1}',NULL,2,1,'system'),
(8304,8204,1102,1,'TH',NULL,1,1,'system'),
(8322,8204,1102,1,'Lũy kế trạm năm {yyyy}',NULL,2,1,'system'),
(8323,8204,1102,1,'Tổng trạm {yyyy-1}',NULL,3,1,'system')
ON DUPLICATE KEY UPDATE source_definition_id=VALUES(source_definition_id),source_role=VALUES(source_role),
source_sheet_name=VALUES(source_sheet_name),source_section_def_id=NULL,sort_order=VALUES(sort_order),status=VALUES(status);

COMMIT;

SELECT b.block_code,m.sort_order,m.source_definition_id,m.source_role,m.source_sheet_name
FROM rpt_output_block_defs b JOIN rpt_output_source_maps m ON m.output_block_id=b.id
WHERE b.output_definition_id=2001 AND b.status=1 AND m.status=1 AND b.block_code IN
('TV360','SYSTEM_INCIDENT_CHART','STATION_INCIDENT_CHART','CABLE_INCIDENT_CHART','STATION_INCIDENT')
ORDER BY b.sort_order,m.sort_order;
