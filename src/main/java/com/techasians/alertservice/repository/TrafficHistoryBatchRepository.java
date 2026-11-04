package com.techasians.alertservice.repository;

import com.techasians.alertservice.entity.TrafficHistory;
import com.techasians.alertservice.entity.TrafficHistoryEvent;
import com.techasians.alertservice.entity.TrafficHistoryMetricValue;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Repository thực hiện JDBC Batch INSERT thật sự.
 *
 * Tại sao cần class này?
 * - Hibernate saveAll() với @GeneratedValue(IDENTITY) KHÔNG batch được.
 *   Lý do: MySQL IDENTITY yêu cầu DB trả về ID sau MỖI INSERT -> không thể gom nhiều INSERT thành 1.
 * - JdbcTemplate.batchUpdate() với PreparedStatement cũng bị giới hạn tương tự.
 *
 * Giải pháp ở đây:
 * - INSERT nhiều rows trong 1 câu SQL: INSERT INTO ... VALUES (...),(...),(...)
 * - Sau đó query lại ID thật bằng unique key (region_channel_id, traffic_date).
 * - Không tự đoán ID bằng LAST_INSERT_ID() + i vì production có thể cấu hình
 *   auto_increment_increment khác 1, ví dụ ID tăng 2, 5, 8, 11...
 * - Gom từng CHUNK_SIZE rows thành 1 câu SQL để tránh packet quá lớn.
 */
@Repository
public class TrafficHistoryBatchRepository {

    // Số rows gom vào 1 câu INSERT. 500 là con số cân bằng giữa hiệu suất và kích thước packet.
    private static final int CHUNK_SIZE = 500;

    private final JdbcTemplate jdbcTemplate;

    public TrafficHistoryBatchRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    // ============================================================
    // INSERT traffic_histories và trả về list ID vừa insert
    // ============================================================

    /**
     * Insert danh sách TrafficHistory bằng multi-row INSERT.
     * Sau khi gọi xong, mỗi TrafficHistory trong list đã được gán ID bởi DB.
     *
     * Ví dụ:
     * Input:  [h1, h2, h3]  (id = null)
     * Sau call: mỗi history được query lại bằng region_channel_id + traffic_date
     * và được gán đúng ID thật do DB sinh ra.
     */
    public void batchInsertHistories(List<TrafficHistory> histories) {
        if (histories.isEmpty()) return;
        LocalDateTime now = LocalDateTime.now();

        // Chia thành từng chunk để tránh SQL quá dài
        // Ví dụ: 6265 histories / 500 = 13 chunks (12 chunk x 500 rows + 1 chunk x 265 rows)
        for (int start = 0; start < histories.size(); start += CHUNK_SIZE) {
            int end = Math.min(start + CHUNK_SIZE, histories.size());
            List<TrafficHistory> chunk = histories.subList(start, end);
            insertHistoryChunk(chunk, now);
        }
    }

