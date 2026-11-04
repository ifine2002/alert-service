# Danh mục sheet/header phục vụ báo cáo tuần

Phạm vi: Word 2001, Phụ lục I Excel 2002 và Phụ lục II Excel 2003. Nguồn chuẩn là `weekly-report-excel-requirements.json`; SQL và tài liệu này sinh từ manifest bằng script trong `documents/scripts`.

## Quy tắc import và triển khai

- `{yyyy}` là năm của ngày kết thúc tuần; `{yyyy-1}` là năm trước đó. Tuần 28/12/2026–03/01/2027 dùng năm 2027 và 2026. Quy tắc dùng chung cho import, Word và hai phụ lục.
- Chạy `report-excel-header-name-migration.sql` một lần để bỏ `row_index`, `column_index`, sau đó `report-weekly-excel-validation-config.sql`. Chưa có thao tác nào tự chạy trên DB.
- Script metadata thay thế bộ sheet/header active trong các definition 1010, 1102, 1104, 1106, 1107, 1112. Bản ghi cũ ngoài danh mục chuyển status=0; không xóa bản ghi.
- Mỗi tên header phải khớp toàn bộ nội dung một ô sau khi chuẩn hóa Unicode NFC, hoa/thường và khoảng trắng/xuống dòng/NBSP. Không dùng contains và không ghép nhiều ô để tạo một tên.
- Quét toàn sheet, dừng khi đủ header. Các header được phép nằm khác dòng/cột hoặc trong ô gộp (giá trị nằm ở ô đầu). Tên lặp ở nhiều bảng/tuần chỉ cần có mặt; vị trí tương đối và kỳ báo cáo do reader kiểm tra tiếp.
- Danh mục `headers` gồm tên cột, nhãn bảng và nhãn dòng mà reader cần. Các tên động có ngày và các lựa chọn tên thay thế nằm ở phần ghi chú, không seed thành nhiều yêu cầu bắt buộc cùng lúc.
- Hai sheet năm trước được reader đọc có điều kiện khi cửa sổ 8 tuần hoặc tuần báo cáo đi qua năm trước. Vì DB chỉ có status, bộ seed mặc định yêu cầu cả hai sheet quanh năm để đủ nguồn.
- Mẫu di động là `.xlsm`; validator đã hỗ trợ khi `allowed_extension=xlsm`. Nếu DB vẫn cho phép xlsx, cần cấu hình đúng extension hoặc lưu nguồn thành xlsx; script header không tự thay chính sách extension.

## Sheet và tên bắt buộc để cấu hình DB

### Definition 1010 — `Bất cập DV`

- Output: 2001, 2003. Reader: `WordProductIssueReader`, `AppendixExcelOutputGenerator.copyFilteredAppendixTwo`.
- File mẫu: `Phu luc - TH BCDV_PAGY_2026.xlsx`.
- Tên bắt buộc: `Ngày phát hiện`, `Tiến độ`, `Nội dung`, `Kết quả`, `Dịch vụ`.
- Word lấy dòng đầu tiên trong tuần; Giải pháp lấy Kết quả. Phụ lục II còn lọc Dịch vụ. Tên bất cập/lỗi và các cột chi tiết xem danh sách cột sao chép bên dưới tài liệu.

### Definition 1102 — `TH`

- Output: 2001. Reader: `WordSystemIncidentReader.read`, `WordSystemIncidentReader.readStation`, `WordSystemIncidentReader.readCable`.
- File mẫu: `T36. Bao cao tuan 01.09 -07.09.xlsx`.
- Tên bắt buộc: `Tổng lỗi phát sinh`, `So sánh lỗi trạm`, `So sánh Sự cố đứt cáp, cháy cáp trục, cáp nhánh`, `Số lượng`, `PA`, `TB ảnh hưởng`, `Tỷ lệ tăng giảm`.
- Danh sách gồm cả nhãn bảng/dòng cần đọc. Hai header tuần có thể là Tuần này/Tuần trước hoặc khoảng ngày thực tế; chênh lệch có các tên thay thế. Reader kiểm tra cấu trúc và đúng kỳ.

