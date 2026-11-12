# Nguồn dữ liệu và cách điền weekly-vcx-word-template.docx

Ngày đối chiếu: **10/11/2026**. Phạm vi: output **2001 – Báo cáo tuần VCX**, template [weekly-vcx-word-template.docx](../src/main/resources/templates/weekly-vcx-word-template.docx).

Tài liệu mô tả **code và cấu hình SQL đang có trong repository**, không phải kết quả truy vấn cấu hình database đang chạy. ID nguồn, tên sheet, marker và trạng thái block thực tế lấy từ database; các giá trị dưới đây là cấu hình được cung cấp bởi các script hiện tại. Tên file mẫu chỉ giúp tìm tài liệu tương ứng trong `documents`, không phải đường dẫn được hardcode để sinh báo cáo.

## 1. Quy ước và luồng lấy file

| Ký hiệu | Ý nghĩa |
|---|---|
| `W` | `snapshot.weekStartDate`, ngày bắt đầu tuần do tác vụ báo cáo truyền vào; code không tự chuyển về thứ Hai |
| `E` | `W + 6 ngày`, kết thúc tuần |
| `P` | `W - 7 ngày`, bắt đầu tuần trước; tuần trước kết thúc tại `W - 1 ngày` |
| `Y` | Năm của **E**, dùng thay `{yyyy}` trong tên sheet |
| `C`, `B`, `D` | Giá trị tuần hiện tại, tuần trước và chênh lệch `D = C - B` |
| `round(n)` | Làm tròn HALF_UP; số chữ số thập phân được ghi riêng ở từng block |

Luồng chung:

1. `WordGenerationSnapshotService.load()` đọc definition output, các block `status=1` theo `sort_order`, rồi đọc mapping nguồn đang hoạt động của từng block.
2. Với mỗi `source_definition_id` và tuần nguồn, tìm `RptInstance`. Chỉ lấy `currentFileId` khi instance có trạng thái `COMPLETED`; kiểm tra file thuộc đúng instance. Không đủ điều kiện thì giữ nguồn với `file=null`.
3. `WordSourceDocuments` tải file theo `RptFile.storageObjectName` qua `ReportFileService.downloadToTemporaryFile()`, mở Word bằng Aspose và Excel bằng Apache POI; cache theo ID file trong một lần sinh báo cáo.
4. `WeeklyWordOutputGenerator` gọi handler theo `block_code`, rồi thay text, chèn các node Word hoặc chèn ảnh PNG vào bookmark đích.

Các mapping hiện tại dùng `TEXT=0`, `DATA=1`, `CHART_ACCUMULATED=3`. Chúng đều lấy **file của tuần W**. Biểu đồ 8 tuần đọc dữ liệu lũy kế trong file đó; sheet năm trước cũng nằm trong file tuần W. Chỉ role `CHART=2` mới khiến snapshot lấy 8 file của 8 tuần, nhưng các biểu đồ đang cấu hình ở đây dùng role 3.

Tên sheet qua `ReportSheetNameResolver` có các placeholder: `{yyyy}=Y`, `{yyyy-1}=Y-1`, `{dd-dd.MM}=ngày bắt đầu-ngày kết thúc.tháng kết thúc`. Handler gọi trực tiếp `workbook.getSheet(sheetName)` như SYSTEM_INCIDENT/MOBILE/CLM/PRODUCT_ISSUE dùng nguyên tên sheet được truyền; các handler qua `documents.excelSheet()` dùng resolver.

Nguồn code: [WordGenerationSnapshotService.java](../src/main/java/com/techasians/alertservice/service/impl/WordGenerationSnapshotService.java), [WordSourceDocuments.java](../src/main/java/com/techasians/alertservice/service/impl/WordSourceDocuments.java), [ReportSheetNameResolver.java](../src/main/java/com/techasians/alertservice/service/impl/ReportSheetNameResolver.java).

## 2. Danh mục nguồn và file mẫu

| ID nguồn | Tên definition trong dữ liệu master | File mẫu tương ứng trong repository |
|---|---|---|
| 1101 | Báo Cáo Word Lỗi Phát Sinh Tuần | [T36. Bao cao tuan 01.09 -07.09.docx](<File 1/1. Báo cáo Lỗi phát sinh tuần_ thuynt14/T36. Bao cao tuan 01.09 -07.09.docx>) |
| 1102 | Báo Cáo Excel Lỗi Phát Sinh Tuần | [T36. Bao cao tuan 01.09 -07.09.xlsx](<File 1/1. Báo cáo Lỗi phát sinh tuần_ thuynt14/T36. Bao cao tuan 01.09 -07.09.xlsx>) |
| 1119 | Báo Cáo Word Chất Lượng Tổng Đài | [Nổi bật tuần 01.09 - 07.09.docx](<File 2/9. Báo cáo chất lượng tổng đài_anhlp13+ Thuynt14/Nổi bật tuần 01.09 - 07.09.docx>) |
| 1010 | Báo Cáo Bất Cập Về Sản Phẩm/Dịch Vụ Trong Tuần | [Phu luc - TH BCDV_PAGY_2026.xlsx](<File 2/10. Báo cáo bất cập về sản phẩm dịch vụ trong tuần_thanhnt1+phuongpm1+thuyntg+anhlp13+nhung7750/Phu luc - TH BCDV_PAGY_2026.xlsx>) |
| 1104 | Báo Cáo Excel Dịch Vụ Di Động tuần | [PHU_LUC_BAO_CAO_TUAN_DICH_VU_DI_DONG_07092026.xlsm](<File 1/2. Báo cáo PAKH dịch vụ Di động tuần_thanhnt1/PHU_LUC_BAO_CAO_TUAN_DICH_VU_DI_DONG_07092026.xlsm>) |
| 1107 | Báo Cáo Excel 2 Dịch Vụ Chất Lượng Mạng Tuần | [PL_CLM Toàn qu c tu n 01.09 - 07.09_gui.xlsx](<File 2/3+4. Báo cáo PAKH dịch vụ chất lượng mạng + SME tuần_thuyntg/PL_CLM Toàn qu c tu n 01.09 - 07.09_gui.xlsx>) |
| 1106 | Báo Cáo Excel 1 Dịch Vụ Chất Lượng Mạng Tuần | Fixture đang dùng cho FIXED: [PL CDBR TUAN 01.09-07.09.xlsx](<File 2/5. Báo cáo PAKH dịch vụ cố định tuần_phuongpm1/PL CDBR TUAN 01.09-07.09.xlsx>) |
| 1112 | Báo Cáo Excel 2 Dịch Vụ Cố Định Tuần | [PL TV360 TUAN 01.09-07.09.xlsx](<File 2/5. Báo cáo PAKH dịch vụ cố định tuần_phuongpm1/PL TV360 TUAN 01.09-07.09.xlsx>) |
| 1108 | Báo Cáo Word Dịch Vụ SME Tuần | [Bao cao CLDV thong qua PAKH tuan 01.09-07.09.docx](<File 2/3+4. Báo cáo PAKH dịch vụ chất lượng mạng + SME tuần_thuyntg/Bao cao CLDV thong qua PAKH tuan 01.09-07.09.docx>) |

Lưu ý về ID 1106: tên master là Excel 1 CLM, nhưng mapping FIXED và fixture hiện tại trỏ tới workbook CĐBR nêu trên. Khi vận hành phải kiểm tra **file upload dưới definition 1106**, không suy ra nguồn chỉ từ tên nghiệp vụ. Ngoài ra, nhiều workbook mẫu có sheet `Bất cập DV`; handler PRODUCT_ISSUE chỉ dùng file được mapping dưới nguồn 1010.

## 3. Bảng tổng hợp 15 block

Thứ tự dưới đây theo bố cục nội dung trong template; `sort_order` cấu hình có thể khác thứ tự hiển thị vì dữ liệu được điền theo bookmark.

