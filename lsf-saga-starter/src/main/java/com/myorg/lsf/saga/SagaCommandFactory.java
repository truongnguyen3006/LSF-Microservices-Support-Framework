package com.myorg.lsf.saga;

@FunctionalInterface
public interface SagaCommandFactory<S> {

    SagaCommand create(SagaExecutionContext<S> context);
}
