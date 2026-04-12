package com.myorg.lsf.discovery;

import org.springframework.cloud.client.DefaultServiceInstance;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

public class LsfStaticDiscoveryClient implements DiscoveryClient {

    private final Map<String, List<ServiceInstance>> instancesByService;

    public LsfStaticDiscoveryClient(LsfDiscoveryProperties properties) {
        this.instancesByService = properties.getServices().entrySet().stream()
                .collect(Collectors.toUnmodifiableMap(
                        Map.Entry::getKey,
                        entry -> mapInstances(entry.getKey(), entry.getValue())
                ));
    }

    @Override
    public String description() {
        return "LSF static discovery client";
    }

    @Override
    public List<ServiceInstance> getInstances(String serviceId) {
        return instancesByService.getOrDefault(serviceId, List.of());
    }

    @Override
    public List<String> getServices() {
        return instancesByService.keySet().stream()
                .sorted(Comparator.naturalOrder())
                .toList();
    }

    private static List<ServiceInstance> mapInstances(String serviceId, List<LsfDiscoveryProperties.Instance> configured) {
        List<ServiceInstance> instances = new ArrayList<>();
        for (int i = 0; i < configured.size(); i++) {
            LsfDiscoveryProperties.Instance item = configured.get(i);
            String instanceId = serviceId + "-" + item.getHost() + "-" + item.getPort() + "-" + i;
            Map<String, String> metadata = new java.util.LinkedHashMap<>(item.getMetadata());
            String scheme = StringUtils.hasText(item.getScheme())
                    ? item.getScheme().trim().toLowerCase(Locale.ROOT)
                    : (item.isSecure() ? "https" : "http");
            metadata.putIfAbsent("scheme", scheme);
            if (StringUtils.hasText(item.getContextPath())) {
                metadata.putIfAbsent("contextPath", normalizeContextPath(item.getContextPath()));
            }
            DefaultServiceInstance serviceInstance = new DefaultServiceInstance(
                    instanceId,
                    serviceId,
                    item.getHost(),
                    item.getPort(),
                    item.isSecure(),
                    metadata
            );
            instances.add(serviceInstance);
        }
        return List.copyOf(instances);
    }

    private static String normalizeContextPath(String contextPath) {
        if (!StringUtils.hasText(contextPath)) {
            return "";
        }
        String normalized = contextPath.trim();
        if (!normalized.startsWith("/")) {
            normalized = "/" + normalized;
        }
        return normalized.endsWith("/") && normalized.length() > 1
                ? normalized.substring(0, normalized.length() - 1)
                : normalized;
    }
}
