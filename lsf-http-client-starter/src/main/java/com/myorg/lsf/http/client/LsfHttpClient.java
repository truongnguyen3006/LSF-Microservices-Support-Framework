package com.myorg.lsf.http.client;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface LsfHttpClient {

    String serviceId();

    String pathPrefix() default "";

    String resilienceId() default "";

    LsfClientAuthMode authMode() default LsfClientAuthMode.AUTO;
}
