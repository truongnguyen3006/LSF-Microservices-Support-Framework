package com.myorg.lsf.contracts.core.context;

import java.util.LinkedHashMap;
import java.util.Map;

public record LsfTraceContext(Map<String, String> headers) {

    public LsfTraceContext {
        headers = headers == null ? Map.of() : Map.copyOf(new LinkedHashMap<>(headers));
    }

    public boolean isEmpty() {
        return headers.isEmpty();
    }

    public String header(String name) {
        return headers.get(name);
    }
}
