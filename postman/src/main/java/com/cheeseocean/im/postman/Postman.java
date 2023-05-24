package com.cheeseocean.im.postman;

import com.cheeseocean.im.common.ApplicationKeeper;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Configuration;

/**
 * 消息投递服务
 * 负责从消息代理(Kafka, File, DB)中通过推送服务发送至用户
 *
 * @author xxxcrel
 * @date 2023/05/24
 */
@Configuration
public class Postman {

    public static void main(String[] args) {
        new ApplicationKeeper(new AnnotationConfigApplicationContext(Postman.class)).keep();
    }
}