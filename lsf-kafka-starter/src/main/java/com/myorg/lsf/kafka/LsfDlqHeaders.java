package com.myorg.lsf.kafka;
// Khai báo các hằng số (constants) và Enum để chuẩn hoá các thẻ (tags) đính kèm vào message khi gửi vào DLQ.
public final class LsfDlqHeaders {
    private LsfDlqHeaders() {}

    public static final String REASON = "lsf.dlq.reason";
    public static final String NON_RETRYABLE = "lsf.dlq.non_retryable";

    public static final String EXCEPTION_CLASS = "lsf.dlq.exception_class";
    public static final String EXCEPTION_MESSAGE = "lsf.dlq.exception_message";

    public static final String SERVICE = "lsf.dlq.service";
    public static final String TS_MS = "lsf.dlq.ts_ms";
    public static final String ORIGINAL_TOPIC = "lsf.dlq.original_topic";
    public static final String ORIGINAL_PARTITION = "lsf.dlq.original_partition";
    public static final String ORIGINAL_OFFSET = "lsf.dlq.original_offset";
}
