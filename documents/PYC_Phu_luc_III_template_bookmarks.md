# Template Word Phụ lục III

Tài liệu: `PYC_Phu_luc_III_template_bookmarks.docx`.
Bản dùng trong ứng dụng: `src/main/resources/templates/phu-luc-3-template.docx`.

Khung ban đầu giữ tiêu đề, dòng dẫn chiếu báo cáo, phần ký tên, khổ giấy A4,
lề và phông chữ từ tài liệu gốc. Các số liệu, biểu đồ, bảng và ghi chú của tuần
mẫu được thay bằng placeholder. Chưa cấu hình luồng sinh báo cáo Phụ lục III.

| Mục | Đầu mục | Bookmark nội dung |
| --- | --- | --- |
| 1 | Phản ánh Dịch vụ Di động | `BM_MOBILE` |
| 2 | Phản ánh Chất lượng mạng | `BM_CLM` |
| 3 | Phản ánh Dịch vụ Cố định | `BM_FIXED` |
| 3.1 | Tình hình tiếp nhận phản ánh | `BM_FIXED_RECEIVED` |
| 3.2 | Tiến độ xử lý | `BM_FIXED_PROCESSING` |
| 4 | Dịch vụ TV360 | `BM_TV360` |
| 5 | Dịch vụ SME | `BM_SME` |
| 6 | Thông tin thu thập trên mạng xã hội | `BM_SOCIAL_MEDIA` |
| 7 | Kết quả happycall (HPC) | `BM_HAPPYCALL` |
| 8 | Chương trình Viettel++ | `BM_VIETTEL_PLUS` |

Hai bookmark thông tin báo cáo: `BM_REPORT_NUMBER` chứa phần số trước
`/BC-VCX`; `BM_REPORT_DATE` chứa cả cụm `ngày [DD] tháng [MM] năm [YYYY]`.

Các đầu mục dùng đánh số tự động và cấp dàn ý tương ứng để hiện trong
Navigation Pane của Word. Bookmark nội dung nằm sau đầu mục để khi thay nội
dung vẫn giữ tiêu đề và số thứ tự.

Riêng `BM_FIXED` bao toàn bộ phần dưới mục 3, gồm cả tiêu đề và nội dung hai
mục 3.1, 3.2. Điền hai bookmark con khi cần giữ khung có sẵn; chỉ thay bookmark
cha khi muốn thay toàn bộ phần dịch vụ cố định (phải cung cấp lại hai tiêu đề
con nếu cần). Không thay đồng thời bookmark cha và các bookmark con trong cùng
lượt sinh báo cáo.
