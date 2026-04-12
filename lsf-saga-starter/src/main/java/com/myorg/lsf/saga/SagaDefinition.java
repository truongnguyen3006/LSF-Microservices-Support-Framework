package com.myorg.lsf.saga;

import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class SagaDefinition<S> {

    private final String name;
    private final Class<S> stateType;
    private final List<SagaStep<S>> steps;

    private SagaDefinition(String name, Class<S> stateType, List<SagaStep<S>> steps) {
        this.name = name;
        this.stateType = stateType;
        this.steps = List.copyOf(steps);
    }

    public String name() {
        return name;
    }

    public Class<S> stateType() {
        return stateType;
    }

    public List<SagaStep<S>> steps() {
        return steps;
    }

    public SagaStep<S> stepAt(int index) {
        return steps.get(index);
    }

    public static <S> Builder<S> builder(String name, Class<S> stateType) {
        return new Builder<>(name, stateType);
    }

    public static final class Builder<S> {
        private final String name;
        private final Class<S> stateType;
        private final List<SagaStep<S>> steps = new ArrayList<>();

        private Builder(String name, Class<S> stateType) {
            Assert.isTrue(StringUtils.hasText(name), "definition name must not be blank");
            Assert.notNull(stateType, "stateType must not be null");
            this.name = name;
            this.stateType = stateType;
        }

        public Builder<S> step(SagaStep<S> step) {
            this.steps.add(step);
            return this;
        }

        public SagaDefinition<S> build() {
            Assert.notEmpty(steps, "saga definition must contain at least one step");
            Set<String> names = new HashSet<>();
            for (SagaStep<S> step : steps) {
                Assert.isTrue(names.add(step.name()), "Duplicate saga step name: " + step.name());
            }
            return new SagaDefinition<>(name, stateType, steps);
        }
    }
}
