package com.cheeseocean.postman;

import ch.qos.logback.core.util.TimeUtil;

import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

/**
 * @author xxxcrel
 * @date 2023/5/18 12:56
 */
public class StreamTest {

    public static void main(String[] args) {
        long startTime = System.currentTimeMillis();
        IntStream.range(0, 15).parallel().forEach(i -> {
            try {
                TimeUnit.SECONDS.sleep(1);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        });
        long endTime = System.currentTimeMillis();
        System.out.println("cost " + (endTime - startTime) / 1000);

    }
}
