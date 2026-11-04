package com.techasians.alertservice.repository;

import com.techasians.alertservice.entity.NotificationChannel;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface NotificationChannelRepository extends JpaRepository<NotificationChannel, Integer> {

    // Tìm NotificationChannel theo tên (không phân biệt hoa/thường) — VD: "SMS", "Email"
    Optional<NotificationChannel> findByNameIgnoreCase(String name);
}

