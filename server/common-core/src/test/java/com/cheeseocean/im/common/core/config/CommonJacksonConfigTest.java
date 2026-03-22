package com.cheeseocean.im.common.core.config;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import java.time.LocalDateTime;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CommonJacksonConfigTest {

    @Test
    void commonJacksonConfigShouldProvideSharedObjectMapper() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(CommonJacksonConfig.class)) {
            ObjectMapper objectMapper = context.getBean(ObjectMapper.class);

            assertNotNull(objectMapper);
            assertFalse(objectMapper.getDeserializationConfig().isEnabled(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES));
            assertFalse(objectMapper.getSerializationConfig().isEnabled(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS));
            assertTrue(objectMapper.canSerialize(LocalDateTime.class));
        }
    }

    @Test
    void sharedObjectMapperShouldIgnoreUnknownProperties() throws Exception {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(CommonJacksonConfig.class)) {
            ObjectMapper objectMapper = context.getBean(ObjectMapper.class);

            SamplePayload payload = objectMapper.readValue("""
                    {"name":"cheese","extra":"ignored"}
                    """, SamplePayload.class);

            assertNotNull(payload);
            assertTrue("cheese".equals(payload.getName()));
        }
    }

    @Test
    void sharedObjectMapperShouldWriteIsoDateStrings() throws Exception {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(CommonJacksonConfig.class)) {
            ObjectMapper objectMapper = context.getBean(ObjectMapper.class);

            String json = objectMapper.writeValueAsString(Map.of("time", java.time.LocalDateTime.of(2026, 3, 22, 12, 34, 56)));

            assertTrue(json.contains("2026-03-22T12:34:56"));
        }
    }

    static class SamplePayload {
        private String name;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }
    }
}
