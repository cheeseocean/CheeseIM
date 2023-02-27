package com.cheeseocean.im.gateway;

import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Configuration;

@Configuration
public class GatewayApplication {
    private static final Logger logger = LoggerFactory.getLogger(GatewayApplication.class);

    private static final ReentrantLock LOCK = new ReentrantLock();

    private static final Condition STOP = LOCK.newCondition();

    public static void main(String[] args) throws InterruptedException {
        try {
            AnnotationConfigApplicationContext ctx = new AnnotationConfigApplicationContext(GatewayApplication.class);
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                ctx.stop();
                try {
                    LOCK.lock();
                    STOP.signal();
                } finally {
                    LOCK.unlock();
                }
                logger.info("stop push application");
            }));
            ctx.start();
            logger.info("start push application successful");
        } catch (Exception e) {
            logger.error("start push application error", e);
            System.exit(1);
        }
        try {
            LOCK.lock();
            STOP.await();
        } catch (InterruptedException e) {
            logger.warn("Dubbo service server stopped, interrupted by other thread!", e);
        } finally {
            LOCK.unlock();
        }
    }
}