| Block | Bookmark chính | Nguồn | Sheet hoặc section | Cách tạo nội dung |
|---|---|---|---|---|
| SYSTEM_INCIDENT | `BM_SYSTEM_INCIDENT` | Excel 1102 | `TH`, bảng `Tổng lỗi phát sinh` | Đọc hai tuần; tính chênh lệch SC, đọc tỷ lệ PA |
| SYSTEM_INCIDENT_CHART | `BM_SYSTEM_INCIDENT_CHART` | Excel 1102 | `Lũy kế lỗi phát sinh năm {yyyy}`; `LPS năm {yyyy-1}` | Lọc VTT, gom 8 tuần: số SC và tổng PA |
| INCIDENT | `BM_INCIDENT` | Word 1101 | Section 8102 | Copy phần sau `Vấn đề/Sự cố nổi bật:` đến trước `SỰ CỐ TRẠM CĐBR` |
| STATION_OVERVIEW | `BM_STATION_OVERVIEW` | Excel 1102 | `TH`, bảng `So sánh lỗi trạm` | Đọc SC, PA, TB ảnh hưởng và so sánh hai tuần |
| STATION_INCIDENT_CHART | `BM_STATION_INCIDENT_CHART` | Excel 1102 | `Lũy kế trạm năm {yyyy}`; `Tổng trạm {yyyy-1}` | Gom 8 tuần: số SC, tổng PA, tổng ảnh hưởng |
| STATION_INCIDENT | `BM_STATION_INCIDENT` | Excel 1102 | `TH`; thêm sheet lũy kế trạm để lấy top tỉnh | Số liệu cáp từ TH; top 3 tỉnh từ chi tiết tuần |
| CABLE_INCIDENT_CHART | `BM_CABLE_INCIDENT_CHART` | Excel 1102 | `Lũy kế trạm năm {yyyy}`; `Tổng trạm {yyyy-1}` | Lọc đứt/cháy cáp, gom 8 tuần |
| PROGRAM_EVENT | `BM_PROGRAM_EVENT` | Word 1119 | Section 8104 | Copy phần I của nguồn, bỏ tiêu đề phần |
| CUSTOMER_REACTION | `BM_CUSTOMER_REACTION` | Word 1119 | Section 8105 | Copy phần II của nguồn đến hết tài liệu, bỏ tiêu đề phần |
| PRODUCT_ISSUE | `BM_PRODUCT_ISSUE` | Excel 1010 | `Bất cập DV` | Lọc ngày phát hiện trong tuần; đếm theo tiến độ; lấy dòng đầu làm điển hình |
| MOBILE | `BM_MOBILE` | Excel/XLSM 1104 | `PL02-So lieu TN&XL` | Đọc số tổng đã tính và chọn nhóm biến động cùng chiều lớn nhất |
| CLM | `BM_CLM` | Excel 1107 | `Tỷ lệ PA tuần` | Đọc KQ, KPI, đánh giá và bảng top tỉnh; tự chia PA/7 |
| FIXED | `BM_FIXED` | Excel 1106 | `N.Xet tuan` và `TH` | Cộng tiếp nhận BO+KT; chia 7; tính so sánh và đánh giá KPI |
| TV360 | `BM_TV360` | Excel 1112 | `NDPA` và `NNLOI` | Đọc TB ngày; tính biến động; chọn top 2 nhóm và đọc tỷ trọng nguyên nhân |
| SME | `BM_SME` | Word 1108 | Section 8110 | Lấy diễn giải trước ảnh/marker; gộp thành một paragraph |

## 4. Ba bookmark kỳ báo cáo và nội dung tĩnh

| Bookmark | Giá trị được điền |
|---|---|
| `BM_REPORT_PERIOD_TITLE` | `dd/MM/yyyy - dd/MM/yyyy`, tương ứng W và E |
| `BM_REPORT_PERIOD_INTRO` | Cùng kỳ W–E, thay placeholder trong câu mở đầu |
| `BM_REPORT_PERIOD_CLOSING` | Cùng kỳ W–E, thay placeholder trong câu kết |

Nếu text bookmark chứa `[DD/MM-DD/MM/YYYY]`, chỉ thay token này, giữ phần còn lại của câu. Nếu không chứa token, thay toàn bộ vùng bookmark bằng kỳ báo cáo. Không đọc ngày từ file Excel/Word nguồn và không lấy ngày hiện tại của máy để xác định kỳ.

Dòng `Hà Nội, ngày ... tháng ... năm ...` ở đầu template, tên đơn vị, nơi nhận, số văn bản, tiêu đề phần và các câu dẫn ngoài bookmark là nội dung tĩnh của mẫu. Code hiện tại không có bookmark/handler riêng để tự điền ngày ký, số báo cáo hoặc chữ ký.

## 5. SYSTEM_INCIDENT – Sự cố hệ thống hàng loạt

**Nguồn:** mapping 8301, role DATA, Excel 1102, sheet `TH`; handler `systemIncident()`, reader `WordSystemIncidentReader.read()`.

### 5.1. Tìm bảng và cột

- Tìm ô có nhãn chuẩn hóa bằng `Tổng lỗi phát sinh`.
- Header có thể cùng dòng với nhãn hoặc ngay sau vùng tiêu đề gộp. Xác định cột theo `Tuần này`/kỳ W–E; `Tuần trước`/kỳ P–(W−1); `So sánh`; `Tỷ lệ tăng giảm`.
- Dòng dữ liệu ngay sau header phải liên tục theo các nhãn được chấp nhận: `Số lượng`, `PA`, `TB ảnh hưởng`; block này chỉ bắt buộc hai dòng đầu. Không quét qua dòng trống để mượn số của bảng khác.
- Kỳ có thể có hoặc không có năm; nếu có năm phải khớp. Bảng cùng kỳ bị trùng là dữ liệu không hợp lệ.

### 5.2. Công thức và bookmark

| Bookmark | Dòng/cột nguồn và cách tính |
|---|---|
| `BM_SYSTEM_INCIDENT_COUNT` | Dòng `Số lượng`, cột hiện tại C; đọc số nguyên không âm |
| `BM_SYSTEM_INCIDENT_PA` | Dòng `PA`, cột hiện tại C; đọc số nguyên không âm |
| `BM_SYSTEM_INCIDENT_SC_CHANGE` | `D_SC=C_SC−B_SC`; D=0: `không có biến động`; khác 0: `tăng/giảm abs(D_SC) SC` |
| `BM_SYSTEM_INCIDENT_PA_CHANGE` | `D_PA=C_PA−B_PA`; kiểm tra cột `So sánh` phải bằng D_PA; tỷ lệ R đọc từ `Tỷ lệ tăng giảm`, làm tròn 0 chữ số; text `PAKH tăng/giảm abs(R)% (abs(D_PA)PA)` |

Với SC, **không đọc/không bắt buộc giá trị** cột So sánh và Tỷ lệ ở dòng Số lượng. Với PA, code tự trừ hai tuần để kiểm tra cột chênh lệch nhưng **không tự tính tỷ lệ `D/B×100`** và không kiểm tra độ lớn tỷ lệ Excel có khớp phép chia hay không. Tỷ lệ khác 0 phải cùng dấu chênh lệch. Nếu D_PA=0, bỏ qua ô tỷ lệ và ghi `PAKH không có thay đổi`.

Nếu B_PA=0 nhưng D_PA khác 0, block SYSTEM vẫn yêu cầu ô tỷ lệ hợp lệ; không có nhánh xử lý mẫu số 0 như phần trạm. Số trong block hệ thống được chuyển trực tiếp từ `long` thành text, không thêm dấu phân nhóm hàng nghìn.

Nguồn code: [WordSystemIncidentReader.java](../src/main/java/com/techasians/alertservice/service/impl/WordSystemIncidentReader.java).

## 6. Ba biểu đồ sự cố – quy tắc đọc chung

Các block `SYSTEM_INCIDENT_CHART`, `STATION_INCIDENT_CHART`, `CABLE_INCIDENT_CHART` dùng `chartValues()` → `WordIncidentChartReader` → `WordTrendChartRenderer` → `WordBookmarkRenderer.insertImage()`.

### 6.1. Phạm vi thời gian và vắt năm

```text
start = W - 7 tuần
end   = W + 6 ngày
weekIndex = floor(số ngày từ start đến ngày sự cố / 7), thuộc 0..7
```

Khởi tạo 8 tuần với các chỉ số bằng 0. Tuần cũ nhất ở vị trí 0, tuần báo cáo ở vị trí 7. Cửa sổ được chia theo W; không theo số tuần ISO hay tuần lịch tự suy ra.

| Block | sort_order=1, năm Y | sort_order=2, năm Y−1 |
|---|---|---|
| SYSTEM_INCIDENT_CHART | `Lũy kế lỗi phát sinh năm {yyyy}` | `LPS năm {yyyy-1}` |
| STATION_INCIDENT_CHART | `Lũy kế trạm năm {yyyy}` | `Tổng trạm {yyyy-1}` |
| CABLE_INCIDENT_CHART | `Lũy kế trạm năm {yyyy}` | `Tổng trạm {yyyy-1}` |

Nguồn đều là Excel 1102, role CHART_ACCUMULATED. Chỉ đọc nguồn năm trước khi `year(start)<Y`. Dòng được đọc từ sheet của năm nào phải có ngày thuộc đúng năm đó, tránh lấy nhầm dòng cũ trong sheet năm hiện tại.

Ví dụ W=01/09/2026: đọc 14/07/2026–07/09/2026. Ví dụ W=05/01/2027: tuần báo cáo không vắt năm nhưng cửa sổ 8 tuần vắt năm, nên vẫn cần nguồn 2026.

### 6.2. SYSTEM_INCIDENT_CHART

**Bookmark:** `BM_SYSTEM_INCIDENT_CHART`. Đọc các cột `Năm`, `Tháng`, `Ngày`, `Chủ dịch vụ`, `Số lượng phản ảnh` (đúng tên cột dùng trong code).

