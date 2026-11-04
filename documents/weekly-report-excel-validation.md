# Danh mục sheet phục vụ báo cáo tuần

Phạm vi: Word 2001, Phụ lục I Excel 2002 và Phụ lục II Excel 2003. Danh mục nguồn: `weekly-report-excel-requirements.json`.

## Quy tắc import

- Giữ kiểm tra file không rỗng, dung lượng, extension theo definition và mở được workbook. Với nội dung Excel, chỉ cần tồn tại đủ sheet active trong `rpt_excel_sheet_defs`; sheet trống hoặc thiếu/sai header vẫn được import.
- Không truy vấn cấu hình header và không đọc nội dung ô để validate. Reader sinh báo cáo vẫn tìm các cột cần thiết khi đọc dữ liệu.
- `{yyyy}` là năm của ngày kết thúc tuần; `{yyyy-1}` là năm trước đó. Tuần 28/12/2026–03/01/2027 dùng năm 2027/2026. Quy tắc dùng chung cho import và generator.
- Giữ nguyên tên sheet trong file; so khớp với tên cấu hình sau khi thay placeholder. Cho phép có thêm sheet ngoài danh mục.
- Hai sheet năm trước được reader đọc khi cửa sổ 8 tuần hoặc tuần báo cáo đi qua năm trước. Vì DB chỉ có status, bộ seed mặc định yêu cầu cả hai sheet quanh năm để đủ nguồn.
- Mẫu di động là `.xlsm`; `allowed_extension` cần phù hợp định dạng file thực tế.

## Sheet bắt buộc theo definition

| Definition | Sheet | Output | Reader |
|---|---|---|---|
| 1010 | `Bất cập DV` | 2001, 2003 | WordProductIssueReader, AppendixExcelOutputGenerator.copyFilteredAppendixTwo |
| 1102 | `TH` | 2001 | WordSystemIncidentReader.read, WordSystemIncidentReader.readStation, WordSystemIncidentReader.readCable |
| 1102 | `Lũy kế lỗi phát sinh năm {yyyy}` | 2001, 2002 | WordIncidentChartReader.read, AppendixExcelOutputGenerator.copyFilteredByOwner |
| 1102 | `LPS năm {yyyy-1}` | 2001 | WordIncidentChartReader.read |
| 1102 | `Lũy kế trạm năm {yyyy}` | 2001, 2002 | WordIncidentChartReader.readStation, WordIncidentChartReader.readCable, WordIncidentChartReader.readTopCableProvinces, AppendixExcelOutputGenerator.copyAllDataRows |
| 1102 | `Tổng trạm {yyyy-1}` | 2001 | WordIncidentChartReader.readStation, WordIncidentChartReader.readCable, WordIncidentChartReader.readTopCableProvinces |
| 1102 | `LPS tuần` | 2002 | AppendixExcelOutputGenerator.copyFilteredByOwner |
| 1102 | `Trạm tuần` | 2002 | AppendixExcelOutputGenerator.copyAllDataRows |
| 1102 | `Đứt cáp cháy cáp` | 2002 | AppendixExcelOutputGenerator.copyAllDataRows |
| 1104 | `PL02-So lieu TN&XL` | 2001 | WordMobileFeedbackReader |
| 1106 | `N.Xet tuan` | 2001 | WordFixedFeedbackReader |
| 1106 | `TH` | 2001 | WordFixedFeedbackReader.kpi |
| 1107 | `Tỷ lệ PA tuần` | 2001 | WordNetworkQualityReader |
| 1112 | `NDPA` | 2001 | WordTv360FeedbackReader |
| 1112 | `NNLOI` | 2001 | WordTv360FeedbackReader |

## Cập nhật DB

1. Triển khai code bỏ validate header; không tạo bảng cấu hình header khi cài mới.
2. Với DB hiện tại, chạy `report-excel-header-removal.sql` để bỏ bảng cấu hình header cũ.
3. Chạy `report-weekly-excel-validation-config.sql` sau các script phase để đồng bộ danh mục sheet. Sheet cũ ngoài danh mục của 6 definition được chuyển status=0; không xóa bản ghi sheet.

SQL đồng thời giữ mapping nguồn lũy kế theo `{yyyy}` và đồng bộ sheet Phụ lục II thành `Bất cập DV`. Các script này không tự chạy trên DB.
