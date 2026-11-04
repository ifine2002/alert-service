package com.techasians.alertservice.scheduler;

import com.techasians.alertservice.dto.AlertViolationDto;
import com.techasians.alertservice.dto.ChannelStatusDto;
import com.techasians.alertservice.dto.RecipientContactDto;
import com.techasians.alertservice.entity.AlertGroup;
import com.techasians.alertservice.enums.TargetType;
import com.techasians.alertservice.service.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.Duration;
import java.util.*;

import com.techasians.alertservice.entity.EmailMessage;
import com.techasians.alertservice.repository.EmailMessageRepository;
import com.techasians.alertservice.queue.QueueService;
import com.techasians.alertservice.queue.RetryAbleTask;

/**
 * Cronjob tự động chạy 3 ca mỗi ngày để kiểm tra KPI và gửi cảnh báo.
 *
 * 3 ca cố định:
 *   Ca 1 - 08:00: dữ liệu từ 17:31 hôm qua đến 08:00 hôm nay
 *   Ca 2 - 13:30: dữ liệu từ 08:01 hôm nay đến 13:30 hôm nay
 *   Ca 3 - 17:30: dữ liệu từ 13:31 hôm nay đến 17:00 hôm nay
 *
 * Luồng mỗi ca:
 *   1. Tính toán KPI và phát hiện vi phạm (KpiCalculationService)
 *   2. Với mỗi vi phạm: lấy danh sách người nhận (AlertRecipientService)
 *   3. Build nội dung email/SMS (EmailTemplateBuilder)
 *   4. Gửi email và SMS (NotificationService - bất đồng bộ)
 */
@Component
public class AlertSchedulerService {

    private static final Logger log = LoggerFactory.getLogger(AlertSchedulerService.class);
    private static final int CALL_CENTER_ROOT_CATEGORY_ID = 3;

    private final KpiCalculationService kpiCalculationService;
    private final AlertRecipientService alertRecipientService;
    private final EmailTemplateBuilder emailTemplateBuilder;
    private final NotificationService notificationService;
    private final EmailMessageRepository emailMessageRepository;
    private final QueueService queueService;
    private final AlertGroupService alertGroupService;

    public AlertSchedulerService(
            KpiCalculationService kpiCalculationService,
            AlertRecipientService alertRecipientService,
            EmailTemplateBuilder emailTemplateBuilder,
            NotificationService notificationService,
            EmailMessageRepository emailMessageRepository,
            QueueService queueService,
            AlertGroupService alertGroupService) {
        this.kpiCalculationService = kpiCalculationService;
        this.alertRecipientService = alertRecipientService;
        this.emailTemplateBuilder = emailTemplateBuilder;
        this.notificationService = notificationService;
        this.emailMessageRepository = emailMessageRepository;
        this.queueService = queueService;
        this.alertGroupService = alertGroupService;
    }

    /**
     * Ca 1 - chạy lúc 08:00 hàng ngày.
     * Dữ liệu: từ 17:31 ngày hôm trước đến 08:00 ngày hôm nay.
     */
    @Scheduled(cron = "${app.scheduler.shift1.cron:0 0 8 * * *}", zone = "${app.scheduler.zone:Asia/Ho_Chi_Minh}")
    public void runShift1() {
        LocalDateTime toTime = LocalDateTime.now()
                .withHour(8).withMinute(0).withSecond(0).withNano(0);
        LocalDateTime fromTime = toTime.minusDays(1)
                .withHour(17).withMinute(31).withSecond(0).withNano(0);

        runAlertCheck("Ca 1", fromTime, toTime);
    }

    /**
     * Ca 2 - chạy lúc 13:30 hàng ngày.
     * Dữ liệu: từ 08:01 hôm nay đến 13:30 hôm nay.
     */
    @Scheduled(cron = "${app.scheduler.shift2.cron:0 30 13 * * *}", zone = "${app.scheduler.zone:Asia/Ho_Chi_Minh}")
    public void runShift2() {
        LocalDateTime toTime = LocalDateTime.now()
                .withHour(13).withMinute(30).withSecond(0).withNano(0);
        LocalDateTime fromTime = toTime.toLocalDate().atTime(8, 1, 0);

        runAlertCheck("Ca 2", fromTime, toTime);
    }

    /**
     * Ca 3 - chạy lúc 17:30 hàng ngày.
     * Dữ liệu: từ 13:31 hôm nay đến 17:00 hôm nay.
     */
    @Scheduled(cron = "${app.scheduler.shift3.cron:0 30 17 * * *}", zone = "${app.scheduler.zone:Asia/Ho_Chi_Minh}")
    public void runShift3() {
        LocalDateTime toTime = LocalDateTime.now()
                .withHour(17).withMinute(0).withSecond(0).withNano(0);
        LocalDateTime fromTime = toTime.toLocalDate().atTime(13, 31, 0);

        runAlertCheck("Ca 3", fromTime, toTime);
    }

