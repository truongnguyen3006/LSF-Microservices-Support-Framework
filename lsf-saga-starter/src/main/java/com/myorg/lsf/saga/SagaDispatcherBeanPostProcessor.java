package com.myorg.lsf.saga;

import com.myorg.lsf.eventing.LsfDispatcher;
import org.springframework.beans.factory.config.BeanPostProcessor;

public class SagaDispatcherBeanPostProcessor implements BeanPostProcessor {

    private final LsfSagaOrchestrator orchestrator;
    private final LsfSagaProperties properties;

    public SagaDispatcherBeanPostProcessor(LsfSagaOrchestrator orchestrator, LsfSagaProperties properties) {
        this.orchestrator = orchestrator;
        this.properties = properties;
    }

    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) {
        if (!properties.isObserveDispatch()) {
            return bean;
        }
        if (!(bean instanceof LsfDispatcher dispatcher)) {
            return bean;
        }
        if (bean instanceof SagaAwareLsfDispatcher) {
            return bean;
        }
        return new SagaAwareLsfDispatcher(dispatcher, orchestrator, properties.isConsumeMatchingEvents());
    }
}
