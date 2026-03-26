ALTER TABLE lsf_outbox
    MODIFY created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3);

ALTER TABLE lsf_outbox
    MODIFY sent_at TIMESTAMP(3) NULL;

ALTER TABLE lsf_outbox
    MODIFY lease_until TIMESTAMP(3) NULL;

ALTER TABLE lsf_outbox
    MODIFY next_attempt_at TIMESTAMP(3) NULL;