package com.myorg.lsf.discovery;

import org.springframework.boot.autoconfigure.condition.ConditionOutcome;
import org.springframework.boot.autoconfigure.condition.SpringBootCondition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.EnumerablePropertySource;
import org.springframework.core.env.PropertySource;
import org.springframework.core.type.AnnotatedTypeMetadata;

final class StaticDiscoveryCondition extends SpringBootCondition {

    @Override
    public ConditionOutcome getMatchOutcome(ConditionContext context, AnnotatedTypeMetadata metadata) {
        String mode = context.getEnvironment().getProperty("lsf.discovery.mode", "AUTO");
        if ("DISABLED".equalsIgnoreCase(mode) || "REQUIRED".equalsIgnoreCase(mode)) {
            return ConditionOutcome.noMatch("lsf.discovery.mode does not allow static discovery");
        }
        if ("STATIC".equalsIgnoreCase(mode)) {
            return ConditionOutcome.match("lsf.discovery.mode=STATIC");
        }
        if (hasStaticServiceEntries(context.getEnvironment())) {
            return ConditionOutcome.match("Found lsf.discovery.services.* entries");
        }
        return ConditionOutcome.noMatch("No static discovery services configured");
    }

    static boolean hasStaticServiceEntries(org.springframework.core.env.Environment environment) {
        if (!(environment instanceof ConfigurableEnvironment configurableEnvironment)) {
            return false;
        }
        for (PropertySource<?> propertySource : configurableEnvironment.getPropertySources()) {
            if (propertySource instanceof EnumerablePropertySource<?> enumerable) {
                for (String name : enumerable.getPropertyNames()) {
                    if (name.startsWith("lsf.discovery.services")) {
                        return true;
                    }
                }
            }
        }
        return false;
    }
}