    /**
     * Phương thức dùng chung cho cả 3 ca tự động và API gửi thủ công.
     *
     * Đây là hàm public để AlertNotificationController có thể gọi trực tiếp
     * khi người dùng nhấn nút "Gửi cảnh báo ngay".
     *
     * @param shiftName tên ca ("Ca 1", "Ca 2", "Ca 3" hoặc "Thủ công")
     * @param fromTime  thời điểm bắt đầu lấy dữ liệu
     * @param toTime    thời điểm kết thúc lấy dữ liệu
     */
    public void runAlertCheck(String shiftName, LocalDateTime fromTime, LocalDateTime toTime) {
        log.info("[{}] Bắt đầu kiểm tra cảnh báo | Khoảng thời gian: {} → {}", shiftName, fromTime, toTime);

        // Bước 1: Tính toán KPI, phát hiện vi phạm
        List<AlertViolationDto> violations = kpiCalculationService.detectViolations(shiftName, fromTime, toTime);

        if (violations.isEmpty()) {
            log.info("[{}] Không phát hiện vi phạm nào.", shiftName);
            return;
        }

        log.info("[{}] Phát hiện {} vi phạm, bắt đầu gom nhóm gửi thông báo...", shiftName, violations.size());

        // Gom nhóm theo AlertGroup
        // Key: groupId-metricId (Template A) hoặc groupId-categoryId (Template B)
        // CHỈ đưa vi phạm vào bucket nếu mức đó có ít nhất 1 kênh gửi (email/sms) được bật
        Map<String, List<AlertViolationDto>> bucketA = new HashMap<>();
        Map<String, List<AlertViolationDto>> bucketB = new HashMap<>();

        // Load 1 lần duy nhất tất cả ChannelStatus bằng 1 query batch (tránh N+1)
        List<Integer> allLevelIds = violations.stream()
                .map(AlertViolationDto::getViolatedLevelId)
                .distinct()
                .collect(java.util.stream.Collectors.toList());
        Map<Integer, ChannelStatusDto> channelStatusMap = alertRecipientService.getChannelStatusBatch(allLevelIds);

        for (AlertViolationDto v : violations) {
            Integer levelId = v.getViolatedLevelId();
            Integer categoryId = v.getCategoryId();
            Integer parentCategoryId = v.getParentCategoryId();

            boolean isTypeA = isTemplateA(categoryId, parentCategoryId);

            if (isTypeA) {
                List<AlertGroup> groups = alertGroupService.findGroupsByLevelAndTarget(
                        levelId, v.getOrgId().longValue(), TargetType.ORG);
                for (AlertGroup g : groups) {
                    String key = g.getId() + "-" + v.getMetricId();
                    bucketA.computeIfAbsent(key, k -> new ArrayList<>()).add(v);
                }
            } else {
                if (categoryId == null) continue;
                List<AlertGroup> groups = alertGroupService.findGroupsByLevelAndTarget(
                        levelId, categoryId.longValue(), TargetType.CATEGORY);
                for (AlertGroup g : groups) {
                    String key = g.getId() + "-" + categoryId;
                    bucketB.computeIfAbsent(key, k -> new ArrayList<>()).add(v);
                }
            }
        }

        // Gửi thông báo cho từng bucket
        for (Map.Entry<String, List<AlertViolationDto>> entry : bucketA.entrySet()) {
            processGroupedViolations(shiftName, entry.getKey(), entry.getValue(), "A", channelStatusMap);
        }
        for (Map.Entry<String, List<AlertViolationDto>> entry : bucketB.entrySet()) {
            processGroupedViolations(shiftName, entry.getKey(), entry.getValue(), "B", channelStatusMap);
        }

        log.info("[{}] Hoàn thành xử lý gửi {} nhóm cảnh báo.", shiftName, bucketA.size() + bucketB.size());
    }

    private boolean isTemplateA(Integer categoryId, Integer parentCategoryId) {
        Integer rootCategoryId =
                parentCategoryId != null
                        ? parentCategoryId
                        : categoryId;

        return Objects.equals(rootCategoryId, CALL_CENTER_ROOT_CATEGORY_ID);
    }

