package com.cheeseocean.im.common.core.queue.config;

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
        Class<?> payloadType = method.getParameterTypes()[0];
        queueAdapter.subscribe(
                listener.topic(),
                listener.group(),
                listener.concurrency(),
                payloadType,
                createHandler(bean, method)
        );
    }

    @SuppressWarnings("unchecked")
    private <T> QueueMessageHandler<T> createHandler(Object bean, Method method) {
        return message -> ReflectionUtils.invokeMethod(method, bean, message);
    }
}
