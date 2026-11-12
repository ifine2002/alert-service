param([string]$ProjectRoot = (Get-Location).Path)
$ErrorActionPreference = 'Stop'
$utf8 = [Text.UTF8Encoding]::new($false)
$manifest = Get-Content -LiteralPath (Join-Path $ProjectRoot 'documents/weekly-report-excel-requirements.json') -Raw -Encoding UTF8 | ConvertFrom-Json
function Sql-Text([string]$value) { return "N'" + $value.Replace("'", "''") + "'" }
$definitionIds = @($manifest.sheets.definitionId | Sort-Object -Unique) -join ','
$sql = [Collections.Generic.List[string]]::new()
$sql.Add('-- Sinh từ weekly-report-excel-requirements.json; chỉ cấu hình sheet bắt buộc.')
$sql.Add('SET NAMES utf8mb4;')
$sql.Add('START TRANSACTION;')
$sql.Add('CREATE TEMPORARY TABLE weekly_required_sheets (definition_id BIGINT, sheet_name VARCHAR(255), sort_order INT);')
$sql.Add('INSERT INTO weekly_required_sheets (definition_id,sheet_name,sort_order) VALUES')
$rows = @(); $orderByDefinition = @{}
foreach ($sheet in $manifest.sheets) {
    $key = [string]$sheet.definitionId
    $orderByDefinition[$key] = 1 + [int]$orderByDefinition[$key]
    $rows += '(' + $sheet.definitionId + ',' + (Sql-Text $sheet.sheetName) + ',' + $orderByDefinition[$key] + ')'
}
$sql.Add(($rows -join ",`n") + ';')
$sql.Add(@"
UPDATE rpt_excel_sheet_defs s
LEFT JOIN weekly_required_sheets r ON r.definition_id=s.definition_id AND BINARY r.sheet_name=BINARY s.sheet_name
SET s.status=0
WHERE s.definition_id IN ($definitionIds) AND r.definition_id IS NULL;

INSERT INTO rpt_excel_sheet_defs (definition_id,sheet_name,sort_order,status,created_by)
SELECT r.definition_id,r.sheet_name,r.sort_order,1,'system'
FROM weekly_required_sheets r
WHERE NOT EXISTS (SELECT 1 FROM rpt_excel_sheet_defs s WHERE s.definition_id=r.definition_id AND BINARY s.sheet_name=BINARY r.sheet_name)
ORDER BY r.definition_id,r.sort_order;

UPDATE rpt_excel_sheet_defs s
JOIN weekly_required_sheets r ON r.definition_id=s.definition_id AND BINARY r.sheet_name=BINARY s.sheet_name
SET s.status=1,s.sort_order=r.sort_order;

-- Dùng năm kết thúc tuần cho nguồn lũy kế Phụ lục I.
UPDATE rpt_output_source_maps m JOIN rpt_output_block_defs b ON b.id=m.output_block_id
SET m.source_sheet_name='Lũy kế lỗi phát sinh năm {yyyy}'
WHERE b.output_definition_id=2002 AND b.block_code='INCIDENT_YEAR' AND m.source_definition_id=1102;
UPDATE rpt_output_source_maps m JOIN rpt_output_block_defs b ON b.id=m.output_block_id
SET m.source_sheet_name='Lũy kế trạm năm {yyyy}'
WHERE b.output_definition_id=2002 AND b.block_code='STATION_YEAR' AND m.source_definition_id=1102;

-- Đồng bộ tên sheet với dữ liệu 1010 và template Phụ lục II.
UPDATE rpt_output_source_maps m JOIN rpt_output_block_defs b ON b.id=m.output_block_id
SET m.source_sheet_name='Bất cập DV'
WHERE b.output_definition_id=2003 AND b.block_code='PRODUCT_ISSUE_FILTER' AND m.source_definition_id=1010;
UPDATE rpt_output_block_defs SET target_name='Bất cập DV'
WHERE output_definition_id=2003 AND block_code='PRODUCT_ISSUE_FILTER';

COMMIT;
DROP TEMPORARY TABLE weekly_required_sheets;

SELECT definition_id,sheet_name
FROM rpt_excel_sheet_defs
WHERE status=1 AND definition_id IN ($definitionIds)
ORDER BY definition_id,sort_order,id;
"@)
[IO.File]::WriteAllText((Join-Path $ProjectRoot 'documents/report-weekly-excel-validation-config.sql'), ($sql -join "`n") + "`n", $utf8)

$md = [Collections.Generic.List[string]]::new()
$md.Add('# Danh mục sheet phục vụ báo cáo tuần')
$md.Add('')
$md.Add('Phạm vi: Word 2001, Phụ lục I Excel 2002 và Phụ lục II Excel 2003. Danh mục nguồn: `weekly-report-excel-requirements.json`.')
$md.Add('')
$md.Add('## Quy tắc import')
$md.Add('')
$md.Add('- Giữ kiểm tra file không rỗng, dung lượng, extension theo definition và mở được workbook. Với nội dung Excel, kiểm tra đủ sheet active trong `rpt_excel_sheet_defs`.')
$md.Add('- Riêng `definitionId=1010`, kiểm tra ngày trên tất cả sheet active lấy từ `RptExcelSheetDef`, theo thứ tự `sort_order` và sau khi thay placeholder tên sheet. Cấu hình seed hiện là `Bất cập DV`; tên này không hardcode trong validator. Chưa có sheet active thì báo lỗi cấu hình. Mỗi sheet phải có header `Ngày phát hiện`. Với mọi dòng còn dữ liệu sau header, ô ngày bắt buộc đọc được bằng cùng hàm `WordProductIssueReader.readDate()`: parse chuỗi hiển thị theo `d/M/yyyy` hoặc `d/M/yy`, cho phép ngày/tháng 1–2 chữ số, năm 2 chữ số được hiểu là 2000–2099. Ngày không tồn tại như `31/02/2026` bị từ chối. Dòng hoàn toàn trống được bỏ qua; ô công thức đọc kết quả đã lưu và tôn trọng hệ ngày 1904 của workbook khi định dạng ngày số.')
$md.Add('- Nếu có lỗi ngày của nguồn 1010, trả lại bản sao workbook có cột `Kết quả` ngay sau header cuối cùng của từng sheet cấu hình, tính cả vùng header gộp ô và bỏ qua ô trống chỉ có định dạng hoặc các dòng khác kéo dài hơn header. Header kế thừa font, cỡ chữ, căn lề, nền, viền và các thuộc tính style của header có nội dung gần nhất bên trái; font được sao chép riêng để đổi màu chữ sang đỏ mà không ảnh hưởng header nguồn. Nội dung cảnh báo kế thừa font, cỡ chữ và style của ô có dữ liệu gần nhất bên trái cùng dòng, giữ chữ đỏ và bật wrap text; không lấy font từ ô trống cuối dòng hoặc từ header. Style cảnh báo được dùng lại theo style nguồn để tránh tạo style cho từng ô; từng dòng ghi rõ `Ngày phát hiện không được để trống.` hoặc `Ngày phát hiện không đúng định dạng d/M/uuuu hoặc d/M/uu, hoặc ngày không hợp lệ.`. Dòng hợp lệ không ghi cảnh báo. Khi nộp lại file có cột cảnh báo đã tạo, đặt lại cột đó đúng cuối header và xóa cảnh báo cũ của các dòng đã sửa; giữ nguyên cột Kết quả nghiệp vụ đang có.')
$md.Add('- API `POST /api/reports/inputs/{definitionId}/import` trả `ApiResponse<ReportImportResult>`. Thành công: HTTP 200, message `Import báo cáo thành công`, `data.errorFile=null`. Lỗi dữ liệu ngày 1010: HTTP 200, message `Import có lỗi, kiểm tra file`, `data.errorFile` là nội dung Excel mã hóa Base64 như API import sự kiện; không lưu file, thay đổi instance hoặc sinh báo cáo. Sai extension, file hỏng, thiếu sheet/header hoặc cấu hình không hợp lệ vẫn trả HTTP 400 qua exception handler.')
$md.Add('- Các definition khác chỉ kiểm tra danh sách sheet, không đọc nội dung ô để validate; sheet trống hoặc thiếu/sai header vẫn được import. Không truy vấn cấu hình header; reader sinh báo cáo vẫn tìm các cột cần thiết khi đọc dữ liệu.')
$md.Add('- `{yyyy}` là năm của ngày kết thúc tuần; `{yyyy-1}` là năm trước đó. Tuần 28/12/2026–03/01/2027 dùng năm 2027/2026. Quy tắc dùng chung cho import và generator.')
$md.Add('- Giữ nguyên tên sheet trong file; so khớp với tên cấu hình sau khi thay placeholder. Cho phép có thêm sheet ngoài danh mục.')
$md.Add('- Hai sheet năm trước được reader đọc khi cửa sổ 8 tuần hoặc tuần báo cáo đi qua năm trước. Vì DB chỉ có status, bộ seed mặc định yêu cầu cả hai sheet quanh năm để đủ nguồn.')
$md.Add('- Mẫu di động là `.xlsm`; `allowed_extension` cần phù hợp định dạng file thực tế.')
$md.Add('')
$md.Add('## Sheet bắt buộc theo definition')
$md.Add('')
$md.Add('| Definition | Sheet | Output | Reader |')
$md.Add('|---|---|---|---|')
foreach ($sheet in $manifest.sheets) {
    $md.Add('| ' + $sheet.definitionId + ' | `' + $sheet.sheetName + '` | ' + ($sheet.outputs -join ', ') + ' | ' + ($sheet.readers -join ', ') + ' |')
}
$md.Add('')
$md.Add('## Cập nhật DB')
$md.Add('')
$md.Add('1. Triển khai code bỏ validate header; không tạo bảng cấu hình header khi cài mới.')
$md.Add('2. Với DB hiện tại, chạy `report-excel-header-removal.sql` để bỏ bảng cấu hình header cũ.')
$md.Add('3. Chạy `report-weekly-excel-validation-config.sql` sau các script phase để đồng bộ danh mục sheet. Sheet cũ ngoài danh mục của 6 definition được chuyển status=0; không xóa bản ghi sheet.')
$md.Add('')
$md.Add('SQL đồng thời giữ mapping nguồn lũy kế theo `{yyyy}` và đồng bộ sheet Phụ lục II thành `Bất cập DV`. Các script này không tự chạy trên DB.')
[IO.File]::WriteAllText((Join-Path $ProjectRoot 'documents/weekly-report-excel-validation.md'), ($md -join "`n") + "`n", $utf8)
Write-Output ('Generated SQL and Markdown for ' + $manifest.sheets.Count + ' sheets.')
