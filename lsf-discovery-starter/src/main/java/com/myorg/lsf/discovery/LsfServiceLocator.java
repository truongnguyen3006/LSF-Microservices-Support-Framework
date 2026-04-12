package com.myorg.lsf.discovery;

import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.discovery.DiscoveryClient;

import java.net.URI;
import java.util.Comparator;
import java.util.List;

public class LsfServiceLocator {

    private final DiscoveryClient discoveryClient;

    public LsfServiceLocator(DiscoveryClient discoveryClient) {
        this.discoveryClient = discoveryClient;
    }

    public List<ServiceInstance> getInstances(String serviceId) {
        return discoveryClient.getInstances(serviceId).stream()
                .sorted(Comparator.comparing(ServiceInstance::getInstanceId, Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();
    }

    public ServiceInstance getRequiredInstance(String serviceId) {
        return getInstances(serviceId).stream()
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("No service instance found for serviceId=" + serviceId));
    }

    public URI getRequiredUri(String serviceId) {
        return getRequiredInstance(serviceId).getUri();
    }
}
