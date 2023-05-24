package com.cheeseocean.im.common;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.support.AbstractApplicationContext;

import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

/**
 * @author xxxcrel
 * @date 2023/5/24 14:08
 */
@Slf4j
public class ApplicationKeeper {

    private final ReentrantLock LOCK = new ReentrantLock();
    private final Condition STOP = LOCK.newCondition();

    public ApplicationKeeper(AbstractApplicationContext applicationContext) {
        addShutdownHook(applicationContext);
    }

    private void addShutdownHook(final AbstractApplicationContext applicationContext) {
        Runtime.getRuntime().addShutdownHook(new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    applicationContext.close();
                    log.info("ApplicationContext " + applicationContext + " is closed.");
                } catch (Exception e) {
                    log.error("Failed to close ApplicationContext", e);
                }

                try {
                    LOCK.lock();
                    STOP.signal();
                } finally {
                    LOCK.unlock();
                }
            }
        }));
    }

    /**
     * Keep.
     */
    public void keep() {
        LOCK.lock();
        try {
            log.info("Application is keep running ... ");
            STOP.await();
        } catch (InterruptedException e) {
            log.error("interrupted error ", e);
        } finally {
            LOCK.unlock();
        }

    }
}