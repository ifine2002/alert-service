package com.techasians.alertservice.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import javax.mail.internet.MimeMessage;
import java.util.List;

import com.techasians.alertservice.entity.SmsMessage;
import com.techasians.alertservice.repository.SmsMessageRepository;

/**
 * Service thực thi gửi email và SMS.
 *
 * Cấu hình trong application.properties:
 *   spring.mail.host=smtp.gmail.com
 *   spring.mail.port=587
 *   ...
 */
@Service
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    private final JavaMailSender mailSender;

    @Value("${app.mail.from-name:vCOC - Hệ thống Điều hành Trung tâm}")
    private String fromName;

    @Value("${spring.mail.username:vcoc-alert@example.com}")
    private String fromEmail;

    private final SmsMessageRepository smsMessageRepository;

    public NotificationService(JavaMailSender mailSender, SmsMessageRepository smsMessageRepository) {
        this.mailSender = mailSender;
        this.smsMessageRepository = smsMessageRepository;
    }

    /**
     * Thực hiện gửi email qua JavaMailSender.
     * Ném ngoại lệ RuntimeException nếu lỗi để Caller có thể xử lý (hoặc Retry).
     */
    public void sendEmail(String[] to, String subject, String content, boolean isMultipart, boolean isHtml, String[] cc) {
        log.debug("Chuẩn bị gửi email tới '{}' với tiêu đề '{}'", to, subject);
        
        MimeMessage mimeMessage = mailSender.createMimeMessage();
        try {
            MimeMessageHelper message = new MimeMessageHelper(mimeMessage, isMultipart, java.nio.charset.StandardCharsets.UTF_8.name());
            message.setTo(to);
            if (cc != null && cc.length > 0) {
                message.setCc(cc);
            }
            message.setFrom(fromEmail); // Hoặc fromName nếu cần
            message.setSubject(subject);
            message.setText(content, isHtml);
            mailSender.send(mimeMessage);
            log.debug("Gửi email thành công tới '{}'", (Object) to);
        } catch (org.springframework.mail.MailException | javax.mail.MessagingException e) {
            log.warn("Lỗi gửi email tới '{}': {}", to, e.getMessage());
            throw new RuntimeException("Gửi mail thất bại: " + e.getMessage(), e);
        }
    }

    /**
     * Gửi SMS cho danh sách số điện thoại.
     * Bất đồng bộ (@Async).
     */
    @Async
    public void sendSms(List<String> phoneNumbers, String smsText) {
        if (phoneNumbers == null || phoneNumbers.isEmpty()) {
            log.warn("Danh sách số điện thoại rỗng, không gửi SMS nào.");
            return;
        }

        log.info("Bắt đầu lưu {} tin nhắn SMS vào DB (chờ gửi)", phoneNumbers.size());

        try {
            int totalInserted = smsMessageRepository.insertMessagesByPhones(smsText, phoneNumbers);
            log.info("Lưu thành công {} tin nhắn SMS vào DB qua Native Query", totalInserted);
        } catch (Exception e) {
            log.error("Lưu SMS vào DB thất bại: {}", e.getMessage(), e);
        }
    }
}
