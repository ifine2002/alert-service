-- Cấu hình riêng Word 2001. Không chạy lại phase4 cũ sau script này.
-- Kiểm tra và backup DB trước khi chạy. Không thay đổi output Excel 2002/2003.
START TRANSACTION;
UPDATE rpt_definitions SET template_relative_path='templates/weekly-vcx-word-template.docx' WHERE id=2001;
UPDATE rpt_output_block_defs SET status=0 WHERE output_definition_id=2001;

-- extract_mode: 0=TEXT_RANGE, 2=PARAGRAPH_MARKER.
INSERT INTO rpt_section_defs
(id,source_definition_id,section_name,extract_mode,start_marker,end_marker,item_count,include_start,include_end,status,created_by) VALUES
(8102,1101,'WORD_INCIDENT',0,'Vấn đề/Sự cố nổi bật:','SỰ CỐ TRẠM CĐBR',NULL,0,0,1,'system'),
(8103,1101,'WORD_STATION_CABLE',0,'Sự cố đứt cáp, cháy cáp trục, cáp nhánh',NULL,NULL,1,0,1,'system'),
-- Lấy trọn phần I/II của Word 1119; bỏ tiêu đề nguồn vì mẫu đã có tiêu đề II/III.
(8104,1119,'WORD_PROGRAM',0,'CÁC CHƯƠNG TRÌNH/SỰ KIỆN TRỌNG ĐIỂM, NỔI BẬT','PHẢN ỨNG KH VỀ CÁC CHƯƠNG TRÌNH/CHÍNH SÁCH MỚI/ĐIỀU CHỈNH',NULL,0,0,1,'system'),
(8105,1119,'WORD_REACTION',0,'PHẢN ỨNG KH VỀ CÁC CHƯƠNG TRÌNH/CHÍNH SÁCH MỚI/ĐIỀU CHỈNH',NULL,NULL,0,0,1,'system'),
(8106,1103,'WORD_MOBILE',0,'Tình hình tiếp nhận chung:','KẾT QUẢ THỰC HIỆN KPI',NULL,0,0,1,'system'),
(8107,1105,'WORD_CLM',0,'Phản ánh chất lượng mạng:','Dịch vụ SME:',NULL,0,0,1,'system'),
(8108,1110,'WORD_FIXED',0,'2.2 Phản ánh báo lỗi dịch vụ CĐBR:','Dịch vụ TV360:',NULL,0,0,1,'system'),
(8109,1110,'WORD_TV360',0,'Dịch vụ TV360:','2.3 Tiến độ xử lý:',NULL,0,0,1,'system'),
(8110,1108,'WORD_SME',0,'Tình hình tiếp nhận và xử lý PAKH:','Xu thế PAKH theo tuần:',NULL,0,0,1,'system')
ON DUPLICATE KEY UPDATE source_definition_id=VALUES(source_definition_id),extract_mode=VALUES(extract_mode),
start_marker=VALUES(start_marker),end_marker=VALUES(end_marker),item_count=VALUES(item_count),
include_start=VALUES(include_start),include_end=VALUES(include_end),status=VALUES(status);

INSERT INTO rpt_output_block_defs
(id,output_definition_id,block_code,block_name,target_name,render_type,sort_order,status,created_by) VALUES
(8201,2001,'SYSTEM_INCIDENT','Sự cố hàng loạt','BM_SYSTEM_INCIDENT',1,1,1,'system'),
(8202,2001,'SYSTEM_INCIDENT_CHART','Xu thế sự cố','BM_SYSTEM_INCIDENT_CHART',1,2,1,'system'),
(8203,2001,'INCIDENT','Sự cố nổi bật','BM_INCIDENT',0,3,1,'system'),
(8204,2001,'STATION_INCIDENT','Sự cố đứt/cháy cáp','BM_STATION_INCIDENT',1,4,1,'system'),
(8205,2001,'PROGRAM_EVENT','Chương trình sự kiện','BM_PROGRAM_EVENT',0,5,1,'system'),
(8206,2001,'CUSTOMER_REACTION','Phản ứng chính sách','BM_CUSTOMER_REACTION',0,6,1,'system'),
(8207,2001,'PRODUCT_ISSUE','Bất cập','BM_PRODUCT_ISSUE',1,7,1,'system'),
(8208,2001,'MOBILE','Di động','BM_MOBILE',1,8,1,'system'),
(8209,2001,'CLM','Chất lượng mạng','BM_CLM',1,9,1,'system'),
(8210,2001,'FIXED','Cố định','BM_FIXED',1,10,1,'system'),
(8211,2001,'TV360','TV360','BM_TV360',1,11,1,'system'),
(8212,2001,'SME','SME','BM_SME',1,12,1,'system'),
(8213,2001,'STATION_OVERVIEW','Tình hình chung sự cố trạm','BM_STATION_OVERVIEW',1,13,1,'system'),
(8214,2001,'STATION_INCIDENT_CHART','Xu thế sự cố trạm','BM_STATION_INCIDENT_CHART',1,14,1,'system'),
(8215,2001,'CABLE_INCIDENT_CHART','Xu thế sự cố đứt/cháy cáp','BM_CABLE_INCIDENT_CHART',1,15,1,'system')
ON DUPLICATE KEY UPDATE block_name=VALUES(block_name),target_name=VALUES(target_name),
render_type=VALUES(render_type),sort_order=VALUES(sort_order),status=VALUES(status);

