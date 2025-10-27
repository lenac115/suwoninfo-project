package com.main.suwoninfo.idempotent;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface Idempotent {

    String scope() default "";

    String user() default "";

    String key() default "";

    String content() default "";

    long ttlSeconds() default 1200L;

    long dedupeTtlSeconds() default 600L;

    boolean replayResponse() default true;
}
