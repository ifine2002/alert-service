from docx import Document
from docx.shared import Pt, Inches
from docx.enum.text import WD_ALIGN_PARAGRAPH
from docx.oxml.ns import nsdecls
from docx.oxml import parse_xml

def set_cell_background(cell, color):
    shading_elm = parse_xml(r'<w:shd {} w:fill="{}"/>'.format(nsdecls('w'), color))
    cell._tc.get_or_add_tcPr().append(shading_elm)

doc = Document()

# Add Title
title = doc.add_heading('TÀI LIỆU ĐẶC TẢ API CHI TIẾT - ALERT CONFIG', 0)
title.alignment = WD_ALIGN_PARAGRAPH.CENTER

apis = [
    {
        "name": "API Lấy danh sách cấu hình cảnh báo",
        "url": "http://{{host}}:{{port}}/api/alert-configs?page=0&size=10",
        "method": "GET",
        "content_type": "N/A",
        "input": [],
        "query_params": [
            ("1", "categoryId", "Integer", "ID danh mục loại chỉ số", "Không"),
            ("2", "metricName", "String", "Tên chỉ số để tìm kiếm gần đúng", "Không"),
            ("3", "createdBy", "String", "Người tạo để tìm kiếm", "Không"),
            ("4", "page", "Integer", "Trang hiện tại (mặc định 0)", "Không"),
            ("5", "size", "Integer", "Số lượng bản ghi/trang (mặc định 10)", "Không")
        ],
        "output": [
            ("1", "code", "Integer", "Mã trạng thái (ví dụ 200)"),
            ("2", "success", "Boolean", "Giá trị true nếu thành công"),
            ("3", "message", "String", "Thông báo chi tiết"),
            ("4", "data", "Object", "Dữ liệu trả về (Page<AlertConfigResponse>)")
        ],
        "data_fields": [
            ("1", "content", "List<AlertConfigResponse>", "Danh sách bản ghi cấu hình"),
            ("2", "totalElements", "Long", "Tổng số bản ghi"),
            ("3", "totalPages", "Integer", "Tổng số trang")
        ],
        "logic": [
            ("1", "Hệ thống tiếp nhận Request với các tham số tìm kiếm."),
            ("2", "Làm sạch ký tự đặc biệt cho câu truy vấn LIKE (Escape '_' '%')."),
            ("3", "Truy vấn vào CSDL bảng AlertConfig với điều kiện deleted=false và các filter (categoryId, metricName, createdBy)."),
            ("4", "Chuyển đổi danh sách Entity trả về sang DTO (AlertConfigResponse)."),
            ("5", "Kết thúc xử lý. Trả về kết quả phân trang (Page).")
        ]
    },
    {
        "name": "API Thêm mới cấu hình cảnh báo",
        "url": "http://{{host}}:{{port}}/api/alert-configs",
        "method": "POST",
        "content_type": "application/json",
        "input": [
            ("1", "name", "String", "Tên cấu hình cảnh báo (giới hạn tối đa 255 ký tự)", "Có"),
            ("2", "categoryId", "Integer", "ID của Loại chỉ số", "Có"),
            ("3", "metricId", "Integer", "ID của Chỉ số chi tiết", "Có"),
            ("4", "status", "Integer", "Trạng thái (1: Hoạt động, 0: Vô hiệu hóa)", "Không"),
            ("5", "levels", "List<Object>", "Danh sách các mức cảnh báo", "Có")
        ],
        "levels_fields": [
            ("1", "levelNumber", "Integer", "Cấp độ cảnh báo (1, 2, 3...)", "Có"),
            ("2", "conditionOperator", "Integer", "Phép so sánh (1: <, 2: <=, 3: ==, 4: >, 5: >=, 6: Trong khoảng)", "Có"),
            ("3", "thresholdValue", "BigDecimal", "Giá trị ngưỡng (hoặc giá trị ngưỡng Từ)", "Có"),
            ("4", "thresholdValueTo", "BigDecimal", "Giá trị ngưỡng Đến (bắt buộc nếu phép so sánh là Trong khoảng)", "Tùy thuộc"),
            ("5", "channelIds", "List<Integer>", "Danh sách ID kênh nhận thông báo", "Không"),
            ("6", "groupIds", "List<Integer>", "Danh sách ID nhóm đầu mối nhận", "Không")
        ],
        "output": [
            ("1", "code", "Integer", "Mã trạng thái phản hồi bên trong JSON (Giá trị là 201 hoặc 200)."),
            ("2", "success", "Boolean", "Có giá trị true để xác nhận thêm mới thành công."),
            ("3", "message", "String", "Thông báo chi tiết."),
            ("4", "data", "Integer", "Dữ liệu trả về chính là ID của cấu hình vừa tạo.")
        ],
        "data_fields": [],
        "logic": [
            ("1", "Hệ thống validate định dạng các trường trong Request Body (Không được để trống, các ràng buộc độ dài). Nếu sai, ném ra lỗi Bad Request."),
            ("2", "Kiểm tra xem Cấu hình cảnh báo cho Category và Metric đã tồn tại chưa. Nếu có rồi, ném ra lỗi DuplicateResourceException."),
            ("3", "Kiểm tra sự tồn tại của Category và Metric trong CSDL. Nếu không tìm thấy, ném ra lỗi ResourceNotFoundException."),
            ("4", "Validate danh sách mức cảnh báo: kiểm tra levelNumber không được trùng lặp."),
            ("5", "Validate conditionOperator: nếu bằng 6 (Trong khoảng) thì thresholdValueTo bắt buộc phải có, và thresholdValue phải nhỏ hơn thresholdValueTo."),
            ("6", "Khởi tạo thực thể AlertConfig mới và lưu vào CSDL."),
            ("7", "Khởi tạo các thực thể AlertLevel, AlertLevelChannel, AlertLevelRecipient tương ứng từ Request và lưu hàng loạt vào DB."),
            ("8", "Lưu log lịch sử (AlertConfigHistory) với hành động Tạo mới."),
            ("9", "Kết thúc xử lý. Trả về ID của cấu hình vừa tạo.")
        ]
    },
    {
        "name": "API Cập nhật cấu hình cảnh báo",
        "url": "http://{{host}}:{{port}}/api/alert-configs/{id}",
        "method": "PUT",
        "content_type": "application/json",
        "input": [
            ("1", "name", "String", "Tên cấu hình cảnh báo", "Có"),
            ("2", "categoryId", "Integer", "ID của Loại chỉ số", "Có"),
            ("3", "metricId", "Integer", "ID của Chỉ số chi tiết", "Có"),
            ("4", "status", "Integer", "Trạng thái", "Có"),
            ("5", "levels", "List<Object>", "Danh sách các mức cảnh báo", "Có")
        ],
        "output": [
            ("1", "code", "Integer", "Mã trạng thái"),
            ("2", "success", "Boolean", "Giá trị true"),
            ("3", "message", "String", "Thông báo chi tiết"),
            ("4", "data", "Integer", "ID của cấu hình được cập nhật.")
        ],
        "data_fields": [],
        "logic": [
            ("1", "Hệ thống tìm kiếm AlertConfig theo ID. Ném lỗi nếu không tìm thấy."),
            ("2", "Lấy dữ liệu snapshot trước khi cập nhật để lưu log."),
            ("3", "Kiểm tra nếu categoryId hoặc metricId thay đổi, hệ thống kiểm tra tính duy nhất. Ném lỗi nếu bị trùng cấu hình đã có."),
            ("4", "Cập nhật các thông tin cơ bản của AlertConfig và lưu vào DB."),
            ("5", "Áp dụng cơ chế Clear & Rebuild: Xóa toàn bộ AlertLevelChannel, AlertLevelRecipient, AlertLevel cũ theo AlertConfig ID."),
            ("6", "Tạo lại mới hoàn toàn các levels, channels, recipients từ Request Body và lưu DB."),
            ("7", "So sánh trạng thái trước/sau để xác định loại hành động (Cập nhật, Kích hoạt, Vô hiệu hóa) và lưu log lịch sử."),
            ("8", "Trả về kết quả ResponseResult.")
        ]
    },
    {
        "name": "API Xóa cấu hình cảnh báo",
        "url": "http://{{host}}:{{port}}/api/alert-configs/{id}",
        "method": "DELETE",
        "content_type": "N/A",
        "input": [],
        "output": [
            ("1", "code", "Integer", "Mã trạng thái"),
            ("2", "success", "Boolean", "Giá trị true"),
            ("3", "message", "String", "Thông báo chi tiết"),
            ("4", "data", "Object", "Null")
        ],
        "data_fields": [],
        "logic": [
            ("1", "Tìm kiếm AlertConfig theo ID. Ném lỗi nếu không tìm thấy."),
            ("2", "Lấy dữ liệu snapshot trước khi xóa để lưu log."),
            ("3", "Cập nhật cờ deleted = true cho cấu hình cảnh báo (Xóa mềm). Không đụng đến các bảng con."),
            ("4", "Lưu thay đổi vào CSDL."),
            ("5", "Lưu log lịch sử (Hành động Xóa)."),
            ("6", "Trả về kết quả ResponseResult.")
        ]
    },
    {
        "name": "API Lấy chi tiết cấu hình cảnh báo",
        "url": "http://{{host}}:{{port}}/api/alert-configs/{id}",
        "method": "GET",
        "content_type": "N/A",
        "input": [],
        "output": [
            ("1", "code", "Integer", "Mã trạng thái"),
            ("2", "success", "Boolean", "Giá trị true"),
            ("3", "message", "String", "Thông báo chi tiết"),
            ("4", "data", "Object", "Đối tượng AlertConfigDetailResponse")
        ],
        "data_fields": [
            ("1", "id", "Integer", "ID cấu hình"),
            ("2", "name", "String", "Tên cấu hình"),
            ("3", "categoryId", "Integer", "ID loại chỉ số"),
            ("4", "categoryName", "String", "Tên loại chỉ số"),
            ("5", "metricId", "Integer", "ID chỉ số"),
            ("6", "metricName", "String", "Tên chỉ số"),
            ("7", "status", "Integer", "Trạng thái"),
            ("8", "levels", "List<Object>", "Danh sách các mức cảnh báo và chi tiết recipients/channels")
        ],
        "logic": [
            ("1", "Tìm AlertConfig theo ID trong DB. Nếu không có, ném lỗi ResourceNotFoundException."),
            ("2", "Query lấy tất cả các AlertLevel thuộc cấu hình này."),
            ("3", "Truy vấn 1 lần (để tránh N+1 Query) lấy tất cả Channels và Recipients của tất cả các Levels."),
            ("4", "Nhóm (Group by) các Channels, Recipients theo từng AlertLevel ID bằng RAM (bộ nhớ)."),
            ("5", "Ánh xạ (Mapping) dữ liệu Entity sang DTO AlertConfigDetailResponse hoàn chỉnh."),
            ("6", "Trả về kết quả ResponseResult.")
        ]
    },
    {
        "name": "API Download file template import Excel rỗng",
        "url": "http://{{host}}:{{port}}/api/alert-configs/import/template",
        "method": "GET",
        "content_type": "N/A",
        "input": [],
        "output": [
            ("1", "Content-Disposition", "Header", "Tên file Excel: template_import_canh_bao.xlsx"),
            ("2", "File Bytes", "byte[]", "Dữ liệu nhị phân của file Excel.")
        ],
        "data_fields": [],
        "logic": [
            ("1", "Khởi tạo Workbook rỗng bằng Apache POI (XSSFWorkbook)."),
            ("2", "Tạo sheet có tên 'Template Import'."),
            ("3", "Tạo dòng đầu tiên (header) với 9 cột chuẩn: Loại chỉ số, Tên chỉ số, Mức độ cảnh báo..."),
            ("4", "Ghi Workbook ra ByteArrayOutputStream."),
            ("5", "Cấu hình HTTP Headers Content-Disposition là attachment."),
            ("6", "Trả về file nhị phân.")
        ]
    },
    {
        "name": "API Upload file Excel để import cấu hình cảnh báo",
        "url": "http://{{host}}:{{port}}/api/alert-configs/import",
        "method": "POST",
        "content_type": "multipart/form-data",
        "input": [
            ("1", "file", "MultipartFile", "File Excel định dạng .xlsx", "Có")
        ],
        "output": [
            ("1", "code", "Integer", "Mã trạng thái"),
            ("2", "success", "Boolean", "Trạng thái thành công/thất bại"),
            ("3", "message", "String", "Thông báo chi tiết"),
            ("4", "File Excel lỗi", "byte[]", "NẾU CÓ LỖI, API sẽ trả trực tiếp file Excel kết quả, ngược lại trả về JSON thành công.")
        ],
        "data_fields": [],
        "logic": [
            ("1", "Kiểm tra đuôi file có phải .xlsx không. Nếu sai, ném lỗi IllegalArgumentException."),
            ("2", "Đọc sheet đầu tiên của file Excel. Xác thực format Header phải chuẩn 9 cột."),
            ("3", "Đọc tất cả các dòng dữ liệu, convert dữ liệu thô sang ImportAlertConfigRowDTO. Parse các phép toán, giá trị ngưỡng."),
            ("4", "Pre-load (Tải sẵn) toàn bộ dữ liệu categories, metrics, channels, groups từ CSDL lên bộ nhớ RAM để tránh query DB liên tục."),
            ("5", "Duyệt qua từng dòng, validate tính đúng đắn (Ví dụ: category có tồn tại, mức độ nhập đúng chuẩn...). Nếu sai, gán errorMessage vào dòng đó."),
            ("6", "Kiểm tra trùng lặp các chỉ số ngay trong nội bộ file Excel."),
            ("7", "Gom nhóm các dòng có cùng Tên chỉ số thành 1 Request Tạo cấu hình hoàn chỉnh."),
            ("8", "Mở transaction lưu từng cấu hình vào DB. Nhóm nào lỗi thì đánh dấu lỗi tất cả dòng trong nhóm đó."),
            ("9", "Nếu có bất kỳ dòng nào lỗi, sử dụng Apache POI tạo file Excel mới kèm cột 'Lý do lỗi' màu đỏ và trả về trực tiếp dưới dạng attachment."),
            ("10", "Nếu tất cả thành công, trả về JSON ApiResponse success.")
        ]
    }
]