### Definition 1102 — `Lũy kế lỗi phát sinh năm {yyyy}`

- Output: 2001, 2002. Reader: `WordIncidentChartReader.read`, `AppendixExcelOutputGenerator.copyFilteredByOwner`.
- File mẫu: `T36. Bao cao tuan 01.09 -07.09.xlsx`.
- Tên bắt buộc: `Năm`, `Tháng`, `Ngày`, `Chủ dịch vụ`.
- Biểu đồ 8 tuần và phụ lục I lọc Chủ dịch vụ = VTT. Cột PA bổ sung reader tìm theo tên Số lượng phản ảnh; mẫu dùng Số lượng phản ánh, xem ghi chú chênh lệch trong tài liệu.
- **File mẫu hiện tại thiếu: `Chủ dịch vụ`. Phải bổ sung header trong file để import đạt bộ cấu hình này.**

### Definition 1102 — `LPS năm {yyyy-1}`

- Output: 2001. Reader: `WordIncidentChartReader.read`.
- File mẫu: `T36. Bao cao tuan 01.09 -07.09.xlsx`.
- Tên bắt buộc: `Tháng`, `Ngày`, `Chủ dịch vụ`.
- Reader chỉ cần khi cửa sổ 8 tuần đi qua năm trước. Không bắt buộc cột Năm; giá trị Tháng cần dạng Tháng M.yyyy. Metadata mặc định yêu cầu sheet này cả năm để đủ bộ nguồn.

### Definition 1102 — `Lũy kế trạm năm {yyyy}`

- Output: 2001, 2002. Reader: `WordIncidentChartReader.readStation`, `WordIncidentChartReader.readCable`, `WordIncidentChartReader.readTopCableProvinces`, `AppendixExcelOutputGenerator.copyAllDataRows`.
- File mẫu: `T36. Bao cao tuan 01.09 -07.09.xlsx`.
- Tên bắt buộc: `Năm`, `Tháng`, `Ngày`, `Tổng PA`, `Tổng ảnh hưởng`, `Nguyên nhân cấp 1`, `Tỉnh`.
- Header hai tầng. Nguyên nhân cấp 1 dùng lọc Cháy cáp/Đứt cáp; Tỉnh dùng top tỉnh. Các cột chi tiết phụ lục I có trong danh mục cột sao chép.

### Definition 1102 — `Tổng trạm {yyyy-1}`

- Output: 2001. Reader: `WordIncidentChartReader.readStation`, `WordIncidentChartReader.readCable`, `WordIncidentChartReader.readTopCableProvinces`.
- File mẫu: `T36. Bao cao tuan 01.09 -07.09.xlsx`.
- Tên bắt buộc: `Năm`, `Tháng`, `Ngày`, `Tổng PA`, `Tổng ảnh hưởng`, `Nguyên nhân cấp 1`, `Tỉnh`.
- Dùng khi 8 tuần hoặc tuần báo cáo vắt năm. Metadata mặc định yêu cầu cả năm để đủ bộ nguồn.

### Definition 1102 — `LPS tuần`

- Output: 2002. Reader: `AppendixExcelOutputGenerator.copyFilteredByOwner`.
- File mẫu: `T36. Bao cao tuan 01.09 -07.09.xlsx`.
- Tên bắt buộc: `Chủ dịch vụ`.
- Header bắt buộc để tìm hàng tiêu đề và lọc VTT. Cột STT ở output được tự đánh lại. Cần các cột chi tiết của template nếu muốn xuất đủ dữ liệu.

### Definition 1102 — `Trạm tuần`

- Output: 2002. Reader: `AppendixExcelOutputGenerator.copyAllDataRows`.
- File mẫu: `T36. Bao cao tuan 01.09 -07.09.xlsx`.
- Tên bắt buộc: `Năm`, `Tháng`, `Ngày`, `Mã trạm`, `Tỉnh`.
- Các tên nhận diện cấu trúc nguồn. Reader ánh xạ các cột còn lại theo toàn bộ tên header của template; xem danh mục chi tiết.

