-- =============================================================
-- Phase 2: File lifecycle — báo cáo tuần
-- Ngày: 2026-09-04
-- Mục đích: Thêm index phục vụ query thường dùng trong Phase 2.
--            Không thêm FK để tránh khóa table trên dữ liệu lớn.
-- =============================================================

-- 1. Index tuần + trạng thái trên rpt_instance
ALTER TABLE rpt_instance
    ADD INDEX idx_rpt_instance_week_status (week_start_date, status);

-- 2. Index instance + thời gian trên rpt_audit_log
ALTER TABLE rpt_audit_log
    ADD INDEX idx_rpt_audit_log_instance_time (report_instance_id, created_at);

-- 3. Index tuần trên rpt_audit_log
ALTER TABLE rpt_audit_log
    ADD INDEX idx_rpt_audit_log_week (week_start_date);
