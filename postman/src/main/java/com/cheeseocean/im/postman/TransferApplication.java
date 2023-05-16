package com.cheeseocean.im.postman;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

public class TransferApplication {
    private static final Logger logger = LoggerFactory.getLogger(TransferApplication.class);

    private static final ReentrantLock LOCK = new ReentrantLock();

    private static final Condition STOP = LOCK.newCondition();

    public static void main(String[] args) {
        try {

            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                try {
                    LOCK.lock();
                    STOP.signal();
                } finally {
                    LOCK.unlock();
                }
                logger.info("stop push application");
            }));
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