### Definition 1102 — `Đứt cáp cháy cáp`

- Output: 2002. Reader: `AppendixExcelOutputGenerator.copyAllDataRows`.
- File mẫu: `T36. Bao cao tuan 01.09 -07.09.xlsx`.
- Tên bắt buộc: `Năm`, `Tháng`, `Ngày`, `Mã trạm`, `Tỉnh`.
- Các tên nhận diện cấu trúc nguồn. Không yêu cầu STT ở nguồn vì output tự đánh lại. Xem các cột chi tiết phụ lục I.

### Definition 1104 — `PL02-So lieu TN&XL`

- Output: 2001. Reader: `WordMobileFeedbackReader`.
- File mẫu: `PHU_LUC_BAO_CAO_TUAN_DICH_VU_DI_DONG_07092026.xlsm`.
- Tên bắt buộc: `STT`, `Dịch vụ`, `Tăng giảm`, `Tỷ lệ`, `Phản ánh báo hỏng dịch vụ`.
- Header TB tuần (...) chứa ngày động; reader tự kiểm tra tuần. File mẫu là xlsm; allowed_extension của definition cần phù hợp định dạng file thực tế hoặc lưu lại mẫu thành xlsx.

### Definition 1106 — `N.Xet tuan`

- Output: 2001. Reader: `WordFixedFeedbackReader`.
- File mẫu: `PL CDBR TUAN 01.09-07.09.xlsx`.
- Tên bắt buộc: `Cùng kỳ tuần trước`, `HTDV + SỰ CỐ tiếp nhận chuyển BO`, `HTDV + SỰ CỐ tiếp nhận chuyển KT`, `SỰ CỐ tiếp nhận chuyển KT`, `TLPA/10K`, `FTTH GPON`, `THS`.
- Đây là tên chuẩn của mẫu; reader còn chấp nhận một số biến thể bỏ tiếp nhận, khoảng trắng, THS/Truyền hình số. Nhóm ngày tuần hiện tại/trước được reader kiểm tra riêng.

### Definition 1106 — `TH`

- Output: 2001. Reader: `WordFixedFeedbackReader.kpi`.
- File mẫu: `PL CDBR TUAN 01.09-07.09.xlsx`.
- Tên bắt buộc: `Chỉ tiêu`, `KPI`, `FTTH_Gpon`, `Truyền hình số`.
- FTTH_Gpon là tên literal ở mẫu; reader chuẩn hóa _ thành khoảng trắng khi tìm dịch vụ.

### Definition 1107 — `Tỷ lệ PA tuần`

- Output: 2001. Reader: `WordNetworkQualityReader`.
- File mẫu: `PL_CLM `.
- Tên bắt buộc: `Tỷ lệ chung`, `Chỉ tiêu thoại`, `Chỉ tiêu Data`, `PA`, `Thuê bao`, `KQ`, `KPIs`, `Đánh giá`, `Nguồn tiếp nhận`, `TOP 5 Tỉnh Thoại tăng cao nhất Tuần`, `TOP 5 Tỉnh Data tăng cao nhất Tuần`, `Tỉnh`, `Tuần hiện tại`.
- Nhóm header Tuần Từ ... đến ... là ngày động. Cùng tên PA/Thuê bao/KQ lặp theo tuần là hợp lệ; reader xác định nhóm cột bằng kỳ báo cáo.

### Definition 1112 — `NDPA`

- Output: 2001. Reader: `WordTv360FeedbackReader`.
- File mẫu: `PL TV360 TUAN 01.09-07.09.xlsx`.
- Tên bắt buộc: `STT`, `Nội dung phản ánh`, `TB ngày`, `Tỷ trọng ngày`, `DỊCH VỤ TV360`.
- Khoảng ngày tuần hiện tại/trước là header động và phải gộp nhóm cột. Reader kiểm tra quan hệ này.

### Definition 1112 — `NNLOI`

