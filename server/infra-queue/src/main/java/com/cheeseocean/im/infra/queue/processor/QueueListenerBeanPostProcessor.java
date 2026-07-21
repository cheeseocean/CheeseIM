package com.cheeseocean.im.infra.queue.processor;

import com.cheeseocean.im.common.core.queue.KeyedMessage;
import com.cheeseocean.im.common.core.queue.QueueAdapter;
import com.cheeseocean.im.common.core.queue.QueueMessageHandler;
import com.cheeseocean.im.common.core.queue.Subscription;
import com.cheeseocean.im.common.core.queue.annotation.QueueListener;
import com.cheeseocean.im.infra.queue.config.QueueProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.core.MethodIntrospector;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.util.ReflectionUtils;

import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class QueueListenerBeanPostProcessor implements BeanPostProcessor, ApplicationContextAware, DisposableBean {

    private static final Logger logger = LoggerFactory.getLogger(QueueListenerBeanPostProcessor.class);

    private ApplicationContext applicationContext;
    private final List<Subscription> subscriptions = new ArrayList<>();
    private boolean destroyed;

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) {
        this.applicationContext = applicationContext;
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
        QueueProperties queueProperties = applicationContext.getBeanProvider(QueueProperties.class)
                .getIfAvailable(QueueProperties::new);
        QueueProperties.ListenerSettings settings = queueProperties.resolveListener(
                listener.group(),
                listener.concurrency(),
                listener.batchSize(),
                listener.batchIntervalMs());

        if (listener.batch()) {
            registerBatchListener(bean, method, listener, settings, queueAdapter);
        } else {
            Class<?> payloadType = method.getParameterTypes()[0];
            track(queueAdapter.subscribe(
                    listener.topic(),
                    listener.group(),
                    settings.concurrency(),
                    payloadType,
                    createHandler(bean, method)
            ));
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private void registerBatchListener(Object bean,
                                       Method method,
                                       QueueListener listener,
                                       QueueProperties.ListenerSettings settings,
                                       QueueAdapter queueAdapter) {
        Type paramType = method.getGenericParameterTypes()[0];
        if (!(paramType instanceof ParameterizedType pt) || !List.class.isAssignableFrom((Class<?>) pt.getRawType())) {
            throw new IllegalStateException(
                    "@QueueListener(batch=true) method must declare a List<T> parameter: " + method);
        }
        Class<?> elementType = (Class<?>) pt.getActualTypeArguments()[0];
        track(queueAdapter.subscribeBatch(
                listener.topic(),
                listener.group(),
                settings.concurrency(),
                settings.batchSize(),
                settings.batchIntervalMillis(),
                elementType,
                messages -> {
                    Map<String, List<Object>> grouped = new LinkedHashMap<>();
                    for (KeyedMessage<?> message : messages) {
                        grouped.computeIfAbsent(message.key(), ignored -> new java.util.ArrayList<>())
                                .add(message.payload());
                    }
                    grouped.values().forEach(list -> ReflectionUtils.invokeMethod(method, bean, list));
                }
        ));
    }

    private synchronized void track(Subscription subscription) {
        if (destroyed) {
            subscription.unsubscribe();
            throw new IllegalStateException("Queue listener runtime is already stopping");
        }
        subscriptions.add(subscription);
    }

    /**
     * Spring 关闭上下文时逆序停止全部订阅，确保 Kafka container/Chronicle poller 不残留后台线程。
     */
    @Override
    public void destroy() {
        List<Subscription> active;
        synchronized (this) {
            if (destroyed) {
                return;
            }
            destroyed = true;
            active = new ArrayList<>(subscriptions);
            subscriptions.clear();
        }
        Collections.reverse(active);
        for (Subscription subscription : active) {
            try {
                subscription.unsubscribe();
            } catch (RuntimeException exception) {
                logger.warn("Failed to stop queue subscription during context shutdown", exception);
            }
        }
    }

    @SuppressWarnings("unchecked")
    private <T> QueueMessageHandler<T> createHandler(Object bean, Method method) {
        return message -> ReflectionUtils.invokeMethod(method, bean, message);
    }
}