    /**
     * Insert 1 chunk histories và gán ID thật vào từng entity.
     *
     * Cách cũ bị sai trên production:
     * - Lấy LAST_INSERT_ID() rồi tự cộng i.
     * - Cách này chỉ đúng khi auto_increment_increment = 1.
     * - Production đang có auto_increment_increment = 3 nên ID thật có thể là 2, 5, 8...
     *
     * Cách mới:
     * - Insert xong thì query lại traffic_histories bằng unique key:
     *   region_channel_id + traffic_date.
     * - Đây là khóa nghiệp vụ ổn định, không phụ thuộc cấu hình auto increment.
     */
    private void insertHistoryChunk(List<TrafficHistory> chunk, LocalDateTime now) {
        // Xây dựng câu SQL với nhiều VALUE groups:
        // INSERT INTO traffic_histories (...) VALUES (?,?,?,?,?),(?,?,?,?,?),(?,?,?,?,?)
        String sql = buildHistorySql(chunk.size());

        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(sql);
            int paramIndex = 1;
            for (TrafficHistory h : chunk) {
                ps.setLong(paramIndex++, h.getRegionChannel().getId());  // region_channel_id
                ps.setObject(paramIndex++, h.getTrafficDate());           // traffic_date (LocalDate)
                ps.setInt(paramIndex++, h.getHasEvent() != null ? h.getHasEvent() : 0); // has_event
                ps.setTimestamp(paramIndex++, Timestamp.valueOf(now));    // created_at
                ps.setString(paramIndex++, "import");                     // created_by
            }
            return ps;
        });

        assignRealHistoryIds(chunk);
    }

    /**
     * Query lại ID thật của các TrafficHistory vừa insert.
     *
     * Vì bảng traffic_histories có unique key trên (region_channel_id, traffic_date),
     * mỗi cặp này chỉ trả về đúng 1 history.
     */
    private void assignRealHistoryIds(List<TrafficHistory> histories) {
        String sql = buildFindHistoryIdsSql(histories.size());
        Map<String, Long> historyIdMap = new HashMap<>();

        jdbcTemplate.query(connection -> {
            PreparedStatement ps = connection.prepareStatement(sql);
            int paramIndex = 1;
            for (TrafficHistory history : histories) {
                ps.setLong(paramIndex++, history.getRegionChannel().getId());
                ps.setObject(paramIndex++, history.getTrafficDate());
            }
            return ps;
        }, resultSet -> {
            Long historyId = resultSet.getLong("id");
            Long regionChannelId = resultSet.getLong("region_channel_id");
            LocalDate trafficDate = resultSet.getObject("traffic_date", LocalDate.class);
            historyIdMap.put(buildHistoryLookupKey(regionChannelId, trafficDate), historyId);
        });

        for (TrafficHistory history : histories) {
            Long regionChannelId = history.getRegionChannel().getId();
            LocalDate trafficDate = history.getTrafficDate();
            String lookupKey = buildHistoryLookupKey(regionChannelId, trafficDate);
            Long realHistoryId = historyIdMap.get(lookupKey);

            if (realHistoryId == null) {
                throw new IllegalStateException(
                        "Không tìm thấy ID thật của traffic history sau khi insert: " + lookupKey);
            }

            history.setId(realHistoryId);
        }
    }

    private String buildFindHistoryIdsSql(int rowCount) {
        StringBuilder sql = new StringBuilder();
        sql.append("SELECT id, region_channel_id, traffic_date ")
           .append("FROM traffic_histories ")
           .append("WHERE (region_channel_id, traffic_date) IN (");

        for (int i = 0; i < rowCount; i++) {
            if (i > 0) {
                sql.append(", ");
            }
            sql.append("(?, ?)");
        }

        sql.append(")");
        return sql.toString();
    }

    private String buildHistoryLookupKey(Long regionChannelId, LocalDate trafficDate) {
        return regionChannelId + "|" + trafficDate;
    }

    /** Tạo chuỗi SQL INSERT với N nhóm VALUES */
    private String buildHistorySql(int rowCount) {
        // Kết quả: INSERT INTO traffic_histories (region_channel_id, traffic_date, has_event, created_at, created_by)
        //          VALUES (?,?,?,?,?),(?,?,?,?,?),...
        StringBuilder sb = new StringBuilder();
        sb.append("INSERT INTO traffic_histories ")
          .append("(region_channel_id, traffic_date, has_event, created_at, created_by) VALUES ");
        for (int i = 0; i < rowCount; i++) {
            if (i > 0) sb.append(",");
            sb.append("(?,?,?,?,?)");
        }
        return sb.toString();
    }

    // ============================================================
    // INSERT traffic_history_metric_values
    // ============================================================

    /**
     * Insert danh sách TrafficHistoryMetricValue bằng JDBC batch thật sự.
     * Lúc này trafficHistory đã có ID (do batchInsertHistories đã set).
     */
    public void batchInsertMetricValues(List<TrafficHistoryMetricValue> metricValues) {
        if (metricValues.isEmpty()) return;
        LocalDateTime now = LocalDateTime.now();

        // Dùng JdbcTemplate.batchUpdate() cho metricValues vì không cần lấy generated ID
        // (ID của metricValue không dùng ở bước tiếp theo)
        for (int start = 0; start < metricValues.size(); start += CHUNK_SIZE) {
            int end = Math.min(start + CHUNK_SIZE, metricValues.size());
            List<TrafficHistoryMetricValue> chunk = metricValues.subList(start, end);
            insertMetricValueChunk(chunk, now);
        }
    }

    private void insertMetricValueChunk(List<TrafficHistoryMetricValue> chunk, LocalDateTime now) {
        String sql = buildMetricValueSql(chunk.size());

        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(sql);
            int paramIndex = 1;
            for (TrafficHistoryMetricValue mv : chunk) {
                ps.setLong(paramIndex++, mv.getTrafficHistory().getId());   // traffic_history_id
                ps.setLong(paramIndex++, mv.getChannelMetric().getId());     // channel_metric_id
                ps.setBigDecimal(paramIndex++, mv.getMetricValue());         // metric_value
                ps.setTimestamp(paramIndex++, Timestamp.valueOf(now));       // created_at
                ps.setString(paramIndex++, "import");                        // created_by
            }
            return ps;
        });
    }

    private String buildMetricValueSql(int rowCount) {
        StringBuilder sb = new StringBuilder();
        sb.append("INSERT INTO traffic_history_metric_values ")
          .append("(traffic_history_id, channel_metric_id, metric_value, created_at, created_by) VALUES ");
        for (int i = 0; i < rowCount; i++) {
            if (i > 0) sb.append(",");
            sb.append("(?,?,?,?,?)");
        }
        return sb.toString();
    }

    // ============================================================
    // INSERT traffic_history_events
    // ============================================================

    /**
     * Insert danh sách TrafficHistoryEvent bằng JDBC batch thật sự.
     */
    public void batchInsertEvents(List<TrafficHistoryEvent> events) {
        if (events.isEmpty()) return;
        LocalDateTime now = LocalDateTime.now();

        for (int start = 0; start < events.size(); start += CHUNK_SIZE) {
            int end = Math.min(start + CHUNK_SIZE, events.size());
            List<TrafficHistoryEvent> chunk = events.subList(start, end);
            insertEventChunk(chunk, now);
        }
    }

    private void insertEventChunk(List<TrafficHistoryEvent> chunk, LocalDateTime now) {
        String sql = buildEventSql(chunk.size());

        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(sql);
            int paramIndex = 1;
            for (TrafficHistoryEvent ev : chunk) {
                ps.setLong(paramIndex++, ev.getTrafficHistory().getId());   // traffic_history_id
                ps.setLong(paramIndex++, ev.getTrafficEvent().getId());      // traffic_event_id
                ps.setTimestamp(paramIndex++, Timestamp.valueOf(now));       // created_at
                ps.setString(paramIndex++, "import");                        // created_by
            }
            return ps;
        });
    }

    private String buildEventSql(int rowCount) {
        StringBuilder sb = new StringBuilder();
        sb.append("INSERT INTO traffic_history_events ")
          .append("(traffic_history_id, traffic_event_id, created_at, created_by) VALUES ");
        for (int i = 0; i < rowCount; i++) {
            if (i > 0) sb.append(",");
            sb.append("(?,?,?,?)");
        }
        return sb.toString();
    }
}
