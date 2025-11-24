package xyz.firestige.infrastructure.redis.ack.core;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * PubSubStageBuilder messageTemplate 功能测试
 */
class MessageTemplateTest {

    @Test
    void messageTemplate_withMapValue_replacesPlaceholders() {
        PubSubStageBuilderImpl pubSubStage = new PubSubStageBuilderImpl(null);

        pubSubStage.messageTemplate("CONFIG_UPDATED:{tenantId}:{version}");

        Map<String, Object> value = Map.of("tenantId", "tenant001", "version", "v2.0.0", "extra", "data");
        String message = pubSubStage.getMessageBuilder().apply(value);

        assertEquals("CONFIG_UPDATED:tenant001:v2.0.0", message);
    }

    @Test
    void messageTemplate_noPlaceholders_returnsTemplate() {
        PubSubStageBuilderImpl pubSubStage = new PubSubStageBuilderImpl(null);

        pubSubStage.messageTemplate("SIMPLE_MESSAGE");

        String message = pubSubStage.getMessageBuilder().apply(Map.of("any", "value"));

        assertEquals("SIMPLE_MESSAGE", message);
    }

    @Test
    void messageTemplate_partialMatch_replacesOnlyMatched() {
        PubSubStageBuilderImpl pubSubStage = new PubSubStageBuilderImpl(null);

        pubSubStage.messageTemplate("Update:{found}:{notFound}");

        Map<String, Object> value = Map.of("found", "yes");
        String message = pubSubStage.getMessageBuilder().apply(value);

        assertEquals("Update:yes:{notFound}", message);
    }
}

