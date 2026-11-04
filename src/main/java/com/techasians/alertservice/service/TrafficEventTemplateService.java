package com.techasians.alertservice.service;

import com.techasians.alertservice.dto.FileTemplateResponse;

/**
 * Service tạo file template Excel để import sự kiện lưu lượng.
 *
 * Cách hoạt động:
 * 1. Đọc file base template (template_import_event.xlsx) từ classpath/resources
 * 2. Query tất cả kênh lưu lượng đang hoạt động (status = 1)
 * 3. Fill tên kênh vào:
 *    - Sheet "Reference Sự kiện định kỳ"  : bắt đầu từ ô E5
 *    - Sheet "Reference Sự kiện phát sinh": bắt đầu từ ô C5
 * 4. Trả về file dưới dạng base64 để FE tải xuống
 */
public interface TrafficEventTemplateService {

    /**
     * Tạo và trả về file template import sự kiện đã được điền danh sách kênh.
     *
     * @return FileTemplateResponse chứa tên file, kiểu MIME và nội dung base64
     */
    FileTemplateResponse buildImportTemplate();
}
