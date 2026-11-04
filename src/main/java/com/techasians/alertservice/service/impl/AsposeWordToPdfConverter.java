package com.techasians.alertservice.service.impl;

import com.aspose.words.Document;
import com.aspose.words.SaveFormat;
import com.techasians.alertservice.exception.ReportPreviewGenerationException;
import com.techasians.alertservice.service.WordToPdfConverter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Chuyển đổi file .docx sang PDF bằng Aspose.Words.
 * Toàn bộ quá trình diễn ra trong bộ nhớ; không ghi file tạm trên disk.
 */
@Service
public class AsposeWordToPdfConverter implements WordToPdfConverter {

    private static final Logger log = LoggerFactory.getLogger(AsposeWordToPdfConverter.class);

    @Override
    public byte[] convert(Path sourceWordPath) {
        long totalStartTime = System.nanoTime();
        String sourceFileName = sourceWordPath.getFileName().toString();

        log.info("Bắt đầu chuyển Word sang PDF: file={}", sourceFileName);

        // Bước 1: Đọc file Word vào InputStream
        // Dùng try-with-resources để đảm bảo stream luôn được đóng
        long openStreamStartTime = System.nanoTime();
        try (InputStream inputStream = Files.newInputStream(sourceWordPath)) {
            long openStreamDurationMs = elapsedMilliseconds(openStreamStartTime);
            log.info("Mở InputStream xong: file={}, durationMs={}",
                    sourceFileName, openStreamDurationMs);

            // Bước 2: Aspose đọc tài liệu Word từ stream
            long loadDocumentStartTime = System.nanoTime();
            Document document = new Document(inputStream);
            long loadDocumentDurationMs = elapsedMilliseconds(loadDocumentStartTime);
            log.info("Aspose tải tài liệu xong: file={}, durationMs={}",
                    sourceFileName, loadDocumentDurationMs);

            // Bước 3: Aspose render sang PDF và ghi vào bộ nhớ
            long renderPdfStartTime = System.nanoTime();
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            document.save(outputStream, SaveFormat.PDF);
            long renderPdfDurationMs = elapsedMilliseconds(renderPdfStartTime);
            log.info("Aspose render PDF xong: file={}, durationMs={}",
                    sourceFileName, renderPdfDurationMs);

            // Bước 4: Lấy kết quả và kiểm tra không rỗng
            long createByteArrayStartTime = System.nanoTime();
            byte[] pdfBytes = outputStream.toByteArray();
            long createByteArrayDurationMs = elapsedMilliseconds(createByteArrayStartTime);
            if (pdfBytes == null || pdfBytes.length == 0) {
                throw new ReportPreviewGenerationException(
                        "Aspose trả về PDF rỗng cho file: " + sourceFileName);
            }

            long totalDurationMs = elapsedMilliseconds(totalStartTime);
            log.info("Chuyển Word sang PDF thành công: file={}, pdfSize={} bytes, "
                            + "createByteArrayMs={}, totalDurationMs={}",
                    sourceFileName, pdfBytes.length, createByteArrayDurationMs, totalDurationMs);
            return pdfBytes;

        } catch (ReportPreviewGenerationException exception) {
            log.error("Chuyển Word sang PDF thất bại: file={}, durationMs={}, error={}",
                    sourceFileName, elapsedMilliseconds(totalStartTime), exception.getMessage());
            // Ném thẳng lên, không bọc thêm
            throw exception;
        } catch (Exception exception) {
            // Ghi log đầy đủ để vận hành tra cứu, nhưng exception trả về không chứa path vật lý
            log.error("Lỗi chuyển đổi Word sang PDF: file={}, durationMs={}, error={}",
                    sourceFileName, elapsedMilliseconds(totalStartTime), exception.getMessage(), exception);
            throw new ReportPreviewGenerationException(
                    "Không thể chuyển đổi file Word sang PDF", exception);
        }
    }

    private long elapsedMilliseconds(long startTime) {
        return (System.nanoTime() - startTime) / 1_000_000;
    }
}
