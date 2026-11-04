package com.techasians.alertservice.service.impl;

import com.techasians.alertservice.dto.FileTemplateResponse;
import com.techasians.alertservice.entity.TrafficChannel;
import com.techasians.alertservice.repository.TrafficChannelRepository;
import com.techasians.alertservice.service.TrafficEventTemplateService;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.Base64;
import java.util.List;

/**
 * Triển khai TrafficEventTemplateService.
 *
 * Luồng xử lý:
 *  1. Mở file template từ classpath: templates/template_import_event.xlsx
 *  2. Query DB lấy tất cả kênh lưu lượng đang hoạt động (status = 1)
 *  3. Fill tên kênh vào sheet "Reference Sự kiện định kỳ"  bắt đầu từ ô E5 (row=4, col=4)
 *  4. Fill tên kênh vào sheet "Reference Sự kiện phát sinh" bắt đầu từ ô C5 (row=4, col=2)
 *  5. Ghi workbook ra mảng byte, encode base64, trả về FileTemplateResponse
 */
@Service
public class TrafficEventTemplateServiceImpl implements TrafficEventTemplateService {

    // Tên file template đặt trong src/main/resources/templates/
    private static final String TEMPLATE_PATH = "templates/template_import_event.xlsx";

    // Tên 2 sheet Reference trong template
    private static final String SHEET_REF_DINH_KY    = "Reference Sự kiện định kỳ";
    private static final String SHEET_REF_PHAT_SINH  = "Reference Sự kiện phát sinh";

    // Vị trí bắt đầu fill dữ liệu (0-based index)
    // Sheet "Reference Sự kiện định kỳ"  : ô E5 → row 4, col 4
    private static final int REF_DINH_KY_START_ROW = 4;   // row 5  (0-based = 4)
    private static final int REF_DINH_KY_START_COL = 4;   // col E  (0-based = 4)

    // Sheet "Reference Sự kiện phát sinh": ô C5 → row 4, col 2
    private static final int REF_PHAT_SINH_START_ROW = 4; // row 5  (0-based = 4)
    private static final int REF_PHAT_SINH_START_COL = 2; // col C  (0-based = 2)

    private final TrafficChannelRepository trafficChannelRepository;

    public TrafficEventTemplateServiceImpl(TrafficChannelRepository trafficChannelRepository) {
        this.trafficChannelRepository = trafficChannelRepository;
    }

    @Override
    public FileTemplateResponse buildImportTemplate() {
        // Bước 1: Query danh sách kênh lưu lượng đang hoạt động
        List<TrafficChannel> activeChannels = trafficChannelRepository.findByStatus(1);

        // Bước 2: Đọc file template từ classpath
        try (InputStream inputStream = new ClassPathResource(TEMPLATE_PATH).getInputStream();
             Workbook workbook = new XSSFWorkbook(inputStream)) {

            // Bước 3: Fill tên kênh vào sheet "Reference Sự kiện định kỳ" từ ô E5
            fillChannelNames(
                    workbook,
                    SHEET_REF_DINH_KY,
                    REF_DINH_KY_START_ROW,
                    REF_DINH_KY_START_COL,
                    activeChannels
            );

            // Bước 4: Fill tên kênh vào sheet "Reference Sự kiện phát sinh" từ ô C5
            fillChannelNames(
                    workbook,
                    SHEET_REF_PHAT_SINH,
                    REF_PHAT_SINH_START_ROW,
                    REF_PHAT_SINH_START_COL,
                    activeChannels
            );

            // Bước 5: Ghi workbook ra byte array rồi encode base64
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            workbook.write(outputStream);

            String base64Data = Base64.getEncoder().encodeToString(outputStream.toByteArray());

            return new FileTemplateResponse(
                    "Template_Import_Event.xlsx",
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                    base64Data
            );

        } catch (Exception e) {
            throw new RuntimeException("Lỗi khi tạo template import sự kiện: " + e.getMessage(), e);
        }
    }

    /**
     * Điền tên kênh vào một sheet cụ thể.
     *
     * Ví dụ thực tế:
     *   fillChannelNames(workbook, "Reference Sự kiện định kỳ", 4, 4, channels)
     *   → Ghi channel.getName() vào E5, E6, E7, ...
     *
     * @param workbook      Workbook đang mở
     * @param sheetName     Tên sheet cần fill
     * @param startRow      Chỉ số hàng bắt đầu (0-based, VD: 4 = hàng 5 trên Excel)
     * @param startCol      Chỉ số cột bắt đầu (0-based, VD: 4 = cột E, 2 = cột C)
     * @param channels      Danh sách kênh lưu lượng đang hoạt động
     */
    private void fillChannelNames(Workbook workbook,
                                  String sheetName,
                                  int startRow,
                                  int startCol,
                                  List<TrafficChannel> channels) {
        // Lấy sheet theo tên
        Sheet sheet = workbook.getSheet(sheetName);
        if (sheet == null) {
            throw new RuntimeException(
                    "Không tìm thấy sheet '" + sheetName + "' trong file template. "
                    + "Hãy kiểm tra lại tên sheet trong file template_import_event.xlsx"
            );
        }

        // Duyệt qua từng kênh và ghi tên vào từng ô theo chiều dọc
        for (int i = 0; i < channels.size(); i++) {
            int rowIndex = startRow + i; // Ví dụ: 4, 5, 6, ...

            // Lấy hàng nếu đã có, nếu chưa thì tạo mới
            Row row = sheet.getRow(rowIndex);
            if (row == null) {
                row = sheet.createRow(rowIndex);
            }

            // Lấy ô nếu đã có, nếu chưa thì tạo mới
            Cell cell = row.getCell(startCol);
            if (cell == null) {
                cell = row.createCell(startCol);
            }

            // Ghi tên kênh vào ô
            cell.setCellValue(channels.get(i).getName());
        }
    }
}