- Output: 2001. Reader: `WordTv360FeedbackReader`.
- File mẫu: `PL TV360 TUAN 01.09-07.09.xlsx`.
- Tên bắt buộc: `STT`, `Nguyên nhân lỗi`, `TB ngày`, `Tỷ trọng ngày`, `DỊCH VỤ TV360`, `Hỗ trợ khách hàng`, `Lỗi phía khách hàng`.
- Hai nhóm nguyên nhân là nhãn dòng dữ liệu cần đọc. Khoảng ngày tuần do reader kiểm tra riêng.

## Các header động/điều kiện reader còn kiểm tra

| Reader | Header/điều kiện |
|---|---|
| WordSystemIncidentReader | Tuần này/Tuần trước hoặc khoảng ngày tuần hiện tại/trước; So sánh/Chênh lệch; bảng trạm/cáp có thể dùng hai cột Tỷ lệ tăng giảm. Phải đúng thứ tự/nhóm bảng. |
| WordIncidentChartReader | Năm/Tháng/Ngày nằm cùng hàng ở bảng hệ thống; bảng trạm/cáp có thể chia hai tầng. Dữ liệu năm trước của bảng hệ thống có Tháng M.yyyy. Cột Số lượng phản ảnh dùng tính PA nếu có. |
| WordMobileFeedbackReader | TB tuần (d/M-d/M) có thể có năm; cần đúng tuần báo cáo. STT/Dịch vụ có thể ở hàng phía trên. Dòng Phản ánh báo hỏng dịch vụ phải ngay dưới header kỳ báo cáo. |
| WordNetworkQualityReader | Tuần Từ d.M đến d.M hoặc dấu /, dấu phẩy, có thể có năm; các cột PA/Thuê bao/KQ phải thuộc đúng nhóm tuần. Có hai bảng top tỉnh riêng. |
| WordFixedFeedbackReader | Khoảng tuần dạng d/M-d/M hoặc d.M-d.M, tùy chọn năm và tiền tố Tuần/Tuần từ. Chấp nhận biến thể nhãn bỏ tiếp nhận, khoảng trắng, FTTH_Gpon/FTTH GPON và THS/Truyền hình số. DB hiện dùng đúng literal trong mẫu. |
| WordTv360FeedbackReader | Nhóm d/M-d/M hoặc d/M-d/M/yyyy phải gộp cột, dưới nhóm có TB ngày/Tỷ trọng ngày. Hai sheet NDPA/NNLOI cần đúng nhóm tuần. |

## Toàn bộ tên cột được hai phụ lục Excel ánh xạ

Các tên dưới đây lấy trực tiếp từ hai template đang dùng. Chúng là cột chi tiết cần có nếu muốn điền đủ phụ lục; không phải tất cả đều đang bắt buộc trong reader. Reader cho phép cột nguồn không ánh xạ được để trống. STT được đánh lại, không bắt buộc có ở nguồn. Header nhiều tầng được ánh xạ theo chuỗi cha > con, sau đó thử tên con duy nhất. Validator tên chỉ kiểm tra sự hiện diện, không chứng minh quan hệ cha/con.

### `LPS tuần {dd-dd.MM}` — phu-luc-1-template.xlsx

`STT`, `Tháng`, `Ngày`, `Dịch vụ`, `Hệ thống`, `Khách hàng`, `Nghiệp vụ`, `Đơn vị vận hành`, `Đơn vị chịu trách nhiệm`, `Tên lỗi`, `Hiện tượng`, `Số lượng phản ánh`, `Ảnh hưởng thực`, `Thời gian bắt đầu lỗi`, `Thời gian kết thúc lỗi`, `Nguyên nhân lỗi`, `Hành động khắc phục`, `Tính chất lỗi`, `Mức độ nghiêm trọng (Nhỏ/Trung bình/Lớn/Nghiêm trọng)`.

### `LPS năm {yyyy}` — phu-luc-1-template.xlsx

