package com.myorg.lsf.discovery;

import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.cloud.client.discovery.ReactiveDiscoveryClient;
import reactor.core.publisher.Flux;

public class LsfStaticReactiveDiscoveryClient implements ReactiveDiscoveryClient {

    private final DiscoveryClient delegate;

    public LsfStaticReactiveDiscoveryClient(DiscoveryClient delegate) {
        this.delegate = delegate;
    }

    @Override
    public String description() {
        return delegate.description();
    }

    @Override
    public Flux<ServiceInstance> getInstances(String serviceId) {
        return Flux.fromIterable(delegate.getInstances(serviceId));
    }

    @Override
    public Flux<String> getServices() {
        return Flux.fromIterable(delegate.getServices());
    }
}
