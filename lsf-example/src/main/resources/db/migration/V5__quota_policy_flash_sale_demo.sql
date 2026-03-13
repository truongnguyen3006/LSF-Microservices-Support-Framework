INSERT INTO quota_policy (quota_key, quota_limit, hold_seconds, enabled)
VALUES
    ('shopA:flashsale_sku:IPHONE15-128-BLACK', 5, 180, 1),
    ('shopA:flashsale_sku:AIRPODS-PRO-2', 3, 120, 1)
    ON DUPLICATE KEY UPDATE
                         quota_limit=VALUES(quota_limit),
                         hold_seconds=VALUES(hold_seconds),
                         enabled=VALUES(enabled);
