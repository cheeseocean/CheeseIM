package com.cheeseocean.im.common.core.queue.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Documented
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface QueueListener {

    String topic();

    String group();

    int concurrency() default 1;

    boolean batch() default false;

    int batchSize() default 100;

    long batchIntervalMs() default 500;
}
