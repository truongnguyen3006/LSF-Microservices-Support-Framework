-- Outbox table (MySQL/InnoDB)
-- Stores the entire EventEnvelope as JSON alongside indexed fields.

CREATE TABLE IF NOT EXISTS lsf_outbox (
    -- định danh & routing
    id BIGINT NOT NULL AUTO_INCREMENT,
    topic VARCHAR(255) NOT NULL,
    msg_key VARCHAR(255) NULL,

    -- business identity của event
    event_id VARCHAR(100) NOT NULL,
    event_type VARCHAR(200) NOT NULL,
    correlation_id VARCHAR(100) NULL, -- để trace luồng nghiệp vụ xuyên service (request → event → event…)
    aggregate_id VARCHAR(100) NULL,-- id thực thể chính (orderId/bookingId) giúp debug, hoặc dùng làm msg_key.

    envelope_json LONGTEXT NOT NULL, -- Payload thực tế của event,
                                    -- Chứa toàn bộ EventEnvelope (metadata + payload) dạng JSON string.
    -- trạng thái, retry, audit vận hành
    status VARCHAR(20) NOT NULL DEFAULT 'NEW',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, -- thời điểm ghi outbox (dùng để order/poll).
    sent_at TIMESTAMP NULL, -- lúc publish thành công.
    retry_count INT NOT NULL DEFAULT 0, -- số lần publish fail đã thử.
    last_error TEXT NULL,

    PRIMARY KEY (id),
    UNIQUE KEY uk_lsf_outbox_event_id (event_id), -- chống ghi trùng
    KEY idx_lsf_outbox_status_created (status, created_at) -- Index này phục vụ query kiểu,
                                                    -- lấy các event NEW/FAILED theo thứ tự thời gian để xử lý
    )
    ENGINE=InnoDB;
