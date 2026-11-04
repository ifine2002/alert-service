package com.techasians.alertservice.repository;

import com.techasians.alertservice.entity.TrafficEventSchedule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface TrafficEventScheduleRepository extends JpaRepository<TrafficEventSchedule, Long> {

    // Lấy tất cả schedule của 1 event — dùng cho diff-sync khi update
    List<TrafficEventSchedule> findByEventId(Integer eventId);

    // Load batch tất cả schedule của nhiều event cùng lúc — tránh N+1 trong buildScheduleMap()
    List<TrafficEventSchedule> findByEventIdIn(List<Integer> eventIds);

    // Tìm schedule theo event + ngày cụ thể — dùng trong diff-sync
    Optional<TrafficEventSchedule> findByEventIdAndExactDate(Integer eventId, LocalDate exactDate);
}