- Header năm hiện tại bắt buộc `Năm`, `Tháng`, `Ngày`, `Chủ dịch vụ`. Năm trước bắt buộc `Tháng`, `Ngày`, `Chủ dịch vụ`.
- Chỉ lấy dòng `Chủ dịch vụ=VTT`, không phân biệt hoa thường sau trim.
- Năm hiện tại ghép ngày từ Năm/Tháng/Ngày. Năm trước đọc cột `Tháng` dạng `Tháng n.yyyy`, lấy cả tháng và năm từ text này, rồi ghép với cột Ngày.
- Mỗi dòng ngày hợp lệ trong cửa sổ tăng số SC của tuần tương ứng thêm 1.
- Tổng PA của tuần = tổng cột `Số lượng phản ảnh` trên các dòng được chọn. Nếu cột/ô PA thiếu hoặc không hợp lệ, chỉ số đó không được cộng, đồng thời ghi cảnh báo; SC của dòng vẫn được đếm.
- Biểu đồ: cột đỏ là PA, trục trái; đường nét đứt là số SC, trục phải. Không đọc ảnh biểu đồ có sẵn trong Excel.

### 6.3. STATION_INCIDENT_CHART

**Bookmark:** `BM_STATION_INCIDENT_CHART`. Header bắt buộc: `Năm`, `Tháng`, `Ngày`, `Tổng PA`, `Tổng ảnh hưởng`.

- Header có thể nằm trên nhiều hàng; reader tích lũy các tên cột tới khi tìm đủ, sau đó bắt đầu đọc dữ liệu. Trùng header bắt buộc thì báo thiếu dữ liệu hợp lệ.
- Cả hai năm đều ghép ngày từ ba cột Năm/Tháng/Ngày, không dùng dạng tháng text của biểu đồ hệ thống.
- Không lọc Chủ dịch vụ, Nguyên nhân hay Tỉnh.
- Mỗi dòng hợp lệ: `SC += 1`, `PA += Tổng PA`, `TB ảnh hưởng += Tổng ảnh hưởng`.
- Biểu đồ có cột đen SC, cột đỏ PA ở trục trái; đường nét đứt tổng ảnh hưởng ở trục phải.

### 6.4. CABLE_INCIDENT_CHART

**Bookmark:** `BM_CABLE_INCIDENT_CHART`. Giống biểu đồ trạm nhưng cần thêm cột `Nguyên nhân cấp 1` và chỉ lấy giá trị sau chuẩn hóa bằng **`cháy cáp` hoặc `đứt cáp`**. Không dùng điều kiện chứa chuỗi hay phân loại theo tên trạm/tỉnh.

Theo từng tuần:

```text
SC = số dòng đứt/cháy cáp có ngày hợp lệ
PA = tổng cột Tổng PA của các dòng đó
Ảnh hưởng = tổng cột Tổng ảnh hưởng của các dòng đó
```

Mỗi dòng là 1 SC; không khử trùng theo mã sự cố. PA/ảnh hưởng là tổng số theo tuần, không chia 7. Tiêu đề ảnh: `XU THẾ SỰ CỐ ĐỨT CÁP/CHÁY CÁP`; kiểu cột/đường như biểu đồ trạm.

### 6.5. Dữ liệu không hợp lệ

- Ngày không hợp lệ: bỏ dòng, cảnh báo. Ngoài cửa sổ hoặc sai năm sheet: bỏ dòng.
- Ô PA/ảnh hưởng phải là số không âm, hữu hạn; chấp nhận số hoặc text số. Ô công thức đọc kết quả cache. Ô không hợp lệ: không cộng chỉ số đó và ghi cảnh báo; dòng vẫn có thể được tính vào SC.
- Thiếu sheet/header năm Y: bookmark biểu đồ thành `Chưa đủ dữ liệu để tạo biểu đồ` và có cảnh báo.
- Thiếu sheet/header năm Y−1: tiếp tục với dữ liệu năm hiện tại, cảnh báo dữ liệu năm trước chưa đầy đủ; phần năm trước chưa đọc giữ 0.
- Không có bước đối soát tự động để ép số cuối biểu đồ bằng số trong bảng TH.

Nguồn code: [WordIncidentChartReader.java](../src/main/java/com/techasians/alertservice/service/impl/WordIncidentChartReader.java), [WordTrendChartRenderer.java](../src/main/java/com/techasians/alertservice/service/impl/WordTrendChartRenderer.java).

## 7. INCIDENT – Nội dung sự cố nổi bật từ Word

**Nguồn:** Word 1101, section **8102 – WORD_INCIDENT**, role TEXT, render COPY_SECTION.

| Thuộc tính | Giá trị |
|---|---|
| Bookmark | `BM_INCIDENT` |
| Bắt đầu | Paragraph chứa `Vấn đề/Sự cố nổi bật:` |
| Kết thúc | Paragraph chứa `SỰ CỐ TRẠM CĐBR` |
| include_start / include_end | `0 / 0`: bỏ cả hai paragraph marker |
| Kết quả | Copy tất cả paragraph, bảng, hình ảnh nằm giữa hai marker; không tính lại số trong nội dung |

Đây là cắt theo **toàn paragraph/node**, không cắt từ vị trí ký tự nằm giữa câu. Nếu paragraph marker có thêm nội dung thì phần thêm đó cũng bị loại theo paragraph. Dấu `:` thuộc start marker mặc định, vì vậy nguồn thay tiêu đề bỏ dấu `:` có thể không còn khớp.

Quy tắc tìm marker và định dạng copy được mô tả chung ở mục 11.

## 8. STATION_OVERVIEW – Tình hình chung sự cố trạm

**Nguồn:** mapping 8315, Excel 1102, sheet `TH`, bảng nhãn **`So sánh lỗi trạm`**. Reader `WordSystemIncidentReader.readStation()`.

Đọc ba dòng liên tục `Số lượng`, `PA`, `TB ảnh hưởng`; bốn cột là tuần trước B, tuần này C, chênh lệch D và tỷ lệ R. Header chênh lệch có thể là `So sánh`, `Chênh lệch`, hoặc `Tỷ lệ tăng giảm` ở vị trí cột so sánh; reader hỗ trợ hai header cuối cùng tên `Tỷ lệ tăng giảm` trong bảng trạm.

| Bookmark | Số liệu |
|---|---|
| `BM_STATION_COUNT` | C của dòng Số lượng |
| `BM_STATION_PA` | C của dòng PA |
| `BM_STATION_AFFECTED` | C của dòng TB ảnh hưởng |
| `BM_STATION_SC_CHANGE` | So sánh dòng Số lượng, đơn vị SC |
| `BM_STATION_PA_CHANGE` | So sánh dòng PA, đơn vị PA |
| `BM_STATION_AFFECTED_CHANGE` | So sánh dòng TB ảnh hưởng, đơn vị TB |

Cho từng dòng, code tự tính `D=C−B`, yêu cầu ô chênh lệch Excel bằng D. R được đọc từ Excel, không tính lại từ D/B. Giá trị R khác 0 phải cùng dấu D. Cách hiển thị:

- D=0: `không có biến động`, không cần đọc R.
- B=0 và C>0: `tăng C<đơn vị> (tuần trước không phát sinh)`, không cần đọc R, cho phép ô công thức tỷ lệ lỗi chia 0.
- Còn lại: `tăng/giảm round(abs(R),0)% (+D<đơn vị>)` nếu tăng, hoặc `(D<đơn vị>)` nếu giảm; ví dụ `giảm 23% (-44SC)`.

Số lượng hiển thị theo định dạng số nguyên vi-VN có dấu phân nhóm, ví dụ `3.013`. `BM_STATION_OVERVIEW` bao vùng câu để thay toàn khối khi thiếu dữ liệu, không phải một chỉ số riêng.

## 9. STATION_INCIDENT – Sự cố cáp và top tỉnh

### 9.1. Số liệu so sánh từ TH

**Nguồn:** mapping 8304, sort_order=1, DATA, Excel 1102, sheet `TH`; bảng nhãn **`So sánh Sự cố đứt cáp, cháy cáp trục, cáp nhánh`**. Reader `WordSystemIncidentReader.readCable()`.

Đọc ba dòng và tính/kiểm tra chênh lệch giống mục 8, điền:

| Bookmark | Số liệu |
|---|---|
| `BM_CABLE_COUNT` | Số lượng sự cố tuần này |
| `BM_CABLE_PA` | PA tuần này |
| `BM_CABLE_AFFECTED` | TB ảnh hưởng tuần này |
| `BM_CABLE_SC_CHANGE` | So sánh số sự cố, đơn vị SC |
| `BM_CABLE_PA_CHANGE` | So sánh PA, đơn vị PA |
| `BM_CABLE_AFFECTED_CHANGE` | So sánh TB ảnh hưởng, đơn vị TB |

`BM_STATION_INCIDENT` là vùng bao nội dung cáp; tên này khác prefix bookmark số liệu `BM_CABLE_`.

