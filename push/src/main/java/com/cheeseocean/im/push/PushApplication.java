package com.cheeseocean.im.push;

import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

import org.apache.dubbo.config.spring.context.annotation.EnableDubbo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;

@Configuration
@EnableDubbo(scanBasePackages = "com.cheeseocean.cheeseim.push.service")
@PropertySource("classpath:dubbo.properties")
public class PushApplication {
    private static final Logger logger = LoggerFactory.getLogger(PushApplication.class);

    private static ScheduledExecutorService scheduledExecutorService;
    private static final ReentrantLock LOCK = new ReentrantLock();

    private static final Condition STOP = LOCK.newCondition();

    public static void main(String[] args) throws InterruptedException {
        try {
            AnnotationConfigApplicationContext ctx = new AnnotationConfigApplicationContext(PushApplication.class);
            Runtime.getRuntime().addShutdownHook(new Thread() {
                @Override
                public void run() {
                    ctx.stop();
                    try {
                        LOCK.lock();
                        STOP.signal();
                    } finally {
                        LOCK.unlock();
                    }
                    logger.info("stop push application");
                }
            });
            ctx.start();
            logger.info("start push application successfull");
        }catch (Exception e){
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
