package com.myorg.lsf.saga;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

public class SagaDefinitionRegistry {

    private final Map<String, SagaDefinition<?>> definitions = new LinkedHashMap<>();

    public SagaDefinitionRegistry() {
    }

    public SagaDefinitionRegistry(Collection<SagaDefinition<?>> definitions) {
        definitions.forEach(this::register);
    }

    public void register(SagaDefinition<?> definition) {
        if (definition == null) {
            throw new IllegalArgumentException("definition must not be null");
        }
        SagaDefinition<?> previous = definitions.putIfAbsent(definition.name(), definition);
        if (previous != null) {
            throw new IllegalStateException("Duplicate saga definition: " + definition.name());
        }
    }

    @SuppressWarnings("unchecked")
    public <S> SagaDefinition<S> getRequired(String name) {
        SagaDefinition<?> definition = definitions.get(name);
        if (definition == null) {
            throw new IllegalArgumentException("No saga definition registered for name=" + name);
        }
        return (SagaDefinition<S>) definition;
    }

    public Collection<SagaDefinition<?>> definitions() {
        return definitions.values();
    }
}