### 9.2. BM_CABLE_TOP_PROVINCES từ dữ liệu chi tiết

**Nguồn:** cùng block, role DATA; mapping 8322/sort_order=2: `Lũy kế trạm năm {yyyy}`; mapping 8323/sort_order=3: `Tổng trạm {yyyy-1}`. Đọc sheet năm trước **chỉ khi chính tuần W–E vắt năm**, không dùng cửa sổ 8 tuần của biểu đồ.

- Bắt buộc header `Năm`, `Tháng`, `Ngày`, `Nguyên nhân cấp 1`, `Tỉnh`.
- Lọc `Nguyên nhân cấp 1` chuẩn hóa bằng `cháy cáp` hoặc `đứt cáp`, ngày trong W–E và đúng năm sheet.
- Chuẩn hóa mã tỉnh bằng trim/gộp khoảng trắng/chữ hoa; dòng thiếu tỉnh bỏ khỏi xếp hạng và cảnh báo.
- `count[tỉnh] += 1` trên từng dòng; không cộng Tổng PA/Tổng ảnh hưởng và không khử trùng mã SC.
- Sắp giảm theo số SC; bằng nhau thì tăng theo mã tỉnh; lấy tối đa 3. Text: `HCM – 14SC; DNI – 11SC; HNI – 10SC`.
- Tuần vắt năm: cộng số SC cùng tỉnh từ cả hai sheet trước khi xếp hạng.
- Không có tỉnh hợp lệ và số SC trong TH bằng 0: `Không phát sinh sự cố`.
- Không có tỉnh hợp lệ nhưng TH có SC, hoặc nguồn/header top tỉnh thiếu: `Chưa có dữ liệu` kèm cảnh báo. Thiếu năm trước trong tuần vắt năm làm phần top tỉnh không được trả kết quả một phần như biểu đồ.

Số liệu cáp TH phải được đọc thành công trước khi handler chạy phần top tỉnh; nếu bảng TH không hợp lệ, cả block bị thay bằng `Chưa có dữ liệu`.

## 10. PROGRAM_EVENT và CUSTOMER_REACTION – Chương trình/chính sách từ Word

**Nguồn:** cùng file Word **1119** của tuần báo cáo. Không dùng file Excel 1120/1121 để tính nội dung hai phần này.

| Block / bookmark | Section / mapping | Bắt đầu | Kết thúc | Bao gồm marker? |
|---|---|---|---|---|
| PROGRAM_EVENT / `BM_PROGRAM_EVENT` | 8104 / 8305 | `CÁC CHƯƠNG TRÌNH/SỰ KIỆN TRỌNG ĐIỂM, NỔI BẬT` | `PHẢN ỨNG KH VỀ CÁC CHƯƠNG TRÌNH/CHÍNH SÁCH MỚI/ĐIỀU CHỈNH` | Không lấy hai paragraph tiêu đề |
| CUSTOMER_REACTION / `BM_CUSTOMER_REACTION` | 8105 / 8306 | `PHẢN ỨNG KH VỀ CÁC CHƯƠNG TRÌNH/CHÍNH SÁCH MỚI/ĐIỀU CHỈNH` | Không cấu hình end marker: lấy tới hết body tài liệu nguồn | Không lấy paragraph tiêu đề bắt đầu |

Cả hai dùng TEXT_RANGE, include_start=0, include_end=0, không giới hạn số chương trình. Copy nguyên nội dung paragraph/bảng/ảnh trong phạm vi; các số trong đoạn được giữ như nguồn. Không chọn theo tên chương trình từng tuần, không cộng/trừ số liệu bên trong.

Nếu nguồn có tiền tố `I.`/`II.` trong paragraph tiêu đề vẫn khớp vì tìm marker bằng phép **contains** sau chuẩn hóa. Phần II lấy đến hết tài liệu, nên nội dung cuối hoặc phần ký trong body nguồn cũng được lấy nếu nằm sau marker và không có end marker khác được cấu hình.

## 11. Quy tắc chung cho COPY_SECTION

Áp dụng mặc định cho INCIDENT, PROGRAM_EVENT và CUSTOMER_REACTION:

- Ghép danh sách node cấp body của tất cả section Word; chỉ tìm marker trong paragraph cấp body, không tìm trong text tổng hợp của bảng, header hoặc footer.
- So khớp marker sau Unicode NFC, thay NBSP bằng khoảng trắng, gộp khoảng trắng, trim và chuyển chữ thường. Không bỏ dấu tiếng Việt hay dấu câu.
- Start marker phải duy nhất trong toàn vùng tìm; end marker phải duy nhất từ sau start đến cuối tài liệu. Thiếu/trùng marker hoặc phạm vi rỗng tạo cảnh báo và text `Chưa có dữ liệu`.
- Import node bằng KEEP_SOURCE_FORMATTING, giữ font hiệu lực nguồn, căn lại phần copy theo vị trí đích qua `WordSectionAlignment`, đặt cỡ chữ nội dung **14 pt**, gồm chữ trong bảng. Copy ảnh và bảng trong vùng đã chọn.
- Không tự hiểu các câu có số tuần/ngày trong nội dung nguồn để thay thành kỳ W–E.

Nguồn code: [WordSectionExtractor.java](../src/main/java/com/techasians/alertservice/service/impl/WordSectionExtractor.java), [WeeklyWordOutputGenerator.java](../src/main/java/com/techasians/alertservice/service/impl/WeeklyWordOutputGenerator.java), [WordBookmarkRenderer.java](../src/main/java/com/techasians/alertservice/service/impl/WordBookmarkRenderer.java).

## 12. PRODUCT_ISSUE – Góp ý/bất cập sản phẩm, dịch vụ

**Nguồn:** mapping 8307, DATA, Excel 1010, sheet **`Bất cập DV`**. Reader `WordProductIssueReader.read()`.

### 12.1. Đọc dòng và lọc tuần

- Tìm hàng đầu tiên đồng thời có header `Ngày phát hiện`, `Tiến độ`, `Nội dung`; đây là ba cột bắt buộc.
- Đọc thêm `Tên bất cập/lỗi` và `Kết quả` nếu có. Không cố định cột A/B/C hay số dòng của file mẫu.
- Từ sau header đến cuối sheet, bỏ dòng hoàn toàn trống theo các trường chính. Mỗi dòng có ngày hợp lệ trong W–E được tính là 1 bất cập; không khử trùng theo tên hoặc nội dung.
- Ngày được đọc từ **chuỗi hiển thị** của ô, chấp nhận `d/M/yyyy` hoặc `d/M/yy`, parse ngày nghiêm ngặt. Năm hai chữ số là 2000–2099. Ô ngày Excel có định dạng phù hợp cũng được chuyển thành chuỗi để parse. Ngày không hợp lệ hoặc ngoài tuần bị bỏ qua; riêng trường hợp ngày không hợp lệ này không có cảnh báo trong reader hiện tại.
- Không sử dụng nhãn `Lũy kế tuần` để đếm và không lấy toàn bộ bất cập lũy kế trong file.

### 12.2. Đếm và tính tỷ lệ

Gọi N là số dòng bất cập được chọn trong tuần; H là số `Đã hoàn thành`; T là số `Đang thực hiện`; G là số `Đang đánh giá`.

| Bookmark | Công thức/nguồn |
|---|---|
| `BM_PRODUCT_ISSUE_TOTAL` | N |
| `BM_PRODUCT_ISSUE_COMPLETED` | H |
| `BM_PRODUCT_ISSUE_IN_PROGRESS` | T |
| `BM_PRODUCT_ISSUE_OPTIMIZED` | H + T |
| `BM_PRODUCT_ISSUE_OPTIMIZED_RATE` | `round((H+T)×100/N, 1)` |
| `BM_PRODUCT_ISSUE_EVALUATING` | G |
| `BM_PRODUCT_ISSUE_EVALUATING_RATE` | `round(G×100/N, 1)` |

So khớp trạng thái bằng text chuẩn hóa chính xác. Trạng thái `Không khả thi` được đếm riêng bên trong kết quả; trạng thái khác/trống được tính là chưa phân loại và ghi cảnh báo. Cả hai nhóm này **vẫn thuộc mẫu số N**, nhưng không có bookmark số riêng trong template hiện tại, vì vậy hai tỷ lệ hiển thị không nhất thiết cộng thành 100%.

### 12.3. Góp ý điển hình

Chọn **dòng đầu tiên theo thứ tự sheet sau khi lọc tuần**, không chọn theo mức độ nghiêm trọng hay tiến độ:

| Bookmark | Dữ liệu |
|---|---|
| `BM_PRODUCT_ISSUE_ACTUALITY` | Phần sau nhãn `Thực trạng:` trong cột Nội dung, đến trước nhãn kế tiếp được regex nhận diện |
| `BM_PRODUCT_ISSUE_CAUSE` | Phần sau `Nguyên nhân:` trong cột Nội dung |
| `BM_PRODUCT_ISSUE_PROPOSAL` | **Toàn bộ cột `Kết quả`** của dòng điển hình |

