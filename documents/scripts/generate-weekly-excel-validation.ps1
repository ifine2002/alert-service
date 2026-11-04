param([string]$ProjectRoot = (Get-Location).Path)
$ErrorActionPreference = 'Stop'
$utf8 = [Text.UTF8Encoding]::new($false)
$manifest = Get-Content -LiteralPath (Join-Path $ProjectRoot 'documents/weekly-report-excel-requirements.json') -Raw -Encoding UTF8 | ConvertFrom-Json
function Sql-Text([string]$value) { return "N'" + $value.Replace("'", "''") + "'" }
$definitionIds = @($manifest.sheets.definitionId | Sort-Object -Unique) -join ','
$sql = [Collections.Generic.List[string]]::new()
$sql.Add('-- Sinh từ weekly-report-excel-requirements.json bằng documents/scripts/generate-weekly-excel-validation.ps1.')
$sql.Add('-- Chạy sau report-excel-header-name-migration.sql trên DB đã có dữ liệu.')
$sql.Add('-- Thay thế bộ validate của 6 definition dưới đây; metadata cũ ngoài danh mục chuyển status=0, không xóa bản ghi.')
$sql.Add('SET NAMES utf8mb4;')
$sql.Add('START TRANSACTION;')
$sql.Add('CREATE TEMPORARY TABLE weekly_required_sheets (definition_id BIGINT, sheet_name VARCHAR(255), sort_order INT);')
$sql.Add('CREATE TEMPORARY TABLE weekly_required_headers (definition_id BIGINT, sheet_name VARCHAR(255), header_name VARCHAR(500));')
$sql.Add('INSERT INTO weekly_required_sheets (definition_id,sheet_name,sort_order) VALUES')
$rows = @(); $orderByDefinition = @{}
foreach ($sheet in $manifest.sheets) {
    $key = [string]$sheet.definitionId
    $orderByDefinition[$key] = 1 + [int]$orderByDefinition[$key]
    $rows += '(' + $sheet.definitionId + ',' + (Sql-Text $sheet.sheetName) + ',' + $orderByDefinition[$key] + ')'
}
$sql.Add(($rows -join ",`n") + ';')
$sql.Add('INSERT INTO weekly_required_headers (definition_id,sheet_name,header_name) VALUES')
$rows = @()
foreach ($sheet in $manifest.sheets) {
    foreach ($header in $sheet.headers) { $rows += '(' + $sheet.definitionId + ',' + (Sql-Text $sheet.sheetName) + ',' + (Sql-Text $header) + ')' }
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

UPDATE rpt_excel_header_defs h
JOIN rpt_excel_sheet_defs s ON s.id=h.sheet_def_id
LEFT JOIN weekly_required_headers r ON r.definition_id=s.definition_id AND BINARY r.sheet_name=BINARY s.sheet_name AND BINARY r.header_name=BINARY h.header_name
SET h.status=0
WHERE s.definition_id IN ($definitionIds) AND r.definition_id IS NULL;

INSERT INTO rpt_excel_header_defs (sheet_def_id,header_name,status,created_by)
SELECT s.id,r.header_name,1,'system'
FROM weekly_required_headers r
JOIN rpt_excel_sheet_defs s ON s.definition_id=r.definition_id AND BINARY s.sheet_name=BINARY r.sheet_name
WHERE NOT EXISTS (SELECT 1 FROM rpt_excel_header_defs h WHERE h.sheet_def_id=s.id AND BINARY h.header_name=BINARY r.header_name);

UPDATE rpt_excel_header_defs h
JOIN rpt_excel_sheet_defs s ON s.id=h.sheet_def_id
JOIN weekly_required_headers r ON r.definition_id=s.definition_id AND BINARY r.sheet_name=BINARY s.sheet_name AND BINARY r.header_name=BINARY h.header_name
SET h.status=1;

-- Bỏ năm literal của hai nguồn Phụ lục I để cùng quy tắc năm kết thúc tuần.
UPDATE rpt_output_source_maps m JOIN rpt_output_block_defs b ON b.id=m.output_block_id
SET m.source_sheet_name='Lũy kế lỗi phát sinh năm {yyyy}'
WHERE b.output_definition_id=2002 AND b.block_code='INCIDENT_YEAR' AND m.source_definition_id=1102;
UPDATE rpt_output_source_maps m JOIN rpt_output_block_defs b ON b.id=m.output_block_id
SET m.source_sheet_name='Lũy kế trạm năm {yyyy}'
WHERE b.output_definition_id=2002 AND b.block_code='STATION_YEAR' AND m.source_definition_id=1102;

-- Mẫu đầu vào 1010 và template Phụ lục II đều dùng Bất cập DV.
UPDATE rpt_output_source_maps m JOIN rpt_output_block_defs b ON b.id=m.output_block_id
SET m.source_sheet_name='Bất cập DV'
WHERE b.output_definition_id=2003 AND b.block_code='PRODUCT_ISSUE_FILTER' AND m.source_definition_id=1010;
UPDATE rpt_output_block_defs SET target_name='Bất cập DV'
WHERE output_definition_id=2003 AND block_code='PRODUCT_ISSUE_FILTER';

COMMIT;
DROP TEMPORARY TABLE weekly_required_headers;
DROP TEMPORARY TABLE weekly_required_sheets;

SELECT s.definition_id,s.sheet_name,h.header_name
FROM rpt_excel_sheet_defs s LEFT JOIN rpt_excel_header_defs h ON h.sheet_def_id=s.id AND h.status=1
WHERE s.status=1 AND s.definition_id IN ($definitionIds)
ORDER BY s.definition_id,s.sort_order,h.id;
"@)
[IO.File]::WriteAllText((Join-Path $ProjectRoot 'documents/report-weekly-excel-validation-config.sql'), ($sql -join "`n") + "`n", $utf8)

Add-Type -AssemblyName System.IO.Compression
function Template-Headers([string]$relativePath) {
    $stream = [IO.File]::Open((Join-Path $ProjectRoot $relativePath), [IO.FileMode]::Open, [IO.FileAccess]::Read, [IO.FileShare]::ReadWrite)
    $zip = [IO.Compression.ZipArchive]::new($stream, [IO.Compression.ZipArchiveMode]::Read)
    function Read-Part([string]$name) {
        $reader = [IO.StreamReader]::new($zip.GetEntry($name).Open())
        try { return [xml]$reader.ReadToEnd() } finally { $reader.Dispose() }
    }
    try {
        $workbook = Read-Part 'xl/workbook.xml'; $relations = Read-Part 'xl/_rels/workbook.xml.rels'
        $strings = @((Read-Part 'xl/sharedStrings.xml').sst.si | ForEach-Object { $_.InnerText })
        foreach ($tab in $workbook.workbook.sheets.sheet) {
            $id = $tab.GetAttribute('id', 'http://schemas.openxmlformats.org/officeDocument/2006/relationships')
            $target = ($relations.Relationships.Relationship | Where-Object Id -eq $id).Target
            $part = if ($target.StartsWith('/')) { $target.TrimStart('/') } else { 'xl/' + $target }
            $sheet = Read-Part $part
            $first = -1
            foreach ($row in $sheet.worksheet.sheetData.row) {
                foreach ($cell in $row.c) { if ($cell.t -eq 's' -and $strings[[int]$cell.v].Trim() -eq 'STT') { $first = [int]$row.r; break } }
                if ($first -ge 0) { break }
            }
            if ($first -lt 0) { throw "Template thiếu header STT: $($tab.name)" }
            $last = $first
            foreach ($merge in $sheet.worksheet.mergeCells.mergeCell) {
                $bounds = $merge.ref -split ':'
                $from = [int]($bounds[0] -replace '[A-Z]', ''); $to = [int]($bounds[-1] -replace '[A-Z]', '')
                if ($from -le $first -and $to -ge $first) { $last = [Math]::Max($last, $to) }
            }
            $names = @()
            foreach ($row in $sheet.worksheet.sheetData.row | Where-Object { [int]$_.r -ge $first -and [int]$_.r -le $last }) {
                foreach ($cell in $row.c) {
                    if ($cell.t -eq 's') { $value = $strings[[int]$cell.v] } elseif ($cell.t -eq 'inlineStr') { $value = $cell.is.InnerText } else { continue }
                    $value = ($value -replace '[\s\p{Zs}]+', ' ').Trim()
                    if ($value -and $value -notin $names) { $names += $value }
                }
            }
            [PSCustomObject]@{ Name = $tab.name; Headers = $names }
        }
    } finally { $zip.Dispose(); $stream.Dispose() }
}

$md = [Collections.Generic.List[string]]::new()
$md.Add('# Danh mục sheet/header phục vụ báo cáo tuần')
$md.Add('')
$md.Add('Phạm vi: Word 2001, Phụ lục I Excel 2002 và Phụ lục II Excel 2003. Nguồn chuẩn là `weekly-report-excel-requirements.json`; SQL và tài liệu này sinh từ manifest bằng script trong `documents/scripts`.')
$md.Add('')
$md.Add('## Quy tắc import và triển khai')
$md.Add('')
$md.Add('- `{yyyy}` là năm của ngày kết thúc tuần; `{yyyy-1}` là năm trước đó. Tuần 28/12/2026–03/01/2027 dùng năm 2027 và 2026. Quy tắc dùng chung cho import, Word và hai phụ lục.')
$md.Add('- Chạy `report-excel-header-name-migration.sql` một lần để bỏ `row_index`, `column_index`, sau đó `report-weekly-excel-validation-config.sql`. Chưa có thao tác nào tự chạy trên DB.')
$md.Add('- Script metadata thay thế bộ sheet/header active trong các definition 1010, 1102, 1104, 1106, 1107, 1112. Bản ghi cũ ngoài danh mục chuyển status=0; không xóa bản ghi.')
$md.Add('- Mỗi tên header phải khớp toàn bộ nội dung một ô sau khi chuẩn hóa Unicode NFC, hoa/thường và khoảng trắng/xuống dòng/NBSP. Không dùng contains và không ghép nhiều ô để tạo một tên.')
$md.Add('- Quét toàn sheet, dừng khi đủ header. Các header được phép nằm khác dòng/cột hoặc trong ô gộp (giá trị nằm ở ô đầu). Tên lặp ở nhiều bảng/tuần chỉ cần có mặt; vị trí tương đối và kỳ báo cáo do reader kiểm tra tiếp.')
$md.Add('- Danh mục `headers` gồm tên cột, nhãn bảng và nhãn dòng mà reader cần. Các tên động có ngày và các lựa chọn tên thay thế nằm ở phần ghi chú, không seed thành nhiều yêu cầu bắt buộc cùng lúc.')
$md.Add('- Hai sheet năm trước được reader đọc có điều kiện khi cửa sổ 8 tuần hoặc tuần báo cáo đi qua năm trước. Vì DB chỉ có status, bộ seed mặc định yêu cầu cả hai sheet quanh năm để đủ nguồn.')
$md.Add('- Mẫu di động là `.xlsm`; validator đã hỗ trợ khi `allowed_extension=xlsm`. Nếu DB vẫn cho phép xlsx, cần cấu hình đúng extension hoặc lưu nguồn thành xlsx; script header không tự thay chính sách extension.')
$md.Add('')
$md.Add('## Sheet và tên bắt buộc để cấu hình DB')
foreach ($sheet in $manifest.sheets) {
    $md.Add(''); $md.Add('### Definition ' + $sheet.definitionId + ' — `' + $sheet.sheetName + '`'); $md.Add('')
    $md.Add('- Output: ' + ($sheet.outputs -join ', ') + '. Reader: `' + ($sheet.readers -join '`, `') + '`.')
    $md.Add('- File mẫu: `' + $sheet.sourceFilePrefix + '`.')
    $md.Add('- Tên bắt buộc: `' + ($sheet.headers -join '`, `') + '`.')
    $md.Add('- ' + $sheet.notes)
    if ($sheet.sampleMissingHeaders) { $md.Add('- **File mẫu hiện tại thiếu: `' + ($sheet.sampleMissingHeaders -join '`, `') + '`. Phải bổ sung header trong file để import đạt bộ cấu hình này.**') }
}
$md.Add(''); $md.Add('## Các header động/điều kiện reader còn kiểm tra'); $md.Add('')
$md.Add('| Reader | Header/điều kiện |'); $md.Add('|---|---|')
$md.Add('| WordSystemIncidentReader | Tuần này/Tuần trước hoặc khoảng ngày tuần hiện tại/trước; So sánh/Chênh lệch; bảng trạm/cáp có thể dùng hai cột Tỷ lệ tăng giảm. Phải đúng thứ tự/nhóm bảng. |')
$md.Add('| WordIncidentChartReader | Năm/Tháng/Ngày nằm cùng hàng ở bảng hệ thống; bảng trạm/cáp có thể chia hai tầng. Dữ liệu năm trước của bảng hệ thống có Tháng M.yyyy. Cột Số lượng phản ảnh dùng tính PA nếu có. |')
$md.Add('| WordMobileFeedbackReader | TB tuần (d/M-d/M) có thể có năm; cần đúng tuần báo cáo. STT/Dịch vụ có thể ở hàng phía trên. Dòng Phản ánh báo hỏng dịch vụ phải ngay dưới header kỳ báo cáo. |')
$md.Add('| WordNetworkQualityReader | Tuần Từ d.M đến d.M hoặc dấu /, dấu phẩy, có thể có năm; các cột PA/Thuê bao/KQ phải thuộc đúng nhóm tuần. Có hai bảng top tỉnh riêng. |')
$md.Add('| WordFixedFeedbackReader | Khoảng tuần dạng d/M-d/M hoặc d.M-d.M, tùy chọn năm và tiền tố Tuần/Tuần từ. Chấp nhận biến thể nhãn bỏ tiếp nhận, khoảng trắng, FTTH_Gpon/FTTH GPON và THS/Truyền hình số. DB hiện dùng đúng literal trong mẫu. |')
$md.Add('| WordTv360FeedbackReader | Nhóm d/M-d/M hoặc d/M-d/M/yyyy phải gộp cột, dưới nhóm có TB ngày/Tỷ trọng ngày. Hai sheet NDPA/NNLOI cần đúng nhóm tuần. |')
$md.Add(''); $md.Add('## Toàn bộ tên cột được hai phụ lục Excel ánh xạ'); $md.Add('')
$md.Add('Các tên dưới đây lấy trực tiếp từ hai template đang dùng. Chúng là cột chi tiết cần có nếu muốn điền đủ phụ lục; không phải tất cả đều đang bắt buộc trong reader. Reader cho phép cột nguồn không ánh xạ được để trống. STT được đánh lại, không bắt buộc có ở nguồn. Header nhiều tầng được ánh xạ theo chuỗi cha > con, sau đó thử tên con duy nhất. Validator tên chỉ kiểm tra sự hiện diện, không chứng minh quan hệ cha/con.')
foreach ($path in @('src/main/resources/templates/phu-luc-1-template.xlsx', 'src/main/resources/templates/phu-luc-2-template.xlsx')) {
    foreach ($sheet in Template-Headers $path) {
        $md.Add(''); $md.Add('### `' + $sheet.Name + '` — ' + [IO.Path]::GetFileName($path)); $md.Add('')
        $md.Add('`' + ($sheet.Headers -join '`, `') + '`.')
    }
}
$md.Add(''); $md.Add('## Các khác biệt đã xác nhận trong cấu hình/mẫu'); $md.Add('')
$md.Add('- Phụ lục II trước đây trỏ source/target Bất cập, trong khi dữ liệu 1010 và template output đều là Bất cập DV. SQL metadata đã đồng bộ hai mapping về Bất cập DV.')
$md.Add('- Hai mapping nguồn lũy kế của phụ lục I trước đây cố định năm 2026; SQL đã chuyển thành `{yyyy}` để có tác dụng khi qua năm.')
$md.Add('- Mẫu 1102 đang thiếu header Chủ dịch vụ ở sheet Lũy kế lỗi phát sinh năm 2026; reader biểu đồ và phụ lục I đều cần để lọc VTT. Manifest giữ yêu cầu này và fixture test xác nhận lỗi thiếu header, không bỏ qua yêu cầu.')
$md.Add('- WordIncidentChartReader đang tìm tên bổ sung Số lượng phản ảnh, trong khi mẫu dùng Số lượng phản ánh. Đây không phải một header bắt buộc trong reader, vì vậy không ép tên sai vào seed. Cần thống nhất tên nếu muốn biểu đồ lấy đủ tổng PA từ cột này.')
$md.Add('- Một số cột template phụ lục II là Vấn đề bất cập/Hiện trạng/Đơn vị phát hiện/Phân loại, còn nguồn 1010 là Tên bất cập/lỗi/Nội dung/BO/PAGY/PL ND khắc phục. Reader hiện không có bảng alias tên này; các cột khác tên có thể để trống. Danh mục giữ nguyên cả tên bắt buộc nguồn và tên output để người cấu hình đối chiếu.')
$md.Add('- Nguồn Word 1101, 1119, 1108 dùng section/bookmark và không có sheet/header Excel. Nguồn 1122 là cấu hình cũ, không được các mapping phase4/phase5 hiện tại dùng cho bộ ba output này.')
[IO.File]::WriteAllText((Join-Path $ProjectRoot 'documents/weekly-report-excel-validation.md'), ($md -join "`n") + "`n", $utf8)
Write-Output ('Generated SQL and Markdown for ' + $manifest.sheets.Count + ' sheets.')