-- source_role theo enum hiện tại: TEXT=0, DATA=1, CHART=2, CHART_ACCUMULATED=3.
-- sort_order: TV360 1=NDPA, 2=NNLOI; biểu đồ 1=năm báo cáo, 2=năm trước.
-- STATION_INCIDENT: 1=TH, 2=năm báo cáo, 3=năm trước (top tỉnh).
-- FIXED (1106): 1=N.Xet tuan (toàn bộ số liệu tuần), 2=TH (ngưỡng KPI).
-- {yyyy} là năm của ngày kết thúc tuần báo cáo.
INSERT INTO rpt_output_source_maps
(id,output_block_id,source_definition_id,source_role,source_sheet_name,source_section_def_id,sort_order,status,created_by) VALUES
(8301,8201,1102,1,'TH',NULL,1,1,'system'),
(8302,8202,1102,3,'Lũy kế lỗi phát sinh năm {yyyy}',NULL,1,1,'system'),
(8303,8203,1101,0,NULL,8102,1,1,'system'),
(8304,8204,1102,1,'TH',NULL,1,1,'system'),
(8305,8205,1119,0,NULL,8104,1,1,'system'),
(8306,8206,1119,0,NULL,8105,1,1,'system'),
(8307,8207,1010,1,'Bất cập DV',NULL,1,1,'system'),
(8308,8208,1104,1,'PL02-So lieu TN&XL',NULL,1,1,'system'),
(8309,8209,1107,1,'Tỷ lệ PA tuần',NULL,1,1,'system'),
(8310,8210,1106,1,'N.Xet tuan',NULL,1,1,'system'),
(8311,8211,1112,1,'NDPA',NULL,1,1,'system'),
(8312,8212,1108,0,NULL,8110,1,1,'system'),
(8315,8213,1102,1,'TH',NULL,1,1,'system'),
(8316,8214,1102,3,'Lũy kế trạm năm {yyyy}',NULL,1,1,'system'),
(8317,8215,1102,3,'Lũy kế trạm năm {yyyy}',NULL,1,1,'system'),
(8318,8211,1112,1,'NNLOI',NULL,2,1,'system'),
(8319,8202,1102,3,'LPS năm {yyyy-1}',NULL,2,1,'system'),
(8320,8214,1102,3,'Tổng trạm {yyyy-1}',NULL,2,1,'system'),
(8321,8215,1102,3,'Tổng trạm {yyyy-1}',NULL,2,1,'system'),
(8322,8204,1102,1,'Lũy kế trạm năm {yyyy}',NULL,2,1,'system'),
(8323,8204,1102,1,'Tổng trạm {yyyy-1}',NULL,3,1,'system'),
(8313,8210,1106,1,'TH',NULL,2,1,'system')
ON DUPLICATE KEY UPDATE source_definition_id=VALUES(source_definition_id),source_role=VALUES(source_role),
source_sheet_name=VALUES(source_sheet_name),source_section_def_id=VALUES(source_section_def_id),sort_order=VALUES(sort_order),status=VALUES(status);
-- Bổ sung các section diễn giải của báo cáo chính, không tính lại từ Excel.
INSERT INTO rpt_section_defs
(id,source_definition_id,section_name,extract_mode,start_marker,end_marker,item_count,include_start,include_end,status,created_by) VALUES
(8111,1110,'WORD_FIXED_PROGRESS',0,'2.3 Tiến độ xử lý:','ĐỀ XUẤT /KIẾN NGHỊ:',NULL,1,0,1,'system'),
(8112,1108,'WORD_SME_KPI',0,'Kết quả thực hiện các KPI chính:','Bảng tiếp nhận theo dịch vụ:',NULL,1,0,1,'system')
ON DUPLICATE KEY UPDATE source_definition_id=VALUES(source_definition_id),extract_mode=VALUES(extract_mode),
start_marker=VALUES(start_marker),end_marker=VALUES(end_marker),include_start=VALUES(include_start),
include_end=VALUES(include_end),status=VALUES(status);

-- FIXED đọc trực tiếp Excel 1106; không còn mapping Word diễn giải/tiến độ 1110.
UPDATE rpt_output_source_maps SET status=0 WHERE output_block_id=8210 AND id NOT IN (8310,8313);
-- SME dùng marker/include_* từ section 8110; custom handler dừng trước ảnh và gộp thành một đoạn.
UPDATE rpt_output_source_maps SET status=0 WHERE output_block_id=8212 AND id<>8312;
COMMIT;

-- Word 1119: phần I dừng trước tiêu đề phần II; phần II lấy đến hết tài liệu.
-- Marker là tiêu đề phần, không phụ thuộc tên hoặc số lượng chương trình từng tuần.
SELECT b.block_code,b.target_name,m.source_definition_id,s.start_marker,s.end_marker
FROM rpt_output_block_defs b JOIN rpt_output_source_maps m ON m.output_block_id=b.id
LEFT JOIN rpt_section_defs s ON s.id=m.source_section_def_id
WHERE b.output_definition_id=2001 AND b.status=1 AND m.status=1 ORDER BY b.sort_order,m.sort_order;
