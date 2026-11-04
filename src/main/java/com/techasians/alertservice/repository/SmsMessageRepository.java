package com.techasians.alertservice.repository;

import com.techasians.alertservice.entity.SmsMessage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public interface SmsMessageRepository extends JpaRepository<SmsMessage, Long> {

    @Modifying
    @Transactional
    @Query(value = "INSERT INTO cos_dashboard.messages (content, status, receiver_phone, receiver_full_name, unit, create_time) "
            +
            "SELECT :content, 0, u.phone, MAX(u.full_name), 'VTT', NOW() " +
            "FROM cos_monitor.alert_users u " +
            "WHERE u.phone IN (:phones) " +
            "AND u.deleted_at IS NULL " +
            "AND u.phone IS NOT NULL " +
            "AND u.phone <> '' " +
            "GROUP BY u.phone", nativeQuery = true)
    int insertMessagesByPhones(@Param("content") String content,
            @Param("phones") java.util.List<String> phones);
}
