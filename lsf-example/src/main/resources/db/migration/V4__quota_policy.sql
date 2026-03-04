CREATE TABLE IF NOT EXISTS quota_policy (
                                            quota_key VARCHAR(255) NOT NULL,
    quota_limit INT NOT NULL,
    hold_seconds INT NULL,
    enabled TINYINT(1) NOT NULL DEFAULT 1,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (quota_key)
    );

INSERT INTO quota_policy (quota_key, quota_limit, hold_seconds, enabled)
VALUES
    ('uniA:course_section:CT101-2026HK2-NHOM1', 3, 60, 1),
    ('uniA:ticket:FLIGHT123-ECONOMY', 2, 120, 1)
    ON DUPLICATE KEY UPDATE
                         quota_limit=VALUES(quota_limit),
                         hold_seconds=VALUES(hold_seconds),
                         enabled=VALUES(enabled);