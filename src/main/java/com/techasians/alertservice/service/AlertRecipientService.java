package com.techasians.alertservice.service;

import com.techasians.alertservice.dto.ChannelStatusDto;
import com.techasians.alertservice.dto.RecipientContactDto;
import com.techasians.alertservice.entity.AlertGroupMember;
import com.techasians.alertservice.entity.AlertLevelChannel;
import com.techasians.alertservice.entity.AlertLevelRecipient;
import com.techasians.alertservice.entity.AlertUser;
import com.techasians.alertservice.repository.AlertLevelChannelRepository;
import com.techasians.alertservice.repository.AlertLevelRecipientRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Service truy xuất danh sách người nhận cảnh báo theo từng mức.
 */
@Service
public class AlertRecipientService {

    private static final Logger log = LoggerFactory.getLogger(AlertRecipientService.class);

    private final AlertLevelRecipientRepository levelRecipientRepository;
    private final AlertLevelChannelRepository levelChannelRepository;
    private final com.techasians.alertservice.repository.AlertGroupMemberRepository groupMemberRepository;

    public AlertRecipientService(
            AlertLevelRecipientRepository levelRecipientRepository,
            AlertLevelChannelRepository levelChannelRepository,
            com.techasians.alertservice.repository.AlertGroupMemberRepository groupMemberRepository) {
        this.levelRecipientRepository = levelRecipientRepository;
        this.levelChannelRepository = levelChannelRepository;
        this.groupMemberRepository = groupMemberRepository;
    }

    public RecipientContactDto getContactsForAlertGroup(Integer alertGroupId) {
        Set<String> emailSet = new LinkedHashSet<>();
        Set<String> phoneSet = new LinkedHashSet<>();

        List<AlertGroupMember> members = groupMemberRepository.findByAlertGroupId(alertGroupId);
        for (AlertGroupMember m : members) {
            AlertUser user = m.getAlertUser();
            if (user.getDeletedAt() != null) continue;
            
            String email = user.getEmail();
            if (email != null && !email.trim().isEmpty()) {
                emailSet.add(email.trim());
            }

            String phone = user.getPhone();
            if (phone != null && !phone.trim().isEmpty()) {
                phoneSet.add(phone.trim());
            }
        }
        return new RecipientContactDto(new ArrayList<>(emailSet), new ArrayList<>(phoneSet));
    }

    /**
     * Lấy thông tin liên hệ (email, phone) của tất cả người nhận thuộc mức cảnh báo này.
     * Sử dụng truy vấn JOIN FETCH để tránh lỗi N+1 Query.
     *
     * @param alertLevelId ID của alert_level bị vi phạm
     * @param orgId ID của đơn vị vận hành (để filter AlertGroup thuộc đơn vị đó)
     * @return DTO chứa danh sách email và phone đã loại trùng lặp
     */
    public RecipientContactDto getContacts(Integer alertLevelId, Long orgId) {
        Set<String> emailSet = new LinkedHashSet<>();
        Set<String> phoneSet = new LinkedHashSet<>();

        // Sử dụng phương thức repository lấy trực tiếp danh sách người dùng để tránh N+1
        List<AlertUser> users = levelRecipientRepository.findUsersByAlertLevelIdAndOrgId(alertLevelId, orgId);

        for (AlertUser user : users) {
            String email = user.getEmail();
            if (email != null && !email.trim().isEmpty()) {
                emailSet.add(email.trim());
            }

            String phone = user.getPhone();
            if (phone != null && !phone.trim().isEmpty()) {
                phoneSet.add(phone.trim());
            }
        }

        List<String> emails = new ArrayList<>(emailSet);
        List<String> phones = new ArrayList<>(phoneSet);
        log.info("alertLevelId={} → {} email, {} phone người nhận", alertLevelId, emails.size(), phones.size());
        
        return new RecipientContactDto(emails, phones);
    }

    /**
     * Lấy trạng thái của các kênh (email, sms) được cấu hình cho mức cảnh báo này.
     * Sử dụng truy vấn JOIN FETCH để tránh N+1 Query.
     *
     * @param alertLevelId ID của alert_level
     * @return DTO chứa trạng thái có kênh email và sms hay không
     */
    public ChannelStatusDto getChannelStatus(Integer alertLevelId) {
        List<AlertLevelChannel> channels = levelChannelRepository.findByAlertLevelIdWithChannel(alertLevelId);
        
        boolean hasEmail = false;
        boolean hasSms = false;
        
        for (AlertLevelChannel c : channels) {
            if (c.getStatus() == 1) {
                String channelName = c.getNotificationChannel().getName();
                if ("email".equalsIgnoreCase(channelName)) {
                    hasEmail = true;
                } else if ("sms".equalsIgnoreCase(channelName)) {
                    hasSms = true;
                }
            }
        }
        
        return new ChannelStatusDto(hasEmail, hasSms);
    }
    /**
     * Lấy trạng thái các kênh (email, sms) cho nhiều mức cảnh báo cùng 1 lúc.
     * Dùng 1 query batch thay vì gọi getChannelStatus() trong vòng lặp (tránh N+1).
     *
     * @param alertLevelIds danh sách ID các alert_level cần kiểm tra
     * @return Map: alertLevelId → ChannelStatusDto
     */
    public Map<Integer, ChannelStatusDto> getChannelStatusBatch(List<Integer> alertLevelIds) {
        if (alertLevelIds == null || alertLevelIds.isEmpty()) {
            return new HashMap<>();
        }

        // 1 query duy nhất lấy tất cả các kênh của danh sách mức
        List<AlertLevelChannel> channels = levelChannelRepository.findByAlertLevelIdIn(alertLevelIds);

        // Build map: levelId → (hasEmail, hasSms)
        Map<Integer, ChannelStatusDto> result = new HashMap<>();
        for (AlertLevelChannel c : channels) {
            if (c.getStatus() != 1) continue;

            Integer levelId = c.getAlertLevel().getId();
            String channelName = c.getNotificationChannel().getName();

            // computeIfAbsent: nếu chưa có entry thì tạo mới
            ChannelStatusDto status = result.computeIfAbsent(levelId, id -> new ChannelStatusDto(false, false));

            if ("email".equalsIgnoreCase(channelName)) {
                status.setHasEmail(true);
            } else if ("sms".equalsIgnoreCase(channelName)) {
                status.setHasSms(true);
            }
        }

        return result;
    }
}