Regex nhận diện nhãn Thực trạng/Bất cập/Nguyên nhân/Đề xuất, không phân biệt hoa thường; cho phép số thứ tự/bullet và dấu hai chấm thường hoặc toàn chiều rộng. `Bất cập` là một ranh giới được nhận diện, nhưng getter Thực trạng không coi nhãn `Bất cập:` là đồng nghĩa với `Thực trạng:`.

Đặc biệt: dù bookmark có tên `PROPOSAL` và template ghi `=> Giải pháp:`, handler hiện gọi **`selected.getResult()`**, không gọi `getProposal()` để lấy phần Đề xuất trong Nội dung. Thiếu một trong ba phần điển hình thì phần đó thành `Chưa có dữ liệu`, đồng thời ghi cảnh báo. Tài liệu output có custom properties `ProductIssueSourceFileId` và `ProductIssueSourceRow` để truy ngược dòng được chọn.

Nếu N=0 và không có cảnh báo: thay cả `BM_PRODUCT_ISSUE` bằng `Không phát sinh bất cập có ngày phát hiện trong tuần.` Nếu N=0 nhưng có cảnh báo, dùng câu chưa đủ dữ liệu hợp lệ để kết luận. Ô công thức đọc cache, không tính lại.

Nguồn code: [WordProductIssueReader.java](../src/main/java/com/techasians/alertservice/service/impl/WordProductIssueReader.java), handler `productIssues()` trong [WeeklyWordOutputGenerator.java](../src/main/java/com/techasians/alertservice/service/impl/WeeklyWordOutputGenerator.java).

## 13. MOBILE – Phản ánh dịch vụ di động

**Nguồn:** mapping 8308, DATA, Excel/XLSM 1104, sheet **`PL02-So lieu TN&XL`**. Reader `WordMobileFeedbackReader.read()`.

### 13.1. Xác định bảng và số tổng

- Tìm đúng một header `TB tuần (dd/MM – dd/MM)` của W–E; mỗi mốc có thể có năm. Chấp nhận dấu phân cách khoảng `-`, `–`, `—`.
- Cùng hàng phải tìm được `Tăng giảm`, `Tỷ lệ`; cột `Dịch vụ`, `STT` có thể cùng hàng hoặc hàng ngay phía trên.
- Ngay dưới header phải có dòng `Phản ánh báo hỏng dịch vụ` trong cột Dịch vụ.
- Đọc trực tiếp ba giá trị ở dòng tổng: **TB tuần A**, **Tăng giảm D**, **Tỷ lệ R**. A phải không âm; D và R phải cùng dấu, kể cả trường hợp 0.

**Không cộng các dòng dịch vụ để ra A, không chia tổng tuần cho 7, không tự trừ hai cột tuần và không tự tính D/B×100.** Những phép tính đó phải có sẵn trong workbook. Header tuần trước không phải điều kiện bắt buộc của reader này.

### 13.2. Điền tổng và so sánh

| Bookmark | Cách điền |
|---|---|
| `BM_MOBILE_AVERAGE` | `round(A,0)`, định dạng số nguyên vi-VN |
| `BM_MOBILE_CHANGE` | D=0: `không có biến động`; còn lại `tăng/giảm round(abs(R),1)%`, bỏ số 0 thập phân dư |
| `BM_MOBILE_CHANGE_DETAILS` | D=0: rỗng; còn lại ` (+/-round(abs(D),0)PA/ngày)` |
| `BM_MOBILE_DIRECTION` | Chiều `tăng` hoặc `giảm` theo dấu D của tổng |

Ô tỷ lệ dạng số có định dạng `%` được nhân 100 để chuyển từ tỷ phần sang giá trị phần trăm; text dạng `13,4%` được đọc thành 13,4.

### 13.3. Chọn nhóm PA điển hình

- Chỉ chạy khi tổng có D khác 0.
- Duyệt ngay sau dòng tổng, yêu cầu STT liên tiếp đúng `1, 2, 3, ...` và tên dịch vụ không trống. Gặp sai thứ tự/tên trống thì dừng; bảng chi tiết bắt đầu lại STT 1 không được trộn vào nhóm tổng hợp.
- Chỉ xét nhóm có dấu Tăng giảm cùng chiều với D tổng. Trong số nhóm có đủ bộ số hợp lệ, chọn **abs(Tỷ lệ) lớn nhất**, không chọn theo số PA tăng/giảm tuyệt đối. Nếu bằng nhau, giữ nhóm xuất hiện trước.
- Nhóm có số liệu không hợp lệ được bỏ qua và cảnh báo, không nhất thiết làm hỏng toàn block.

| Bookmark | Cách điền |
|---|---|
| `BM_MOBILE_SERVICE` | Tên nhóm được chọn; thêm `Lỗi ` nếu tên chuẩn hóa chưa bắt đầu bằng `lỗi ` |
| `BM_MOBILE_SERVICE_CHANGE` | `tăng/giảm abs(R_nhóm)%`, làm tròn tối đa 1 số thập phân; riêng nhóm tăng có R>100 thì dùng `tăng round(R/100,1) lần` |
| `BM_MOBILE_TYPICAL_SENTENCE` | Vùng bao cả câu điển hình để thay khi không có nhóm phù hợp |

Ví dụ quy tắc hiển thị trong code: nhóm tăng 680% → `tăng 6,8 lần`; code không cộng thêm 1 vào R/100.

Nếu tổng D=0: câu điển hình thành `Không có PA biến động điển hình.` Nếu tổng biến động nhưng không tìm được nhóm hợp lệ cùng chiều: `Chưa đủ dữ liệu để xác định PA điển hình.`

Mẫu câu: `Tiếp nhận 1.722PA/ngày, tăng 13,4% (+204PA/ngày) so với tuần trước. PA tăng điển hình là Lỗi Kết nối tăng 6,8 lần.` Đây là ví dụ trong test, không phải khẳng định số liệu của mọi lần chạy.

Nguồn code: [WordMobileFeedbackReader.java](../src/main/java/com/techasians/alertservice/service/impl/WordMobileFeedbackReader.java).

## 14. CLM – Phản ánh chất lượng mạng, thoại và data

**Nguồn:** mapping 8309, DATA, Excel 1107, sheet **`Tỷ lệ PA tuần`**. Reader `WordNetworkQualityReader.read()`.

Header kỳ có dạng **`Tuần từ dd.MM đến dd.MM`**, cho phép dấu `.`, `,`, `/` giữa ngày/tháng và có thể có năm. Nhóm kỳ cần các cột con **`PA`, `Thuê bao`, `KQ`**. Tìm nhãn/header theo nội dung, không cố định tọa độ ô.

Reader xử lý độc lập ba phần: tổng quan, thoại và data. Một phần thiếu dữ liệu vẫn có thể giữ các phần khác.

### 14.1. Tổng quan

Tìm nhãn **`Tỷ lệ chung`**. Tìm nhóm header tuần hiện tại và tuần trước ở phía trên nhãn; hai nhóm phải cùng hàng header. Tại dòng Tỷ lệ chung, đọc:

| Bookmark | Cách tính/đọc |
|---|---|
| `BM_CLM_DAILY_PA` | `round(PA_hiện_tại/7,0)`, định dạng số nguyên vi-VN |
| `BM_CLM_RATE` | Đọc `KQ` tuần hiện tại, làm tròn tối đa 2 chữ số thập phân |
| `BM_CLM_PREVIOUS_RATE` | Đọc `KQ` tuần trước, cùng quy tắc làm tròn |
| `BM_CLM_TREND` | So KQ hiện tại với KQ trước: `tăng`, `giảm`, hoặc `không đổi` |
| `BM_CLM_OVERVIEW` | Vùng tổng quan để thay `Chưa có dữ liệu` nếu phần này không hợp lệ |

PA/Thuê bao phải là số nguyên không âm và Thuê bao hiện tại phải >0. **Không tự tính `PA/Thuê bao×1.000.000`; KQ được đọc sẵn từ Excel.** Không tính phần trăm thay đổi KQ, chỉ so lớn/nhỏ/bằng.

### 14.2. Chỉ tiêu thoại và data

Tìm lần lượt nhãn **`Chỉ tiêu thoại`**, **`Chỉ tiêu Data`**. Phạm vi mỗi bảng từ sau nhãn đến trước nhãn kế tiếp bắt đầu bằng `Chỉ tiêu ` hoặc cuối sheet.

Trong bảng tìm nhóm tuần W–E và các cột `KPIs`, `Đánh giá`, `Nguồn tiếp nhận`. Bỏ qua dòng tổng hoàn toàn trống; dòng tổng đầu tiên phải có nhãn nguồn là rỗng/`Tổng`/`Toàn quốc`/`Thoại`/`Data`; không dùng dòng tỉnh để thay thế dòng tổng.

