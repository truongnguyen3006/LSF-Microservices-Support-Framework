package com.myorg.lsf.http.client;

import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.annotation.AnnotatedBeanDefinition;
import org.springframework.context.EnvironmentAware;
import org.springframework.context.ResourceLoaderAware;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.context.annotation.ImportBeanDefinitionRegistrar;
import org.springframework.core.env.Environment;
import org.springframework.core.io.ResourceLoader;
import org.springframework.core.type.AnnotationMetadata;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.util.ClassUtils;
import org.springframework.util.StringUtils;

import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

public class LsfHttpClientRegistrar implements ImportBeanDefinitionRegistrar, ResourceLoaderAware, EnvironmentAware {

    private ResourceLoader resourceLoader;
    private Environment environment;

    @Override
    public void registerBeanDefinitions(AnnotationMetadata importingClassMetadata, BeanDefinitionRegistry registry) {
        Map<String, Object> attributes = importingClassMetadata
                .getAnnotationAttributes(EnableLsfHttpClients.class.getName(), false);
        if (attributes == null) {
            return;
        }

        Set<String> basePackages = new LinkedHashSet<>();
        for (String basePackage : (String[]) attributes.get("basePackages")) {
            if (StringUtils.hasText(basePackage)) {
                basePackages.add(basePackage);
            }
        }
        for (Class<?> basePackageClass : (Class<?>[]) attributes.get("basePackageClasses")) {
            basePackages.add(ClassUtils.getPackageName(basePackageClass));
        }
        if (basePackages.isEmpty()) {
            basePackages.add(ClassUtils.getPackageName(importingClassMetadata.getClassName()));
        }

        ClassPathScanningCandidateComponentProvider scanner = new ClassPathScanningCandidateComponentProvider(false, environment) {
            @Override
            protected boolean isCandidateComponent(AnnotatedBeanDefinition beanDefinition) {
                return beanDefinition.getMetadata().isInterface() && beanDefinition.getMetadata().isIndependent();
            }
        };
        scanner.setResourceLoader(resourceLoader);
        scanner.addIncludeFilter(new AnnotationTypeFilter(LsfHttpClient.class));

        for (String basePackage : basePackages) {
            for (BeanDefinition candidate : scanner.findCandidateComponents(basePackage)) {
                registerClient(candidate, registry);
            }
        }
    }

    private void registerClient(BeanDefinition candidate, BeanDefinitionRegistry registry) {
        String className = candidate.getBeanClassName();
        if (!StringUtils.hasText(className) || registry.containsBeanDefinition(className)) {
            return;
        }

        BeanDefinitionBuilder builder = BeanDefinitionBuilder.genericBeanDefinition(LsfHttpClientFactoryBean.class);
        builder.addPropertyValue("typeName", className);
        registry.registerBeanDefinition(className, builder.getBeanDefinition());
    }

    @Override
    public void setResourceLoader(ResourceLoader resourceLoader) {
        this.resourceLoader = resourceLoader;
    }

    @Override
    public void setEnvironment(Environment environment) {
        this.environment = environment;
    }
}
