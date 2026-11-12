# Danh mục sheet phục vụ báo cáo tuần

Phạm vi: Word 2001, Phụ lục I Excel 2002 và Phụ lục II Excel 2003. Danh mục nguồn: `weekly-report-excel-requirements.json`.

## Quy tắc import

- Giữ kiểm tra file không rỗng, dung lượng, extension theo definition và mở được workbook. Với nội dung Excel, kiểm tra đủ sheet active trong `rpt_excel_sheet_defs`.
- Riêng `definitionId=1010`, kiểm tra ngày trên tất cả sheet active lấy từ `RptExcelSheetDef`, theo thứ tự `sort_order` và sau khi thay placeholder tên sheet. Cấu hình seed hiện là `Bất cập DV`; tên này không hardcode trong validator. Chưa có sheet active thì báo lỗi cấu hình. Mỗi sheet phải có header `Ngày phát hiện`. Với mọi dòng còn dữ liệu sau header, ô ngày bắt buộc đọc được bằng cùng hàm `WordProductIssueReader.readDate()`: parse chuỗi hiển thị theo `d/M/yyyy` hoặc `d/M/yy`, cho phép ngày/tháng 1–2 chữ số, năm 2 chữ số được hiểu là 2000–2099. Ngày không tồn tại như `31/02/2026` bị từ chối. Dòng hoàn toàn trống được bỏ qua; ô công thức đọc kết quả đã lưu và tôn trọng hệ ngày 1904 của workbook khi định dạng ngày số.
- Nếu có lỗi ngày của nguồn 1010, trả lại bản sao workbook có cột `Kết quả` ngay sau header cuối cùng của từng sheet cấu hình, tính cả vùng header gộp ô và bỏ qua ô trống chỉ có định dạng hoặc các dòng khác kéo dài hơn header. Header kế thừa font, cỡ chữ, căn lề, nền, viền và các thuộc tính style của header có nội dung gần nhất bên trái; font được sao chép riêng để đổi màu chữ sang đỏ mà không ảnh hưởng header nguồn. Nội dung cảnh báo kế thừa font, cỡ chữ và style của ô có dữ liệu gần nhất bên trái cùng dòng, giữ chữ đỏ và bật wrap text; không lấy font từ ô trống cuối dòng hoặc từ header. Style cảnh báo được dùng lại theo style nguồn để tránh tạo style cho từng ô; từng dòng ghi rõ `Ngày phát hiện không được để trống.` hoặc `Ngày phát hiện không đúng định dạng d/M/uuuu hoặc d/M/uu, hoặc ngày không hợp lệ.`. Dòng hợp lệ không ghi cảnh báo. Khi nộp lại file có cột cảnh báo đã tạo, đặt lại cột đó đúng cuối header và xóa cảnh báo cũ của các dòng đã sửa; giữ nguyên cột Kết quả nghiệp vụ đang có.
- API `POST /api/reports/inputs/{definitionId}/import` trả `ApiResponse<ReportImportResult>`. Thành công: HTTP 200, message `Import báo cáo thành công`, `data.errorFile=null`. Lỗi dữ liệu ngày 1010: HTTP 200, message `Import có lỗi, kiểm tra file`, `data.errorFile` là nội dung Excel mã hóa Base64 như API import sự kiện; không lưu file, thay đổi instance hoặc sinh báo cáo. Sai extension, file hỏng, thiếu sheet/header hoặc cấu hình không hợp lệ vẫn trả HTTP 400 qua exception handler.
- Các definition khác chỉ kiểm tra danh sách sheet, không đọc nội dung ô để validate; sheet trống hoặc thiếu/sai header vẫn được import. Không truy vấn cấu hình header; reader sinh báo cáo vẫn tìm các cột cần thiết khi đọc dữ liệu.
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