| Bookmark Thoại | Bookmark Data | Cột nguồn |
|---|---|---|
| `BM_CLM_VOICE_RATE` | `BM_CLM_DATA_RATE` | KQ của dòng tổng tuần hiện tại |
| `BM_CLM_VOICE_KPI` | `BM_CLM_DATA_KPI` | KPIs của dòng tổng |
| `BM_CLM_VOICE_ASSESSMENT` | `BM_CLM_DATA_ASSESSMENT` | Đánh giá: chỉ nhận `Đạt`/`Không đạt`, cho phép bỏ hậu tố ` KPI` |
| `BM_CLM_VOICE` | `BM_CLM_DATA` | Vùng bao câu của từng dịch vụ để thay khi thiếu dữ liệu |

Reader kiểm tra PA/Thuê bao và Thuê bao >0. **Đánh giá được đọc sẵn từ Excel, không tự so KQ với KPI để kết luận.** KQ/KPI hiển thị tối đa 2 chữ số thập phân.

### 14.3. Cảnh báo tỉnh

| Dịch vụ | Bảng nguồn | Bookmark |
|---|---|---|
| Thoại | `TOP 5 Tỉnh Thoại tăng cao nhất Tuần` | `BM_CLM_VOICE_WARNING_COUNT`, `BM_CLM_VOICE_WARNINGS`, `BM_CLM_VOICE_WARNING_SENTENCE` |
| Data | `TOP 5 Tỉnh Data tăng cao nhất Tuần` | `BM_CLM_DATA_WARNING_COUNT`, `BM_CLM_DATA_WARNINGS`, `BM_CLM_DATA_WARNING_SENTENCE` |

- Dưới tiêu đề bảng tìm cột `Tỉnh`, `Tuần hiện tại`.
- Đọc tối đa **5 dòng ngay sau header**, dừng khi gặp tên tỉnh trống; lỗi số liệu một dòng thì cảnh báo và bỏ dòng đó.
- Bỏ tiền tố `Tỉnh`/`Thành phố` trong tên; đọc giá trị Tuần hiện tại, ghép `Tên: xPA`, nối bằng `; `.
- WARNING_COUNT = số dòng tỉnh hợp lệ đã đọc, không tự đếm toàn bộ tỉnh không đạt trên cả sheet.
- **Không tự xếp lại top, không lọc lại theo KPI và không tính mức tăng của tỉnh.** Reader tin nội dung bảng top đã có trong Excel. Template mô tả là tỉnh không đạt nhưng code hiện tại không chứng minh lại điều kiện này.
- Đọc dưới 5 tỉnh hợp lệ: có cảnh báo. Không đọc được bảng/top: thay vùng WARNING_SENTENCE bằng `Chưa đủ dữ liệu cảnh báo tỉnh.`, vẫn giữ số KQ/KPI nếu hợp lệ.

Nguồn code: [WordNetworkQualityReader.java](../src/main/java/com/techasians/alertservice/service/impl/WordNetworkQualityReader.java).

## 15. FIXED – Phản ánh dịch vụ cố định

**Nguồn:** cùng definition Excel **1106**, role DATA; mapping 8310/sort_order=1 là **`N.Xet tuan`**, mapping 8313/sort_order=2 là **`TH`**. Workbook chỉ tải/mở một lần nếu cùng ID file. Reader `WordFixedFeedbackReader.read()`.

### 15.1. Xác định bảng tuần

Trong N.Xet tuan:

- Tìm header nhóm **`Cùng kỳ tuần trước`** và các nhãn chỉ tiêu nằm dưới, về bên trái nhóm đó.
- Chọn cột nhãn chỉ tiêu gần nhóm tuần nhất; các nhãn diễn giải ở cột trái xa hơn không dùng để chọn số liệu.
- Tìm đúng một cột tuần W–E phía trước nhóm Cùng kỳ tuần trước; bên trong nhóm này tìm cột P–(W−1). Cột tuần trước không đúng kỳ thì không dùng số lịch sử khác để thế vào.
- Kỳ chấp nhận `dd/MM-dd/MM`, `dd.MM-dd.MM`, có thể kèm năm và tiền tố `Tuần` hoặc `Tuần từ`.
- Phạm vi bảng dừng trước header cùng loại tiếp theo ở cùng cột; nhãn chỉ tiêu được tìm duy nhất trong phạm vi bảng đã chọn.

### 15.2. Cột/dòng nguồn và công thức

Gọi BO, KT, S, R là các ô **tổng tuần/giá trị tuần** ở cột đúng kỳ, trước bước định dạng.

| Bookmark | Dòng nguồn trong N.Xet tuan | Công thức |
|---|---|---|
| `BM_FIXED_DAILY` | `HTDV + sự cố chuyển BO` và `HTDV + sự cố chuyển KT` | `round((BO_C + KT_C)/7,0)` |
| `BM_FIXED_TECH_DAILY` | `SỰ CỐ chuyển KT` | `round(S_C/7,0)` |
| `BM_FIXED_TECH_CHANGE` | Cùng dòng SỰ CỐ chuyển KT, hai tuần | Nếu S_C=S_B: `không đổi`; nếu S_B>0: `tăng/giảm round(abs(S_C−S_B)×100/S_B,0)%` |
| `BM_FIXED_RATE` | `TLPA/10K` | Đọc R_C, làm tròn tối đa 2 số thập phân |
| `BM_FIXED_RATE_CHANGE` | Cùng dòng TLPA/10K, hai tuần | Nếu bằng nhau: `không đổi`; còn lại `tăng/giảm round(abs(R_C−R_B),2)PA` |
| `BM_FIXED_GPON_RATE` | `FTTH GPON` bên dưới dòng TLPA/10K | Đọc tỷ lệ dịch vụ ở cột tuần hiện tại |
| `BM_FIXED_THS_RATE` | `THS` hoặc `Truyền hình số` bên dưới TLPA/10K | Đọc tỷ lệ dịch vụ ở cột tuần hiện tại |

Nhãn BO/KT/SỰ CỐ chấp nhận tiền tố `Tiếp nhận ` sau chuẩn hóa. Nhãn dịch vụ bỏ tiền tố `Dịch vụ ` và chuẩn hóa dấu nối/gạch dưới thành khoảng trắng. Không lấy số tổng `Tổng DVCĐ` hay sheet `TPLA tuần N` để thay cho bảng hiện tại.

Lưu ý:

- Cộng đúng hai dòng tiếp nhận BO và KT; không cộng thêm dòng SỰ CỐ chuyển KT vào BM_FIXED_DAILY lần thứ hai.
- Phần trăm sự cố kỹ thuật được tính từ **tổng tuần chưa làm tròn**, không từ SC/ngày đã làm tròn.
- TLPA/10K là giá trị có sẵn trong ô; không tự tính lại từ số phản ánh và số thuê bao.
- Chênh lệch TLPA/10K dùng đơn vị **PA**, không phải phần trăm.
- Nếu S_B=0 và S_C>0: không thể tính phần trăm; để thiếu phần so sánh và ghi cảnh báo. Nếu cả hai bằng 0 thì vẫn hiển thị `không đổi`.

### 15.3. KPI FTTH GPON và THS

Ngưỡng lấy từ sheet **TH** của nguồn 1106:

1. Tìm bảng có header `Chỉ tiêu`, dòng dịch vụ tương ứng và cột `KPI` trên hàng header.
2. Nhãn dịch vụ sau chuẩn hóa cần là `ftth gpon` hoặc `truyền hình số`; phần N.Xet tuan có hỗ trợ THS như tên thay thế cho Truyền hình số.
3. Trong nhóm ô KPI, bỏ ký hiệu `≤`/`<=`, đọc đúng một số ngưỡng. Ký hiệu `≥`/`>=` bị từ chối vì KPI này phải là ngưỡng tối đa. Không hardcode ngưỡng 12/5 trong Java.
4. Tự đánh giá **Đạt nếu rate≤KPI**, ngược lại Không đạt.

| FTTH GPON | THS | Giá trị |
|---|---|---|
| `BM_FIXED_GPON_KPI` | `BM_FIXED_THS_KPI` | Ngưỡng KPI từ TH, tối đa 2 số thập phân |
| `BM_FIXED_GPON_ASSESSMENT` | `BM_FIXED_THS_ASSESSMENT` | Code tự so tỷ lệ với KPI |
| `BM_FIXED_GPON_KPI_PART` | `BM_FIXED_THS_KPI_PART` | Vùng bao để thay bằng `– Chưa đủ dữ liệu đánh giá KPI.` nếu thiếu ngưỡng |
| `BM_FIXED_GPON` | `BM_FIXED_THS` | Vùng dịch vụ; nếu không đọc được tỷ lệ thì thay bằng `Chưa có dữ liệu` |

### 15.4. Công thức Excel và thiếu dữ liệu

