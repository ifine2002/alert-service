-- Bỏ bảng cấu hình header không còn được luồng import báo cáo tuần sử dụng.
-- Chạy trên DB hiện tại sau khi triển khai code chỉ validate sheet.
DROP TABLE IF EXISTS rpt_excel_header_defs;
