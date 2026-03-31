package com.cheeseocean.im.common.core.business.mongo.config;

import org.springframework.context.annotation.Import;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Explicitly enables the shared Mongo persistence layer provided by common-core.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Import(CommonMongoPersistenceConfiguration.class)
public @interface EnableCommonMongoPersistence {
}
