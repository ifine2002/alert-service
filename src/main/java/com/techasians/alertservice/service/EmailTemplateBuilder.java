package com.techasians.alertservice.service;

import com.techasians.alertservice.dto.AlertViolationDto;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.Normalizer;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.time.format.DateTimeFormatter;

/**
 * Service xây dựng nội dung thông báo cảnh báo.
 *
 * Cung cấp 1 method duy nhất: buildContent(violation, isSms)
 *   - isSms = true  → trả về text ngắn gọn cho SMS
 *   - isSms = false → trả về HTML đầy đủ cho Email (có bảng, màu sắc)
 *
 * Ví dụ output SMS:
 *   [vCOC] CẢNH BÁO Mức 2: Tỷ lệ kết nối (CCR) kênh VTT-Di động
 *   lũy kế 82.00%, thấp hơn 4.50% so với KPI (90.00%).
 *   Thời điểm: 08h ngày 26/06/2026
 *
 * Ví dụ output Email: HTML đầy đủ với bảng thống kê và màu đỏ cho giá trị vi phạm.
 */
@Service
public class EmailTemplateBuilder {

    // Format ngày tháng hiển thị trong email/SMS
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final TemplateEngine templateEngine;

    public EmailTemplateBuilder(TemplateEngine templateEngine) {
        this.templateEngine = templateEngine;
    }

    public String buildEmailSubjectA(AlertViolationDto firstViolation) {
        return "CẢNH BÁO KẾT QUẢ CÁC CHỈ SỐ CHẤT LƯỢNG DỊCH VỤ Call Center Outsourcing chỉ số " + firstViolation.getMetricName();
    }

    public String buildEmailSubjectB(AlertViolationDto firstViolation) {
        return "CẢNH BÁO KẾT QUẢ CÁC CHỈ SỐ CHẤT LƯỢNG " + (firstViolation.getCategoryName() != null ? firstViolation.getCategoryName().toUpperCase() : "");
    }

    public String buildContent(java.util.List<AlertViolationDto> violations, String templateType, boolean isSms) {
        if (violations == null || violations.isEmpty()) return "";
        if (isSms) {
            return templateType.equals("A") ? buildSmsTemplateA(violations) : buildSmsTemplateB(violations);
        } else {
            return templateType.equals("A") ? buildHtmlTemplateA(violations) : buildHtmlTemplateB(violations);
        }
    }

    private String buildSmsTemplateA(java.util.List<AlertViolationDto> violations) {
        AlertViolationDto first = violations.get(0);
        String hour = String.format("%02d", first.getCheckTime().getHour());
        String minute = String.format("%02d", first.getCheckTime().getMinute());
        String date = first.getCheckTime().format(DATE_FORMAT);

        StringBuilder sb = new StringBuilder();
        sb.append(String.format("CANH BAO KET QUA chi so %s:\n", removeAccents(first.getMetricName())));
        sb.append("vCOC kinh gui dong chi,\n\n");
        sb.append(String.format("Den thoi diem %sh%s ngay %s:\n", hour, minute, date));

        for (AlertViolationDto v : violations) {
            String dir = removeAccents(getDeviationDirection(v.getActualValue(), v.getTargetKpiValue())).toLowerCase();
            sb.append(String.format("- Kenh %s luy ke la %s%%, %s %s%% so voi KPI (%s%%).\n",
                    removeAccents(v.getOrgName()), formatValue(v.getActualValue()), dir, formatValue(v.getDeviationValue()), formatValue(v.getTargetKpiValue())));
        }
        return sb.toString();
    }

    private String buildSmsTemplateB(java.util.List<AlertViolationDto> violations) {
        AlertViolationDto first = violations.get(0);
        String hour = String.format("%02d", first.getCheckTime().getHour());
        String minute = String.format("%02d", first.getCheckTime().getMinute());
        String date = first.getCheckTime().format(DATE_FORMAT);

        StringBuilder sb = new StringBuilder();
        sb.append(String.format("CANH BAO KET QUA CAC CHI SO CHAT LUONG %s\n", removeAccents(first.getCategoryName() != null ? first.getCategoryName().toUpperCase() : "")));
        sb.append("vCOC kinh gui dong chi,\n\n");
        sb.append(String.format("Den thoi diem %sh%s ngay %s:\n", hour, minute, date));

        for (AlertViolationDto v : violations) {
            String dir = removeAccents(getDeviationDirection(v.getActualValue(), v.getTargetKpiValue())).toLowerCase();
            sb.append(String.format("- Chi so %s luy ke la %s%%, %s %s%% so voi KPI (%s%%).\n",
                    removeAccents(v.getMetricName()), formatValue(v.getActualValue()), dir, formatValue(v.getDeviationValue()), formatValue(v.getTargetKpiValue())));
        }
        return sb.toString();
    }

