-- Lưu “ai đang giữ quyền xử lý” record (tên instance/pod/hostname, ví dụ publisher-1)
ALTER TABLE lsf_outbox ADD COLUMN lease_owner VARCHAR(128) NULL;
-- Thời điểm “hết hạn lease”. lease_until = now + leaseDuration
ALTER TABLE lsf_outbox ADD COLUMN lease_until TIMESTAMP NULL;
-- Thời điểm được phép thử publish lại
ALTER TABLE lsf_outbox ADD COLUMN next_attempt_at TIMESTAMP NULL;
-- Index này giúp DB lọc nhanh theo (status, lease_until).
CREATE INDEX idx_lsf_outbox_lease ON lsf_outbox (status, lease_until);
-- Lấy record đến hạn retry:
CREATE INDEX idx_lsf_outbox_next_attempt ON lsf_outbox (status, next_attempt_at);
