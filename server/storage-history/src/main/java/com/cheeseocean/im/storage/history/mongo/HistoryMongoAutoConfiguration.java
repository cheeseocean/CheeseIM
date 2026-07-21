package com.cheeseocean.im.storage.history.mongo;

import com.cheeseocean.im.common.core.history.MessageHistoryRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.data.mongo.MongoDataAutoConfiguration;
import org.springframework.boot.autoconfigure.mongo.MongoAutoConfiguration;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.context.annotation.Bean;

/**
 * 历史 Mongo adapter 的唯一装配入口。
 *
 * <p>只有显式依赖 storage-history 且已有 MongoTemplate 的进程才会获得历史仓储实现。</p>
 */
@AutoConfiguration(after = {MongoAutoConfiguration.class, MongoDataAutoConfiguration.class})
@ConditionalOnBean(MongoTemplate.class)
public class HistoryMongoAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(MessageHistoryRepository.class)
    public MessageHistoryRepository messageHistoryRepository(
            MongoTemplate mongoTemplate,
            ObjectMapper objectMapper) {
        return new MongoMessageHistoryRepository(mongoTemplate, objectMapper);
    }
}
