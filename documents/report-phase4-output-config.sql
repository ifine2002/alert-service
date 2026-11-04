-- Phase 4: Output configuration. Phase 1 phải tồn tại trước.
START TRANSACTION;

UPDATE rpt_definitions SET name=N'Báo Cáo Excel Bất Cập Về Sản Phẩm/Dịch Vụ Trong Tuần', file_kind=2, allowed_extension='xlsx' WHERE id=1122;

-- extract_mode: 0=TEXT_MARKER; marker ổn định, không chứa ngày/tuần/năm động.
INSERT INTO rpt_section_defs (id,source_definition_id,section_name,extract_mode,start_marker,end_marker,start_bookmark,end_bookmark,include_start,include_end,status,created_by) VALUES
(4001,1113,N'Thông tin thu thập về VTT',0,N'2.1  THÔNG TIN THU THẬP VỀ TỔNG CÔNG TY VIỄN THÔNG VIETTEL (VTT):',N'2.2  THÔNG TIN THU THẬP VỀ VIETTEL POST:',NULL,NULL,1,0,1,'system')
ON DUPLICATE KEY UPDATE source_definition_id=VALUES(source_definition_id),section_name=VALUES(section_name),extract_mode=VALUES(extract_mode),start_marker=VALUES(start_marker),end_marker=VALUES(end_marker),include_start=VALUES(include_start),include_end=VALUES(include_end),status=VALUES(status);

-- render_type: 0=COPY_SECTION, 1=CUSTOM_HANDLER.
INSERT INTO rpt_output_block_defs (id,output_definition_id,block_code,block_name,target_name,render_type,sort_order,status,created_by) VALUES
(5001,2001,'SOCIAL',N'Thông tin thu thập trên mạng xã hội','SOCIAL_BLOCK',0,1,1,'system'),
(5101,2002,'INCIDENT_WEEK',N'Lỗi phát sinh tuần','LPS tuần {dd-dd.MM}',1,1,1,'system'),
(5102,2002,'INCIDENT_YEAR',N'Lỗi phát sinh lũy kế năm','LPS năm {yyyy}',1,2,1,'system'),
(5103,2002,'STATION_WEEK',N'Lỗi trạm tuần','Lỗi trạm tuần {dd-dd.MM}',1,3,1,'system'),
(5104,2002,'CABLE_WEEK',N'Đứt cáp cháy cáp tuần','Đứt cáp cháy cáp tuần',1,4,1,'system'),
(5105,2002,'STATION_YEAR',N'Lỗi trạm lũy kế năm','Lỗi trạm năm {yyyy}',1,5,1,'system'),
(5201,2003,'PRODUCT_ISSUE_FILTER',N'Bất cập sản phẩm dịch vụ trong tuần','Bất cập DV',1,1,1,'system')
ON DUPLICATE KEY UPDATE output_definition_id=VALUES(output_definition_id),block_code=VALUES(block_code),block_name=VALUES(block_name),target_name=VALUES(target_name),render_type=VALUES(render_type),sort_order=VALUES(sort_order),status=VALUES(status);

-- source_role: 0=PRIMARY, 1=TEXT, 2=DATA, 3=CHART.
INSERT INTO rpt_output_source_maps (id,output_block_id,source_definition_id,source_role,source_sheet_name,source_section_def_id,sort_order,status,created_by) VALUES
(6001,5001,1113,1,NULL,4001,1,1,'system'),
(6101,5101,1102,2,N'LPS tuần',NULL,1,1,'system'),
(6102,5102,1102,2,N'Lũy kế lỗi phát sinh năm {yyyy}',NULL,1,1,'system'),
(6103,5103,1102,2,N'Trạm tuần',NULL,1,1,'system'),
(6104,5104,1102,2,N'Đứt cáp cháy cáp',NULL,1,1,'system'),
(6105,5105,1102,2,N'Lũy kế trạm năm {yyyy}',NULL,1,1,'system'),
(6201,5201,1010,2,N'Bất cập DV',NULL,1,1,'system')
ON DUPLICATE KEY UPDATE output_block_id=VALUES(output_block_id),source_definition_id=VALUES(source_definition_id),source_role=VALUES(source_role),source_sheet_name=VALUES(source_sheet_name),source_section_def_id=VALUES(source_section_def_id),sort_order=VALUES(sort_order),status=VALUES(status);
COMMIT;

-- Hậu kiểm số block/source active.
SELECT output_definition_id,COUNT(*) active_blocks FROM rpt_output_block_defs WHERE output_definition_id IN(2001,2002,2003) AND status=1 GROUP BY output_definition_id;
SELECT b.output_definition_id,m.source_definition_id,COUNT(*) source_maps FROM rpt_output_source_maps m JOIN rpt_output_block_defs b ON b.id=m.output_block_id WHERE b.output_definition_id IN(2001,2002,2003) AND b.status=1 AND m.status=1 GROUP BY b.output_definition_id,m.source_definition_id;
-- Hai query dưới phải trả về rỗng.
SELECT m.id,m.source_definition_id FROM rpt_output_source_maps m JOIN rpt_output_block_defs b ON b.id=m.output_block_id LEFT JOIN rpt_definitions d ON d.id=m.source_definition_id WHERE b.output_definition_id IN(2001,2002,2003) AND m.status=1 AND(d.id IS NULL OR d.direction<>0 OR d.node_type<>1 OR d.source_mode<>0 OR d.status<>1);
SELECT m.id,m.source_definition_id,s.source_definition_id section_source_definition_id FROM rpt_output_source_maps m JOIN rpt_section_defs s ON s.id=m.source_section_def_id WHERE m.source_section_def_id IS NOT NULL AND m.source_definition_id<>s.source_definition_id;
