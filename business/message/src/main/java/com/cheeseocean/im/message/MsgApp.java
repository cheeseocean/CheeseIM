package com.cheeseocean.im.message;

import com.cheeseocean.im.message.config.AppConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

/**
 * @author xxxcrel
 * Created on 2023/4/17
 */
@Slf4j
public class MsgApp {
    private static final ReentrantLock LOCK = new ReentrantLock();

    private static final Condition STOP = LOCK.newCondition();

    public static void main(String[] args) throws InterruptedException {
        try {
            AnnotationConfigApplicationContext ctx = new AnnotationConfigApplicationContext(AppConfig.class);
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                ctx.stop();
                try {
                    LOCK.lock();
                    STOP.signal();
                } finally {
                    LOCK.unlock();
                }
                log.info("stop push application");
            }));
            ctx.start();
            log.info("start push application successful");
        } catch (Exception e) {
            log.error("start push application error", e);
            System.exit(1);
        }
        try {
            LOCK.lock();
            STOP.await();
        } catch (InterruptedException e) {
            log.warn("Dubbo service server stopped, interrupted by other thread!", e);
        } finally {
            LOCK.unlock();
        }
    }
}