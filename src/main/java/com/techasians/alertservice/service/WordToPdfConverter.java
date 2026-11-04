package com.techasians.alertservice.service;

import java.nio.file.Path;

/**
 * Interface chuyển đổi file Word sang PDF.
 * Tách riêng để WordPreviewService không phụ thuộc trực tiếp vào Aspose.
 * Unit test service dùng mock; integration test dùng Aspose thật.
 */
public interface WordToPdfConverter {

    /**
     * Đọc file Word tại sourceWordPath và trả về nội dung PDF dạng byte[].
     * PDF được tạo hoàn toàn trong bộ nhớ, không ghi file tạm.
     *
     * @param sourceWordPath đường dẫn tuyệt đối tới file .docx đã validate an toàn
     * @return mảng byte PDF, không bao giờ null và độ dài lớn hơn 0
     * @throws com.techasians.alertservice.exception.ReportPreviewGenerationException
     *         khi Aspose không đọc/render được file
     */
    byte[] convert(Path sourceWordPath);
}