    private void processGroupedViolations(String shiftName, String bucketKey, List<AlertViolationDto> violations,
                                           String templateType, Map<Integer, ChannelStatusDto> channelStatusMap) {
        if (violations.isEmpty()) return;

        // Key hiện tại chỉ còn: groupId-metricId (A) hoặc groupId-categoryId (B)
        String[] parts = bucketKey.split("-");
        Integer groupId = Integer.parseInt(parts[0]);

        AlertViolationDto firstViolation = violations.get(0);
        String metricInfo = "Template " + templateType + " - " + (templateType.equals("A") ? firstViolation.getMetricName() : firstViolation.getCategoryName());

        log.info("[{}] Xử lý vi phạm nhóm: {} ({} vi phạm)", shiftName, metricInfo, violations.size());

        // Tách thành 2 danh sách riêng biệt cho từng kênh
        // Tra cứu từ channelStatusMap (không gọi DB, đã load batch từ trước)
        List<AlertViolationDto> violationsForEmail = new ArrayList<>();
        List<AlertViolationDto> violationsForSms = new ArrayList<>();

        for (AlertViolationDto v : violations) {
            ChannelStatusDto cs = channelStatusMap.get(v.getViolatedLevelId());
            if (cs == null) continue;
            if (cs.isHasEmail()) violationsForEmail.add(v);
            if (cs.isHasSms())   violationsForSms.add(v);
        }

        RecipientContactDto contacts = alertRecipientService.getContactsForAlertGroup(groupId);

        log.info("Danh sách email, phone: {}, {}", contacts.getEmails(), contacts.getPhones());

        // --- Gửi EMAIL với danh sách vi phạm có kênh Email ---
        if (!violationsForEmail.isEmpty()) {
            List<String> emails = contacts.getEmails();

            if (!emails.isEmpty()) {
                AlertViolationDto firstForEmail = violationsForEmail.get(0);
                String subject = templateType.equals("A")
                    ? emailTemplateBuilder.buildEmailSubjectA(firstForEmail)
                    : emailTemplateBuilder.buildEmailSubjectB(firstForEmail);
                String htmlBody = emailTemplateBuilder.buildContent(violationsForEmail, templateType, false);
                String[] toArray = emails.toArray(new String[0]);

                EmailMessage logMsg = new EmailMessage();
                logMsg.setRecipients(String.join(",", toArray));
                logMsg.setSubject(subject);
                logMsg.setContent(htmlBody);
                logMsg.setStatus(0);
                logMsg = emailMessageRepository.save(logMsg);

                final Long logId = logMsg.getId();

                RetryAbleTask retryTask = RetryAbleTask.builder()
                        .delegate(() -> notificationService.sendEmail(toArray, subject, htmlBody, false, true, null))
                        .maxRetries(5)
                        .firstBackoff(Duration.ofSeconds(5))
                        .queue(queueService)
                        .retryWhen(ex -> true)
                        .onSuccess(() -> updateEmailLogStatus(logId, 1, null, null))
                        .onFailure(ex -> updateEmailLogStatus(logId, 2, null, ex.getMessage()))
                        .onRetry((retryCount, ex) -> updateEmailLogStatus(logId, null, retryCount, ex.getMessage()))
                        .build();

                queueService.push(retryTask);

                log.info("[{}] Đã đưa task gửi email vào queue cho {} người nhận ({})",
                        shiftName, emails.size(), metricInfo);
            } else {
                log.warn("[{}] Kênh Email được cấu hình nhưng không có người nhận nào trong nhóm (groupId={})",
                        shiftName, groupId);
            }
        }

        // --- Gửi SMS với danh sách vi phạm có kênh SMS ---
        if (!violationsForSms.isEmpty()) {
            List<String> phones = contacts.getPhones();

            if (!phones.isEmpty()) {
                String smsText = emailTemplateBuilder.buildContent(violationsForSms, templateType, true);
                notificationService.sendSms(phones, smsText);
                log.info("[{}] Đã kích hoạt gửi SMS cho {} số điện thoại ({})",
                        shiftName, phones.size(), metricInfo);
            } else {
                log.warn("[{}] Kênh SMS được cấu hình nhưng không có số điện thoại nào trong nhóm (groupId={})",
                        shiftName, groupId);
            }
        }
    }

    private void updateEmailLogStatus(Long logId, Integer status, Integer retryCount, String errorMessage) {
        emailMessageRepository.findById(logId).ifPresent(logMsg -> {
            if (status != null) logMsg.setStatus(status);
            if (retryCount != null) logMsg.setRetryCount(retryCount);
            if (errorMessage != null) logMsg.setErrorMessage(errorMessage);
            emailMessageRepository.save(logMsg);
        });
    }
}