    private String buildHtmlTemplateA(List<AlertViolationDto> violations) {
        AlertViolationDto first = violations.get(0);
        String hour = String.format("%02d", first.getCheckTime().getHour());
        String minute = String.format("%02d", first.getCheckTime().getMinute());
        String date = first.getCheckTime().format(DATE_FORMAT);

        List<ViolationView> viewList = new ArrayList<>();
        for (AlertViolationDto v : violations) {
            String dir = getDeviationDirection(v.getActualValue(), v.getTargetKpiValue());
            viewList.add(new ViolationView(
                    v.getOrgName(),
                    formatValue(v.getActualValue()),
                    dir,
                    formatValue(v.getDeviationValue()),
                    formatValue(v.getTargetKpiValue()),
                    v.getViolatedLevelNumber()
            ));
        }

        Context context = new Context();
        context.setVariable("hour", hour);
        context.setVariable("minute", minute);
        context.setVariable("date", date);
        context.setVariable("violations", viewList);

        return templateEngine.process("mail/alert-template-a", context);
    }

    private String buildHtmlTemplateB(List<AlertViolationDto> violations) {
        AlertViolationDto first = violations.get(0);
        String hour = String.format("%02d", first.getCheckTime().getHour());
        String minute = String.format("%02d", first.getCheckTime().getMinute());
        String date = first.getCheckTime().format(DATE_FORMAT);

        List<ViolationView> viewList = new ArrayList<>();
        for (AlertViolationDto v : violations) {
            String dir = getDeviationDirection(v.getActualValue(), v.getTargetKpiValue());
            viewList.add(new ViolationView(
                    v.getMetricName(),
                    formatValue(v.getActualValue()),
                    dir,
                    formatValue(v.getDeviationValue()),
                    formatValue(v.getTargetKpiValue()),
                    v.getViolatedLevelNumber()
            ));
        }

        Context context = new Context();
        context.setVariable("hour", hour);
        context.setVariable("minute", minute);
        context.setVariable("date", date);
        context.setVariable("violations", viewList);

        return templateEngine.process("mail/alert-template-b", context);
    }

    private String getDeviationDirection(BigDecimal actual, BigDecimal target) {
        int compare = actual.compareTo(target);
        if (compare < 0) return "Thấp hơn";
        if (compare > 0) return "Lớn hơn";
        return "Bằng";
    }

    private String formatValue(BigDecimal value) {
        if (value == null) return "0.00";
        return value.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    private String removeAccents(String text) {
        if (text == null) return null;
        String normalized = Normalizer.normalize(text, Normalizer.Form.NFD);
        String result = normalized.replaceAll("\\p{InCombiningDiacriticalMarks}+", "");
        result = result.replace("Đ", "D").replace("đ", "d");
        return result;
    }

    public static class ViolationView {
        private String name;
        private String actualValue;
        private String deviationDirection;
        private String deviationValue;
        private String targetKpiValue;
        private Integer level;

        public ViolationView(String name, String actualValue, String deviationDirection, String deviationValue, String targetKpiValue, Integer level) {
            this.name = name;
            this.actualValue = actualValue;
            this.deviationDirection = deviationDirection;
            this.deviationValue = deviationValue;
            this.targetKpiValue = targetKpiValue;
            this.level = level;
        }

        public String getName() { return name; }
        public String getActualValue() { return actualValue; }
        public String getDeviationDirection() { return deviationDirection; }
        public String getDeviationValue() { return deviationValue; }
        public String getTargetKpiValue() { return targetKpiValue; }
        public Integer getLevel() { return level; }
    }
}