Đây là reader có bước **thử tính lại công thức bằng FormulaEvaluator** cho các ô số được sử dụng. Nếu evaluator ném RuntimeException, dùng cache Excel đã lưu và ghi cảnh báo chưa xác minh độ mới, tối đa một cảnh báo loại này cho mỗi workbook. Nếu evaluator trả về giá trị lỗi thay vì ném exception, ô đó vẫn bị xử lý là dữ liệu không hợp lệ; không mặc nhiên lấy cache để che lỗi.

Các phần tiếp nhận/kỹ thuật/TLPA/dịch vụ/KPI xử lý độc lập khi gặp lỗi dữ liệu đã biết:

| Bookmark vùng điều khiển | Cách thay khi thiếu dữ liệu |
|---|---|
| `BM_FIXED_RECEIVED` | `Chưa có dữ liệu tiếp nhận.` |
| `BM_FIXED_TECH_SENTENCE` | `Chưa có dữ liệu sự cố kỹ thuật.` |
| `BM_FIXED_TECH_COMPARISON` | `Chưa đủ dữ liệu so sánh với tuần trước.` |
| `BM_FIXED_RATE_SENTENCE` | `Chưa có dữ liệu TLPA.` |
| `BM_FIXED_RATE_COMPARISON` | `Chưa đủ dữ liệu so sánh với tuần trước.` |

Nếu thiếu file chính N.Xet tuan thì cả vùng `BM_FIXED` thành `Chưa có dữ liệu`. Nếu chỉ thiếu TH/KPI, vẫn có thể giữ số liệu tuần và tỷ lệ dịch vụ. Handler hiện không đọc/copy phần Word tiến độ xử lý từ nguồn 1110 vào block này.

Nguồn code: [WordFixedFeedbackReader.java](../src/main/java/com/techasians/alertservice/service/impl/WordFixedFeedbackReader.java), [report-phase5-fixed-feedback-excel.sql](report-phase5-fixed-feedback-excel.sql).

## 16. TV360 – Phản ánh, nhóm lỗi tập trung và nguyên nhân

**Nguồn:** Excel **1112**, role DATA; mapping 8311/sort_order=1: **NDPA**; mapping 8318/sort_order=2: **NNLOI**. Reader `WordTv360FeedbackReader.read()`.

### 16.1. Định vị bảng

- Mỗi sheet cần đúng một dòng tổng có nhãn **`DỊCH VỤ TV360`**.
- Trước dòng tổng, tìm header kỳ đúng W–E. Header kỳ phải gộp thành nhóm cột; bên dưới tìm hai cột **`TB ngày`**, **`Tỷ trọng ngày`**.
- Kỳ dùng dấu `/` cho ngày tháng và `-` hoặc `–` cho khoảng; có thể có năm. Header rút gọn có năm ở mốc cuối được suy ra đúng khi vắt năm.
- Các cột trái nhóm tuần có `STT`; NDPA có nhãn **`Nội dung phản ánh`**, NNLOI có nhãn **`Nguyên nhân lỗi`**.
- Duyệt nhóm bên dưới dòng tổng: STT là số nguyên dương tăng dần, không bắt buộc liên tiếp. Dòng STT trống hoặc `-` được bỏ qua trong kiểm tra ranh giới và không phải nhóm tổng hợp. Dừng trước STT không hợp lệ hoặc STT bắt đầu lại/không tăng, để không lấy bảng chi tiết thiết bị bên dưới.

### 16.2. Tổng tiếp nhận và biến động

Gọi A là ô **TB ngày** tại dòng tổng DỊCH VỤ TV360 của tuần hiện tại; B là cùng ô thuộc nhóm tuần trước trong **NDPA**.

| Bookmark | Cách tính |
|---|---|
| `BM_TV360_DAILY` | Đọc A trực tiếp, `round(A,0)`, số nguyên vi-VN; không cộng nhóm và không chia 7 |
| `BM_TV360_CHANGE` | Nếu A=B: `không đổi`; còn lại `(A−B)/B×100`, hiển thị chiều tăng/giảm và trị tuyệt đối |
| `BM_TV360_COMPARISON` | Vùng bao so sánh để thay câu chưa đủ dữ liệu khi không đọc được B hoặc B=0 mà A>0 |

Tỷ lệ biến động tính ở độ chính xác trung gian 8 chữ số. Bình thường làm tròn 0 chữ số phần trăm; nếu làm tròn 0 chữ số thành 0 nhưng biến động thật khác 0 thì hiển thị tối đa **2 chữ số** để giữ biến động nhỏ. Ví dụ 0,2% không bị in thành 0%.

### 16.3. Top 2 nhóm phản ánh từ NDPA

- Lấy các dòng nhóm STT hợp lệ trong phạm vi bảng, đọc tên ở Nội dung phản ánh và tỷ phần ở Tỷ trọng ngày của tuần hiện tại.
- Chỉ lấy tỷ phần >0; sắp giảm dần, bằng nhau giữ thứ tự sheet; chọn tối đa 2 nhóm.
- Không chọn theo TB ngày của nhóm và không tự tính tỷ phần từ tổng.

| Bookmark | Giá trị |
|---|---|
| `BM_TV360_SERVICE_1`, `BM_TV360_SERVICE_2` | Tên nhóm top 1/top 2 |
| `BM_TV360_SHARE_1`, `BM_TV360_SHARE_2` | `round(share×100,1)%`, bỏ số 0 thập phân dư |
| `BM_TV360_SECOND_SERVICE` | Vùng bao phần nhóm thứ hai; xóa nếu chỉ có một nhóm |
| `BM_TV360_TOP_SENTENCE` | Vùng bao câu top; A=0 và không có nhóm: `Không phát sinh PAKH trong tuần.`; A>0 nhưng thiếu nhóm hợp lệ: câu chưa đủ dữ liệu xác định PAKH tập trung |

### 16.4. Nguyên nhân từ NNLOI

Trong phạm vi bảng DỊCH VỤ TV360 trên NNLOI, tìm **đúng một nhóm** cho mỗi tên:

| Bookmark | Nhãn nhóm | Giá trị |
|---|---|---|
| `BM_TV360_SUPPORT_SHARE` | `Hỗ trợ khách hàng` | Tỷ trọng ngày tuần hiện tại ×100, tối đa 1 số thập phân, thêm `%` |
| `BM_TV360_CUSTOMER_SHARE` | `Lỗi phía khách hàng` | Cùng cách tính |
| `BM_TV360_CAUSES` | Vùng bao cả phần nguyên nhân | Nếu một trong hai nhóm hoặc sheet thiếu/không hợp lệ: thay bằng `Chưa đủ dữ liệu nguyên nhân lỗi.` |

Giá trị share phải nằm trong [0,1]. Số Excel đọc như tỷ phần; text có `%` được chia 100 trước khi dùng. Không cộng hai tỷ lệ này để ép bằng 100% và không tự gộp các nguyên nhân khác vào hai nhóm trên.

Các phần so sánh/top/nguyên nhân được xử lý độc lập; tổng A không đọc được thì cả `BM_TV360` thành `Chưa có dữ liệu`. Ô công thức dùng cache, không tính lại tham chiếu file ngoài.

Nguồn code: [WordTv360FeedbackReader.java](../src/main/java/com/techasians/alertservice/service/impl/WordTv360FeedbackReader.java).

## 17. SME – Diễn giải phản ánh dịch vụ SME từ Word

**Nguồn:** mapping 8312, Word **1108**, role TEXT, section **8110 – WORD_SME**, render CUSTOM_HANDLER. Reader `WordSmeFeedbackReader.read()`; không dùng Excel 1109 hay nguồn KPI thứ hai để tính số.

| Thuộc tính | Cấu hình mặc định |
|---|---|
| Bookmark | `BM_SME` |
| extract_mode | TEXT_RANGE |
| Start marker | `Tình hình tiếp nhận và xử lý PAKH:` |
| End marker | `Xu thế PAKH theo tuần:` |
| include_start / include_end | 0 / 0 |

Quy tắc xử lý:

1. Tìm start marker duy nhất trong các paragraph cấp body của tất cả section Word; so khớp contains sau chuẩn hóa giống phần cắt Word chung.
2. Bắt đầu sau paragraph start marker theo include_start mặc định.
3. Lấy chữ theo thứ tự node và **dừng trước ảnh đầu tiên hoặc end marker**, tùy điều kiện nào gặp trước. Nếu chữ và ảnh cùng paragraph thì giữ phần chữ đứng trước ảnh.
4. Với cấu hình include_end=1, có thể lấy thêm chữ của paragraph end marker trước ảnh; mặc định 0 nên không lấy.
5. Bỏ bullet/gạch đầu dòng dạng text ở đầu từng đoạn, gộp khoảng trắng và ghép tất cả thành **một paragraph**. Giữ nhấn mạnh bold/italic/underline/màu từ run nguồn, font theo template, cỡ 14 pt.
6. Các số PA, tỷ lệ và câu nhận xét giữ nguyên theo Word nguồn; không parse số để cộng/trừ hay tính lại.

