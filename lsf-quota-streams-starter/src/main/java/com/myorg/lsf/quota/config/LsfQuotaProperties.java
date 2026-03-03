package com.myorg.lsf.quota.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Data
@ConfigurationProperties(prefix = "lsf.quota")
public class LsfQuotaProperties {
    private boolean enabled = true;
    /**
     * auto: nếu có RedisConnectionFactory -> redis, không có -> memory
     * redis: bắt buộc dùng redis
     * memory: dùng in-memory (dev/test)
     */
    private Store store = Store.AUTO;
    private String keyPrefix = "lsf:quota:";
    /** hold mặc định (seconds) nếu request không truyền hold */
    private int defaultHoldSeconds = 30;
    /** TTL “giữ key sống” để tránh key tồn tại mãi mãi (seconds) */
    private int keepAliveSeconds = 24 * 3600;
    /** Cho phép release confirmed (cancel) hay không, trả tiền rồi không cho hoàn */
    private boolean allowReleaseConfirmed = false;
    /** Metrics on/off */
    private boolean metricsEnabled = true;
    public enum Store { AUTO, REDIS, MEMORY }

    private List<PolicyItem> policies = new ArrayList<>();

    @Data
    public static class PolicyItem {
        private String key;          // full quotaKey string, keep ':'
        private int limit;
        private Integer holdSeconds;
    }

    @Data
    public static class Policy {
        private int limit;
        private Integer holdSeconds; // optional override
    }
}