`STT`, `Tháng`, `Ngày`, `Dịch vụ (Cố định, Di động, SME, Giải pháp, Tài chính ngân hàng, Đa dịch vụ, Hệ thống CNTT nội bộ)*TK`, `Hệ thống cấp 1 (CNTT hoặc Viễn thông)*TK`, `Hệ thống cấp 2 (KHDN, KH cá nhân, KH nội bộ)*TK`, `Hệ thống cấp 3 (CLM, FTTH, Data, BCCS, CA,…)`, `Đơn vị vận hành`, `Đơn vị chịu trách nhiệm`, `Tên lỗi`, `Hiện tượng`, `Số lượng phản ánh`, `Ảnh hưởng thực`, `Thời gian bắt đầu lỗi`, `Thời gian kết thúc lỗi`, `Nguyên nhân lỗi`, `Hành động khắc phục`, `Tính chất lỗi`, `Mức độ nghiêm trọng (Nhỏ/Trung bình/Lớn/Nghiêm trọng)`.

### `Lỗi trạm tuần {dd-dd.MM}` — phu-luc-1-template.xlsx

`STT`, `Tháng`, `Ngày`, `Khu Vực`, `Mã trạm`, `Số lượng tuyến cáp`, `Tên tuyến cáp`, `Tỉnh`, `MÔ TẢ LỖI`, `PHẢN ÁNH`, `TB thực bị ảnh hưởng`, `CSKH chủ động`, `Khu vực tập trung`, `Thời gian phát sinh`, `Tổng thời gian gián đoạn (phút)`, `Kết quả OK/NOK`, `NGUYÊN NHÂN`, `HƯỚNG XỬ LÝ`, `Đầu mối báo lỗi`, `Đơn vị chịu tránh nhiệm`, `ĐƠN VỊ BÁO LỖI (TỈNH/CC)`, `CÔNG VĂN CỦA ĐIỆN LỰC (CÓ/KHÔNG)`, `Tính chất lỗi`, `Trục/Nhánh/Thuê bao`, `Mức độ lỗi`, `Chuyển P.CSKH HPC`, `KH DN`, `KQ HPC (P.CSKH)`, `KLL`, `Số sai`, `Trùng`, `Đối tác`, `Tòa nhà/KĐT`, `c`, `Thời gian XL (giờ)`, `ADSL`, `FTTH`, `PSTN`, `NextTV`, `NGN`, `THC/THS/ EOC/OW`, `Kênh trắng`, `Tổng tiếp nhận`, `Đã XL xong`, `TB thực bị a/h`, `Nhắn tin nội mạng`, `Nhắn tin ngoại mạng`, `Nhắn không thành công`, `KH PRI`, `OK`.

### `Đứt cáp cháy cáp tuần` — phu-luc-1-template.xlsx

`STT`, `Tháng`, `Ngày`, `Khu Vực`, `Mã trạm`, `Số lượng tuyến cáp`, `Tên tuyến cáp`, `Tỉnh`, `MÔ TẢ LỖI`, `PHẢN ÁNH`, `TB thực bị ảnh hưởng`, `CSKH chủ động`, `Khu vực tập trung`, `Thời gian phát sinh`, `Tổng thời gian gián đoạn (phút)`, `Kết quả OK/NOK`, `NGUYÊN NHÂN`, `HƯỚNG XỬ LÝ`, `Đầu mối báo lỗi`, `Đơn vị chịu tránh nhiệm`, `ĐƠN VỊ BÁO LỖI (TỈNH/CC)`, `CÔNG VĂN CỦA ĐIỆN LỰC (CÓ/KHÔNG)`, `Tính chất lỗi`, `Trục/Nhánh/Thuê bao`, `Mức độ lỗi`, `Chuyển P.CSKH HPC`, `KH DN`, `KQ HPC (P.CSKH)`, `Đối tác`, `Tòa nhà/KĐT`, `c`, `Thời gian XL (giờ)`, `ADSL`, `FTTH`, `PSTN`, `NextTV`, `NGN`, `THC/THS/ EOC/OW`, `Kênh trắng`, `TB thực bị a/h`, `Đã XL xong`, `Nhắn tin nội mạng`, `Nhắn tin ngoại mạng`, `Nhắn không thành công`, `KH PRI`, `OK`, `KLL`, `Số sai`, `Trùng`.

