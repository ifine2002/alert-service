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
title = doc.add_heading('TÀI LIỆU ĐẶC TẢ API', 0)
title.alignment = WD_ALIGN_PARAGRAPH.CENTER

subtitle = doc.add_heading('ALERT CONFIG CONTROLLER', 1)
subtitle.alignment = WD_ALIGN_PARAGRAPH.CENTER

apis = [
    {
        "name": "1. Lấy danh sách cấu hình cảnh báo",
        "method": "GET",
        "url": "/api/alert-configs",
        "desc": "Lấy danh sách cấu hình cảnh báo, hỗ trợ tìm kiếm và phân trang.",
        "params": [
            ("categoryId", "Integer", "ID danh mục (tùy chọn)"),
            ("metricName", "String", "Tên chỉ số (tùy chọn)"),
            ("createdBy", "String", "Người tạo (tùy chọn)"),
            ("page", "Integer", "Trang hiện tại, mặc định 0"),
            ("size", "Integer", "Số lượng bản ghi/trang, mặc định 10")
        ],
        "response": "ResponseEntity<ApiResponse<Page<AlertConfigResponse>>>"
    },
    {
        "name": "2. Thêm mới cấu hình cảnh báo",
        "method": "POST",
        "url": "/api/alert-configs",
        "desc": "Thêm mới một cấu hình cảnh báo.",
        "headers": [("X-Username", "String", "Tên người dùng tạo (mặc định: system)")],
        "body": "AlertConfigRequest (name, categoryId, metricId, status, levels...)",
        "response": "ApiResponse<Integer> (Chứa ID của cấu hình vừa tạo)"
    },
    {
        "name": "3. Cập nhật cấu hình cảnh báo",
        "method": "PUT",
        "url": "/api/alert-configs/{id}",
        "desc": "Cập nhật cấu hình cảnh báo theo ID.",
        "headers": [("X-Username", "String", "Tên người dùng cập nhật (mặc định: system)")],
        "path_vars": [("id", "Integer", "ID cấu hình cảnh báo cần cập nhật")],
        "body": "AlertConfigRequest (name, categoryId, metricId, status, levels...)",
        "response": "ApiResponse<Integer> (Chứa ID của cấu hình vừa cập nhật)"
    },
    {
        "name": "4. Xóa cấu hình cảnh báo",
        "method": "DELETE",
        "url": "/api/alert-configs/{id}",
        "desc": "Xóa mềm cấu hình cảnh báo theo ID (đánh dấu deleted = true).",
        "headers": [("X-Username", "String", "Tên người dùng xóa (mặc định: system)")],
        "path_vars": [("id", "Integer", "ID cấu hình cảnh báo cần xóa")],
        "response": "ApiResponse<Void> (Thông báo xóa thành công)"
    },
    {
        "name": "5. Lấy chi tiết cấu hình cảnh báo",
        "method": "GET",
        "url": "/api/alert-configs/{id}",
        "desc": "Lấy chi tiết một cấu hình cảnh báo theo ID.",
        "path_vars": [("id", "Integer", "ID cấu hình cảnh báo cần lấy chi tiết")],
        "response": "ApiResponse<AlertConfigDetailResponse>"
    },
    {
        "name": "6. Download template import Excel",
        "method": "GET",
        "url": "/api/alert-configs/import/template",
        "desc": "Tải về file Excel mẫu rỗng với các cột header đã định dạng sẵn để người dùng điền dữ liệu import.",
        "response": "File Excel (.xlsx) với Content-Disposition: attachment"
    },
    {
        "name": "7. Import cấu hình cảnh báo từ file Excel",
        "method": "POST",
        "url": "/api/alert-configs/import",
        "desc": "Upload file Excel để import hàng loạt cấu hình cảnh báo. Nếu thành công hoàn toàn trả về HTTP 200 JSON, nếu có lỗi dữ liệu trên một số dòng trả về file Excel chứa chi tiết lỗi.",
        "headers": [("X-Username", "String", "Tên người dùng thực hiện (mặc định: system)")],
        "body": "multipart/form-data (tham số 'file': file .xlsx)",
        "response": "ApiResponse<Void> (hoặc byte[] là file Excel báo lỗi nếu có dòng lỗi)"
    }
]

for api in apis:
    doc.add_heading(api["name"], level=2)
    
    p = doc.add_paragraph()
    p.add_run('Mô tả: ').bold = True
    p.add_run(api["desc"]).add_break()
    
    p.add_run('URL: ').bold = True
    p.add_run(api["url"]).add_break()
    
    p.add_run('Method: ').bold = True
    p.add_run(api["method"])
    
    if "path_vars" in api:
        doc.add_heading("Path Variables:", level=3)
        table = doc.add_table(rows=1, cols=3)
        table.style = 'Table Grid'
        hdr_cells = table.rows[0].cells
        for i, header in enumerate(['Tên tham số', 'Kiểu dữ liệu', 'Mô tả']):
            hdr_cells[i].text = header
            hdr_cells[i].paragraphs[0].runs[0].bold = True
            set_cell_background(hdr_cells[i], 'D9D9D9')
        for param in api["path_vars"]:
            row_cells = table.add_row().cells
            row_cells[0].text = param[0]
            row_cells[1].text = param[1]
            row_cells[2].text = param[2]

    if "headers" in api:
        doc.add_heading("Headers:", level=3)
        table = doc.add_table(rows=1, cols=3)
        table.style = 'Table Grid'
        hdr_cells = table.rows[0].cells
        for i, header in enumerate(['Tên Header', 'Kiểu dữ liệu', 'Mô tả']):
            hdr_cells[i].text = header
            hdr_cells[i].paragraphs[0].runs[0].bold = True
            set_cell_background(hdr_cells[i], 'D9D9D9')
        for param in api["headers"]:
            row_cells = table.add_row().cells
            row_cells[0].text = param[0]
            row_cells[1].text = param[1]
            row_cells[2].text = param[2]

    if "params" in api:
        doc.add_heading("Query Parameters:", level=3)
        table = doc.add_table(rows=1, cols=3)
        table.style = 'Table Grid'
        hdr_cells = table.rows[0].cells
        for i, header in enumerate(['Tên tham số', 'Kiểu dữ liệu', 'Mô tả']):
            hdr_cells[i].text = header
            hdr_cells[i].paragraphs[0].runs[0].bold = True
            set_cell_background(hdr_cells[i], 'D9D9D9')
        for param in api["params"]:
            row_cells = table.add_row().cells
            row_cells[0].text = param[0]
            row_cells[1].text = param[1]
            row_cells[2].text = param[2]

    if "body" in api:
        p_body = doc.add_paragraph()
        p_body.add_run('Request Body: ').bold = True
        p_body.add_run(api["body"])

    if "response" in api:
        p_res = doc.add_paragraph()
        p_res.add_run('Response: ').bold = True
        p_res.add_run(api["response"])

    doc.add_paragraph("-" * 50)

doc.save('API_AlertConfigController.docx')
print("Document generated successfully.")
