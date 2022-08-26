package com.cheeseocean.im.push;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

public class PushApplication {
    private static final Logger logger = LoggerFactory.getLogger(PushApplication.class);

    private static ScheduledExecutorService scheduledExecutorService;
    private static final ReentrantLock LOCK = new ReentrantLock();

    private static final Condition STOP = LOCK.newCondition();

    public static void main(String[] args) throws InterruptedException {
        try {
            Runtime.getRuntime().addShutdownHook(new Thread() {
                @Override
                public void run() {
                    try {
                        LOCK.lock();
                        STOP.signal();
                    } finally {
                        LOCK.unlock();
                    }
                    logger.info("stop push application");
                }
            });
            logger.info("start push application successfull");
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