### `Lỗi trạm năm {yyyy}` — phu-luc-1-template.xlsx

`STT`, `Tháng`, `Ngày`, `Khu Vực`, `Mã trạm`, `Số lượng tuyến cáp`, `Tên tuyến cáp`, `Tỉnh`, `Hiện tượng lỗi`, `PHẢN ÁNH`, `TB thực bị ảnh hưởng`, `CSKH chủ động`, `Khu vực tập trung`, `Thời gian phát sinh`, `Tổng thời gian gián đoạn (phút)`, `Kết quả OK/NOK`, `NGUYÊN NHÂN`, `HƯỚNG XỬ LÝ`, `Đầu mối báo lỗi`, `Đơn vị chịu tránh nhiệm`, `ĐƠN VỊ BÁO LỖI (TỈNH/CC)`, `CÔNG VĂN CỦA ĐIỆN LỰC (CÓ/KHÔNG)`, `Nguyên nhân cấp 1`, `Trục/Nhánh/Thuê bao`, `Chi tiết Nguyên nhân`, `Chuyển P.CSKH HPC`, `Chưa có File KHDN`, `KQ HPC (P.CSKH)`, `Cộng Data`, `Ghi chú`, `ADSL`, `FTTH`, `PSTN`, `NextTV`, `NGN`, `THC/THS/ EOC/OW`, `Kênh trắng`, `Tổng PA`, `Đã XL`, `Tổng ảnh hưởng`, `Nhắn tin nội mạng`, `Ngoại mạng`, `Nhắn không thành công`, `OK*`, `KLL*`, `Số sai*`, `Trùng*`, `Tổng cộng Data`, `OK Data`, `NOK Data`.

### `Bất cập DV` — phu-luc-2-template.xlsx

`STT`, `Dịch vụ`, `Vấn đề bất cập`, `Hiện trạng`, `Kết quả`, `SLPA/AH`, `Tiến độ`, `Thời gian hoàn thành /Dự kiến hoàn thành`, `Ngày phát hiện`, `Đơn vị chịu trách nhiệm`, `Đơn vị phát hiện`, `Phân loại`.

## Các khác biệt đã xác nhận trong cấu hình/mẫu

- Phụ lục II trước đây trỏ source/target Bất cập, trong khi dữ liệu 1010 và template output đều là Bất cập DV. SQL metadata đã đồng bộ hai mapping về Bất cập DV.
- Hai mapping nguồn lũy kế của phụ lục I trước đây cố định năm 2026; SQL đã chuyển thành `{yyyy}` để có tác dụng khi qua năm.
- Mẫu 1102 đang thiếu header Chủ dịch vụ ở sheet Lũy kế lỗi phát sinh năm 2026; reader biểu đồ và phụ lục I đều cần để lọc VTT. Manifest giữ yêu cầu này và fixture test xác nhận lỗi thiếu header, không bỏ qua yêu cầu.
- WordIncidentChartReader đang tìm tên bổ sung Số lượng phản ảnh, trong khi mẫu dùng Số lượng phản ánh. Đây không phải một header bắt buộc trong reader, vì vậy không ép tên sai vào seed. Cần thống nhất tên nếu muốn biểu đồ lấy đủ tổng PA từ cột này.
- Một số cột template phụ lục II là Vấn đề bất cập/Hiện trạng/Đơn vị phát hiện/Phân loại, còn nguồn 1010 là Tên bất cập/lỗi/Nội dung/BO/PAGY/PL ND khắc phục. Reader hiện không có bảng alias tên này; các cột khác tên có thể để trống. Danh mục giữ nguyên cả tên bắt buộc nguồn và tên output để người cấu hình đối chiếu.
- Nguồn Word 1101, 1119, 1108 dùng section/bookmark và không có sheet/header Excel. Nguồn 1122 là cấu hình cũ, không được các mapping phase4/phase5 hiện tại dùng cho bộ ba output này.
