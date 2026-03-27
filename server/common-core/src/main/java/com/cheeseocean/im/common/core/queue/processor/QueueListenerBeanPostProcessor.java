package com.cheeseocean.im.common.core.queue.processor;

import com.cheeseocean.im.common.core.queue.BatchingMessageHandler;
import com.cheeseocean.im.common.core.queue.QueueAdapter;
import com.cheeseocean.im.common.core.queue.QueueMessageHandler;
import com.cheeseocean.im.common.core.queue.annotation.QueueListener;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.BeanFactoryAware;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.core.MethodIntrospector;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.util.ReflectionUtils;

import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.List;
import java.util.Map;

public class QueueListenerBeanPostProcessor implements BeanPostProcessor, ApplicationContextAware, BeanFactoryAware {

    private ApplicationContext applicationContext;
    @SuppressWarnings("unused")
    private BeanFactory beanFactory;

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) {
        this.applicationContext = applicationContext;
    }

    @Override
    public void setBeanFactory(BeanFactory beanFactory) {
        this.beanFactory = beanFactory;
    }

    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
        Map<Method, QueueListener> listenerMethods = MethodIntrospector.selectMethods(
                bean.getClass(),
                (MethodIntrospector.MetadataLookup<QueueListener>) method ->
                        AnnotatedElementUtils.findMergedAnnotation(method, QueueListener.class)
        );

        if (listenerMethods.isEmpty()) {
            return bean;
        }

        QueueAdapter queueAdapter = applicationContext.getBean(QueueAdapter.class);
        listenerMethods.forEach((method, listener) -> registerListener(bean, method, listener, queueAdapter));
        return bean;
    }

    private void registerListener(Object bean, Method method, QueueListener listener, QueueAdapter queueAdapter) {
        if (method.getParameterCount() != 1) {
            throw new IllegalStateException("@QueueListener methods must declare exactly one payload parameter: " + method);
        }

        ReflectionUtils.makeAccessible(method);

        if (listener.batch()) {
            registerBatchListener(bean, method, listener, queueAdapter);
        } else {
            Class<?> payloadType = method.getParameterTypes()[0];
            queueAdapter.subscribe(
                    listener.topic(),
                    listener.group(),
                    listener.concurrency(),
                    payloadType,
                    createHandler(bean, method)
            );
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private void registerBatchListener(Object bean, Method method, QueueListener listener, QueueAdapter queueAdapter) {
        Type paramType = method.getGenericParameterTypes()[0];
        if (!(paramType instanceof ParameterizedType pt) || !List.class.isAssignableFrom((Class<?>) pt.getRawType())) {
            throw new IllegalStateException(
                    "@QueueListener(batch=true) method must declare a List<T> parameter: " + method);
        }
        Class<?> elementType = (Class<?>) pt.getActualTypeArguments()[0];
        BatchingMessageHandler batchHandler = new BatchingMessageHandler(
                listener.batchSize(),
                listener.batchIntervalMs(),
                listener.concurrency(),
                list -> ReflectionUtils.invokeMethod(method, bean, list)
        );
        queueAdapter.subscribeKeyed(
                listener.topic(),
                listener.group(),
                listener.concurrency(),
                elementType,
                batchHandler
        );
    }

    @SuppressWarnings("unchecked")
    private <T> QueueMessageHandler<T> createHandler(Object bean, Method method) {
        return message -> ReflectionUtils.invokeMethod(method, bean, message);
    }
}