for api in apis:
    doc.add_heading(f"a. Thông tin chung", level=2)
    p = doc.add_paragraph(style='List Bullet')
    p.add_run('Tên API: ').bold = True
    p.add_run(api["name"])
    
    p = doc.add_paragraph(style='List Bullet')
    p.add_run('URL: ').bold = True
    p.add_run(api["url"])
    
    p = doc.add_paragraph(style='List Bullet')
    p.add_run('Method: ').bold = True
    p.add_run(api["method"])
    
    p = doc.add_paragraph(style='List Bullet')
    p.add_run('Content type: ').bold = True
    p.add_run(api["content_type"])
    
    # Input
    if "input" in api and len(api["input"]) > 0:
        doc.add_paragraph('Mô tả dữ liệu input request body:', style='List Paragraph')
        table = doc.add_table(rows=1, cols=5)
        table.style = 'Table Grid'
        headers = ['STT', 'Trường dữ liệu', 'Kiểu dữ liệu', 'Mô tả', 'Bắt buộc']
        for i, header in enumerate(headers):
            cell = table.rows[0].cells[i]
            cell.text = header
            cell.paragraphs[0].runs[0].bold = True
            set_cell_background(cell, 'D9D9D9')
        for row_data in api["input"]:
            cells = table.add_row().cells
            for i in range(5):
                cells[i].text = str(row_data[i])

    if "query_params" in api and len(api["query_params"]) > 0:
        doc.add_paragraph('Mô tả dữ liệu input Query Parameters:', style='List Paragraph')
        table = doc.add_table(rows=1, cols=5)
        table.style = 'Table Grid'
        headers = ['STT', 'Trường dữ liệu', 'Kiểu dữ liệu', 'Mô tả', 'Bắt buộc']
        for i, header in enumerate(headers):
            cell = table.rows[0].cells[i]
            cell.text = header
            cell.paragraphs[0].runs[0].bold = True
            set_cell_background(cell, 'D9D9D9')
        for row_data in api["query_params"]:
            cells = table.add_row().cells
            for i in range(5):
                cells[i].text = str(row_data[i])

    if "levels_fields" in api and len(api["levels_fields"]) > 0:
        doc.add_paragraph('Mô tả cấu trúc đối tượng level trong Request body:', style='List Paragraph')
        table = doc.add_table(rows=1, cols=5)
        table.style = 'Table Grid'
        headers = ['STT', 'Trường dữ liệu', 'Kiểu dữ liệu', 'Mô tả', 'Bắt buộc']
        for i, header in enumerate(headers):
            cell = table.rows[0].cells[i]
            cell.text = header
            cell.paragraphs[0].runs[0].bold = True
            set_cell_background(cell, 'D9D9D9')
        for row_data in api["levels_fields"]:
            cells = table.add_row().cells
            for i in range(5):
                cells[i].text = str(row_data[i])

    # Output
    if "output" in api and len(api["output"]) > 0:
        doc.add_paragraph('Mô tả dữ liệu output:', style='List Paragraph')
        table = doc.add_table(rows=1, cols=4)
        table.style = 'Table Grid'
        headers = ['STT', 'Trường dữ liệu', 'Kiểu dữ liệu', 'Mô tả']
        for i, header in enumerate(headers):
            cell = table.rows[0].cells[i]
            cell.text = header
            cell.paragraphs[0].runs[0].bold = True
            set_cell_background(cell, 'D9D9D9')
        for row_data in api["output"]:
            cells = table.add_row().cells
            for i in range(4):
                cells[i].text = str(row_data[i])
                
    # Data Fields
    if "data_fields" in api and len(api["data_fields"]) > 0:
        doc.add_paragraph('Mô tả trường dữ liệu data:', style='List Paragraph')
        table = doc.add_table(rows=1, cols=4)
        table.style = 'Table Grid'
        headers = ['STT', 'Trường dữ liệu', 'Kiểu dữ liệu', 'Mô tả']
        for i, header in enumerate(headers):
            cell = table.rows[0].cells[i]
            cell.text = header
            cell.paragraphs[0].runs[0].bold = True
            set_cell_background(cell, 'D9D9D9')
        for row_data in api["data_fields"]:
            cells = table.add_row().cells
            for i in range(4):
                cells[i].text = str(row_data[i])

    doc.add_paragraph('ResponseResult:')
    
    # Logic
    doc.add_heading(f"b. Xử lý luồng sự kiện", level=2)
    table = doc.add_table(rows=1, cols=2)
    table.style = 'Table Grid'
    headers = ['Bước', 'Mô tả']
    for i, header in enumerate(headers):
        cell = table.rows[0].cells[i]
        cell.text = header
        cell.paragraphs[0].runs[0].bold = True
        set_cell_background(cell, 'D9D9D9')
    for row_data in api["logic"]:
        cells = table.add_row().cells
        cells[0].text = str(row_data[0])
        cells[1].text = str(row_data[1])

    doc.add_paragraph("-" * 50)

doc.save('API_AlertConfigController_Detailed.docx')
print("Document generated successfully.")
