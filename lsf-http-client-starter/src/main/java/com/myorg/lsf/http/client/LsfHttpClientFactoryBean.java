package com.myorg.lsf.http.client;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.BeanClassLoaderAware;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.BeanFactoryAware;
import org.springframework.beans.factory.FactoryBean;
import org.springframework.util.ClassUtils;

public class LsfHttpClientFactoryBean implements FactoryBean<Object>, BeanFactoryAware, BeanClassLoaderAware {

    private String typeName;
    private BeanFactory beanFactory;
    private ClassLoader beanClassLoader;

    @Override
    public Object getObject() {
        Class<?> type = resolveType();
        return beanFactory.getBean(LsfHttpServiceClientFactory.class).createClient(type);
    }

    @Override
    public Class<?> getObjectType() {
        return typeName == null ? null : resolveType();
    }

    @Override
    public boolean isSingleton() {
        return true;
    }

    public void setTypeName(String typeName) {
        this.typeName = typeName;
    }

    @Override
    public void setBeanFactory(BeanFactory beanFactory) throws BeansException {
        this.beanFactory = beanFactory;
    }

    @Override
    public void setBeanClassLoader(ClassLoader classLoader) {
        this.beanClassLoader = classLoader;
    }

    private Class<?> resolveType() {
        return ClassUtils.resolveClassName(typeName, beanClassLoader);
    }
}
