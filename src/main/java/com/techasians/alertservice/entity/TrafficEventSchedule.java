package com.techasians.alertservice.entity;

import javax.persistence.*;
import java.io.Serializable;
import java.time.LocalDate;

/**
 * Entity map bảng traffic_event_schedules.
 * Lưu lịch diễn ra của sự kiện — mỗi bản ghi là 1 ngày hoặc 1 quy luật.
 *
 * Cách sử dụng các field theo loại sự kiện:
 * - Phát sinh (event_type=2):              lưu exact_date
 * - Định kỳ không quy luật (pattern_type=2): lưu exact_date
 * - Định kỳ có quy luật / Hằng tháng:     lưu day_of_month
 * - Định kỳ có quy luật / Hằng năm:       lưu day_of_month + month_of_year
 * - Định kỳ có quy luật / Hằng quý:       lưu day_of_month + month_of_year
 */
@Entity
@Table(
    name = "traffic_event_schedules",
    indexes = @Index(name = "idx_schedule_event", columnList = "event_id")
)
public class TrafficEventSchedule extends BaseAuditEntity<TrafficEventSchedule> implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "event_id", nullable = false)
    private TrafficEvent event;

    // Ngày chính xác — dùng cho phát sinh và định kỳ không quy luật
    @Column(name = "exact_date")
    private LocalDate exactDate;

    // Ngày trong tháng (1–31) — dùng cho định kỳ có quy luật
    @Column(name = "day_of_month")
    private Integer dayOfMonth;

    // Tháng trong năm (1–12) — dùng thêm khi chu kỳ hằng năm hoặc hằng quý có quy luật
    @Column(name = "month_of_year")
    private Integer monthOfYear;

    public Long getId() {
        return id;
    }

    public TrafficEventSchedule setId(Long id) {
        this.id = id;
        return this;
    }

    public TrafficEvent getEvent() {
        return event;
    }

    public TrafficEventSchedule setEvent(TrafficEvent event) {
        this.event = event;
        return this;
    }

    public LocalDate getExactDate() {
        return exactDate;
    }

    public TrafficEventSchedule setExactDate(LocalDate exactDate) {
        this.exactDate = exactDate;
        return this;
    }

    public Integer getDayOfMonth() {
        return dayOfMonth;
    }

    public TrafficEventSchedule setDayOfMonth(Integer dayOfMonth) {
        this.dayOfMonth = dayOfMonth;
        return this;
    }

    public Integer getMonthOfYear() {
        return monthOfYear;
    }

    public TrafficEventSchedule setMonthOfYear(Integer monthOfYear) {
        this.monthOfYear = monthOfYear;
        return this;
    }
}
