package com.cheeseocean.im.storage.history.mongo;

import com.cheeseocean.im.common.core.history.model.MessageMutation;
import com.cheeseocean.im.storage.history.mongo.document.MessageMutationDoc;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MongoMessageHistoryRepositoryTest {

    @Test
    void concurrentMutationUpsertShouldConvergeToExistingDocument() {
        MongoTemplate mongoTemplate = mock(MongoTemplate.class);
        MongoMessageHistoryRepository repository =
                new MongoMessageHistoryRepository(mongoTemplate, new ObjectMapper());
        MessageMutation mutation = new MessageMutation();
        mutation.setId("server-1:REVOKED");

        MessageMutationDoc existing = new MessageMutationDoc();
        existing.setId(mutation.getId());
        existing.setServerMsgId("server-1");
        existing.setConversationId("s:u1:u2");
        when(mongoTemplate.findAndModify(
                any(Query.class),
                any(Update.class),
                any(FindAndModifyOptions.class),
                eq(MessageMutationDoc.class)))
                .thenThrow(new DuplicateKeyException("concurrent upsert"));
        when(mongoTemplate.findById(mutation.getId(), MessageMutationDoc.class))
                .thenReturn(existing);

        MessageMutation result = repository.upsertMutation(mutation);

        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo(mutation.getId());
        assertThat(result.getConversationId()).isEqualTo("s:u1:u2");
    }
}
