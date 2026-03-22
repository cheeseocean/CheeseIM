package com.cheeseocean.im.bootstrap.all;

import com.cheeseocean.im.authcenter.AuthCenter;
import com.cheeseocean.im.postbox.Postbox;
import com.cheeseocean.im.postman.Postman;
import com.cheeseocean.im.postoffice.PostOffice;
import com.cheeseocean.im.push.Push;
import org.apache.dubbo.config.spring.context.annotation.EnableDubbo;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.data.mongodb.repository.config.EnableMongoRepositories;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.util.Map;

@SpringBootApplication
@ComponentScan(
        basePackages = {
                "com.cheeseocean.im.common",
                "com.cheeseocean.im.authcenter",
                "com.cheeseocean.im.postoffice",
                "com.cheeseocean.im.postman",
                "com.cheeseocean.im.postbox",
                "com.cheeseocean.im.push"
        },
        excludeFilters = {
                @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = PostOffice.class),
                @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = Postman.class),
                @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = Postbox.class),
                @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = Push.class),
                @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = AuthCenter.class),
        }
)
@EnableDubbo(scanBasePackages = {
        "com.cheeseocean.im.common",
        "com.cheeseocean.im.authcenter",
        "com.cheeseocean.im.postoffice",
        "com.cheeseocean.im.postman",
        "com.cheeseocean.im.postbox",
        "com.cheeseocean.im.push"
})
@EnableKafka
@EnableScheduling
@EnableMongoRepositories(basePackages = "com.cheeseocean.im.postbox")
public class AllInOneApplication {

    public static void main(String[] args) {
        SpringApplication application = new SpringApplication(AllInOneApplication.class);
        application.setDefaultProperties(Map.of("spring.config.name", "application-all"));
        application.run(args);
    }
}
