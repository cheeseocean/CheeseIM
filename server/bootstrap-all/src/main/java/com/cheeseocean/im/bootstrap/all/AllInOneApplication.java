package com.cheeseocean.im.bootstrap.all;

import com.cheeseocean.im.authcenter.AuthCenter;
import com.cheeseocean.im.apiserver.ApiServerApplication;
import com.cheeseocean.im.postbox.Postbox;
import com.cheeseocean.im.postman.Postman;
import com.cheeseocean.im.postmaster.PostMaster;
import com.cheeseocean.im.postoffice.PostOffice;
import com.cheeseocean.im.business.Business;
import org.apache.dubbo.config.spring.context.annotation.EnableDubbo;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.util.Map;

@SpringBootApplication
@ComponentScan(
        basePackages = {
                "com.cheeseocean.im.common",
                "com.cheeseocean.im.apiserver",
                "com.cheeseocean.im.authcenter",
                "com.cheeseocean.im.business",
                "com.cheeseocean.im.postoffice",
                "com.cheeseocean.im.postmaster",
                "com.cheeseocean.im.postbox",
                "com.cheeseocean.im.postman"
        },
        excludeFilters = {
                @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = PostOffice.class),
                @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = PostMaster.class),
                @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = Postbox.class),
                @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = Postman.class),
                @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = AuthCenter.class),
                @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = Business.class),
                @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = ApiServerApplication.class),
                // 物理拆分后 adapter 的 AutoConfiguration 与 port 共用 common 包前缀。
                // 若被普通 ComponentScan 提前注册，ConditionalOnBean 会在 Mongo/Redis 自动装配前误判为 false。
                @ComponentScan.Filter(type = FilterType.ANNOTATION, classes = AutoConfiguration.class),
        }
)
@EnableDubbo(scanBasePackages = {
        "com.cheeseocean.im.common",
        "com.cheeseocean.im.apiserver",
        "com.cheeseocean.im.authcenter",
        "com.cheeseocean.im.business",
        "com.cheeseocean.im.postoffice",
        "com.cheeseocean.im.postmaster",
        "com.cheeseocean.im.postman",
        "com.cheeseocean.im.postbox"
})
@EnableScheduling
public class AllInOneApplication {

    public static void main(String[] args) {
        SpringApplication application = new SpringApplication(AllInOneApplication.class);
        application.setDefaultProperties(Map.of(
                "spring.config.name", "application-all",
                "cheeseim.queue.type", "chronicle"
        ));
        application.run(args);
    }
}