Gặp bảng trước ảnh/end marker thì báo thiếu dữ liệu; không copy bảng. Không có cả ảnh lẫn marker kết thúc, start trùng/thiếu hoặc đoạn kết quả rỗng thì `BM_SME` thành `Chưa có dữ liệu` kèm cảnh báo. Vì ảnh là một điều kiện kết thúc hợp lệ, reader vẫn có thể thành công khi không gặp end marker nhưng đã gặp ảnh.

SQL cập nhật riêng SME giữ marker/include_* đã được cấu hình ở section tồn tại; vì vậy cần kiểm tra section 8110 trong DB nếu phần cắt thực tế khác mặc định trên.

Nguồn code: [WordSmeFeedbackReader.java](../src/main/java/com/techasians/alertservice/service/impl/WordSmeFeedbackReader.java), [report-phase5-sme-feedback-word.sql](report-phase5-sme-feedback-word.sql).

## 18. Công thức, làm tròn và dữ liệu thiếu – đối chiếu nhanh

### 18.1. Những chỉ số code tự tính

| Nhóm | Phép tính thực sự trong Java |
|---|---|
| TH hệ thống | D_SC=C_SC−B_SC; D_PA=C_PA−B_PA để đối soát chênh lệch |
| TH trạm/cáp | D=C−B cho SC, PA, ảnh hưởng; đọc tỷ lệ từ Excel |
| Biểu đồ | Đếm dòng SC và cộng cột PA/ảnh hưởng theo 8 nhóm 7 ngày |
| Top tỉnh cáp | Đếm dòng theo tỉnh trong một tuần; sort và lấy 3 |
| Bất cập | Đếm trạng thái, cộng hoàn thành+thực hiện, chia cho tổng để tính % |
| Mobile | Chọn max abs(tỷ lệ) cùng chiều; làm tròn và đổi cách hiển thị; không tự tính tổng/biến động |
| CLM | PA/7; so KQ hai tuần; đếm số dòng tỉnh top hợp lệ |
| Fixed | (BO+KT)/7; kỹ thuật/7; % chênh lệch kỹ thuật; hiệu TLPA; so rate≤KPI |
| TV360 | % thay đổi TB ngày hai tuần; sort tỷ trọng lấy top 2; tỷ phần×100 để hiển thị |
| Word copy và SME | Không tính lại số nghiệp vụ |

### 18.2. Chính sách ô công thức

| Reader | Cách dùng ô công thức |
|---|---|
| WordSystemIncidentReader | Dùng cached formula result cho số được đọc |
| WordIncidentChartReader | Dùng cache cho số PA/ảnh hưởng và các phần ngày số; các nhãn/header/lọc dùng DataFormatter như code hiện tại |
| WordProductIssueReader | Dùng cache, có xét workbook date1904 khi định dạng kết quả ngày số |
| WordMobileFeedbackReader | Dùng cache; không tính lại công thức |
| WordNetworkQualityReader | Dùng cache; không tính lại công thức |
| WordTv360FeedbackReader | Dùng cache; không tính lại công thức liên kết ngoài |
| WordFixedFeedbackReader | Thử evaluate các ô số; khi evaluator ném exception thì fallback cache và cảnh báo |

Không được mặc định rằng mọi số trong workbook đều được generator tính mới. Cache Excel cũ có thể ảnh hưởng kết quả; tài liệu này mô tả đúng cách code đọc số tại thời điểm đối chiếu.

### 18.3. Xử lý thiếu dữ liệu và lỗi cấu hình

- Thiếu file/section/sheet/header/số liệu theo các exception nghiệp vụ mà handler bắt được: điền câu thiếu dữ liệu ở toàn block hoặc từng phần theo mô tả trên; bổ sung cảnh báo ở cuối output dưới câu `Lưu ý: Báo cáo còn thiếu dữ liệu.`
- Riêng COPY_SECTION giữ được phần nào hợp lệ khi block có nhiều nguồn; các mapping mặc định trong tài liệu hiện chỉ có một nguồn mỗi block loại này.
- Một số vùng cha trong template bao nhiều bookmark con. Khi thay vùng cha bằng câu fallback, các token con cũ bị loại khỏi phần nội dung bị thay.
- Thiếu mapping bắt buộc, sai role, sai section, trùng sort_order/target, thiếu bookmark template hoặc lỗi render/lưu file là lỗi cấu hình/thực thi; có thể làm cả tác vụ thất bại với `Không thể tạo báo cáo Word`, không tự biến mọi lỗi thành số 0.
- Generator có bước dọn các vùng chưa cấu hình sau: BM_SYSTEM_INCIDENT, BM_STATION_OVERVIEW, BM_STATION_INCIDENT, BM_PROGRAM_EVENT, BM_CUSTOMER_REACTION, BM_PRODUCT_ISSUE, BM_MOBILE, BM_CLM, BM_TV360, BM_SME, BM_FIXED. Các vùng đó được thay bằng `Chưa có dữ liệu` và có cảnh báo. BM_INCIDENT và ba bookmark biểu đồ không thuộc danh sách fallback khi block hoàn toàn không được cấu hình.

## 19. Cấu hình và mã nguồn để đối soát

| Nội dung | File |
|---|---|
| Tên definition nguồn | [report-phase1-master.sql](report-phase1-master.sql) |
| Đủ 15 block và mapping mặc định | [report-phase5-word-config.sql](report-phase5-word-config.sql) |
| Mapping nhiều nguồn/năm và quy ước sort_order | [report-phase5-word-source-maps.sql](report-phase5-word-source-maps.sql) |
| Cắt hai phần chương trình/chính sách | [report-phase5-program-reaction-word.sql](report-phase5-program-reaction-word.sql) |
| Cáp TH, top tỉnh và biểu đồ | [report-phase5-cable-incident-excel.sql](report-phase5-cable-incident-excel.sql) |
| Mobile | [report-phase5-mobile-feedback-excel.sql](report-phase5-mobile-feedback-excel.sql) |
| CLM | [report-phase5-network-quality-excel.sql](report-phase5-network-quality-excel.sql) |
| Fixed | [report-phase5-fixed-feedback-excel.sql](report-phase5-fixed-feedback-excel.sql) |
| TV360 | [report-phase5-tv360-feedback-excel.sql](report-phase5-tv360-feedback-excel.sql) |
| SME | [report-phase5-sme-feedback-word.sql](report-phase5-sme-feedback-word.sql) |
| Bất cập | [report-phase5-product-issue-word.sql](report-phase5-product-issue-word.sql) |
| Danh mục sheet import | [weekly-report-excel-validation.md](weekly-report-excel-validation.md) |
| Điều phối điền toàn bộ bookmark | [WeeklyWordOutputGenerator.java](../src/main/java/com/techasians/alertservice/service/impl/WeeklyWordOutputGenerator.java) |

Các section cũ WORD_MOBILE/WORD_CLM/WORD_FIXED/WORD_TV360/WORD_STATION_CABLE hoặc section diễn giải KPI/tiến độ có thể vẫn tồn tại trong SQL/database, nhưng **không đồng nghĩa được handler hiện tại sử dụng**. Cần đối soát bằng mapping active và nhánh xử lý cuối cùng, không chỉ nhìn danh sách section.

Để kiểm tra DB đang chạy, có thể dùng truy vấn chỉ đọc sau; tài liệu này không thực thi truy vấn lên DB:

```sql
SELECT b.block_code, b.target_name, b.render_type, b.sort_order AS block_order,
       m.source_definition_id, d.name AS source_name, m.source_role,
       m.sort_order AS source_order, m.source_sheet_name,
       s.id AS section_id, s.extract_mode, s.start_marker, s.end_marker,
       s.include_start, s.include_end
FROM rpt_output_block_defs b
JOIN rpt_output_source_maps m ON m.output_block_id = b.id AND m.status = 1
LEFT JOIN rpt_definitions d ON d.id = m.source_definition_id
LEFT JOIN rpt_section_defs s ON s.id = m.source_section_def_id
WHERE b.output_definition_id = 2001 AND b.status = 1
ORDER BY b.sort_order, m.sort_order;
```

## 20. Kiểm tra độ bao phủ của tài liệu

- Đối chiếu trực tiếp `word/document.xml` bên trong template: **103 bookmark BM_**, tất cả đều được nhắc và giải thích theo nhóm trong tài liệu này.
- Bao phủ **15 block nội dung** của cấu hình output 2001 và **3 bookmark kỳ báo cáo**.
- Đã kiểm tra các đường dẫn Markdown tới code, SQL và file mẫu đều tồn tại trong workspace tại thời điểm đối chiếu.
- Phạm vi xác minh là template, mã nguồn và các script trong repository; chưa truy vấn DB hoặc chạy sinh báo cáo để khẳng định cấu hình/số liệu của một tác vụ thực tế. Thay đổi của tác vụ này chỉ là bổ sung tài liệu Markdown.
