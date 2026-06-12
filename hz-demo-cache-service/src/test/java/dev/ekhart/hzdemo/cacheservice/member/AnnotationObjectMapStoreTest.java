package dev.ekhart.hzdemo.cacheservice.member;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mongodb.client.FindIterable;
import com.mongodb.client.MongoCollection;
import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Channel;
import dev.ekhart.hzdemo.cacheservice.messaging.AnnotationObjectStreamHeaders;
import dev.ekhart.hzdemo.models.annotations.Annotation;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

class AnnotationObjectMapStoreTest {

    @Test
    void storePublishesPersistentAnnotationMessageToRabbitMq() throws Exception {
        Channel channel = mock(Channel.class);
        AnnotationObjectMapStore mapStore = new AnnotationObjectMapStore();
        ReflectionTestUtils.setField(mapStore, "rabbitChannel", channel);
        ReflectionTestUtils.setField(mapStore, "rabbitExchange", "hz-demo.annotation-objects");
        ReflectionTestUtils.setField(mapStore, "rabbitRoutingKey", "annotation.objects");

        Annotation annotation = annotation(0, 4, "PERSON");

        long beforePublish = System.currentTimeMillis();
        mapStore.store(annotation.getId(), annotation);
        long afterPublish = System.currentTimeMillis();

        ArgumentCaptor<AMQP.BasicProperties> propertiesCaptor = ArgumentCaptor.forClass(AMQP.BasicProperties.class);
        ArgumentCaptor<byte[]> bodyCaptor = ArgumentCaptor.forClass(byte[].class);
        verify(channel).basicPublish(eq("hz-demo.annotation-objects"), eq("annotation.objects"),
                propertiesCaptor.capture(), bodyCaptor.capture());

        AMQP.BasicProperties properties = propertiesCaptor.getValue();
        assertThat(properties.getContentType()).isEqualTo("application/json");
        assertThat(properties.getDeliveryMode()).isEqualTo(2);
        assertThat(properties.getMessageId()).isEqualTo(annotation.getId());
        assertThat(properties.getHeaders()).containsKey(AnnotationObjectStreamHeaders.PUBLISHED_AT_EPOCH_MILLIS);
        String publishedAtEpochMillis = (String) properties.getHeaders()
                .get(AnnotationObjectStreamHeaders.PUBLISHED_AT_EPOCH_MILLIS);
        assertThat(Long.parseLong(publishedAtEpochMillis)).isBetween(beforePublish, afterPublish);

        JsonNode published = new ObjectMapper().readTree(bodyCaptor.getValue());
        assertThat(published.path("id").asText()).isEqualTo(annotation.getId());
        assertThat(published.path("start").asInt()).isEqualTo(annotation.getStart());
        assertThat(published.path("end").asInt()).isEqualTo(annotation.getEnd());
        assertThat(published.path("value").asText()).isEqualTo(annotation.getValue());
    }

    @Test
    void loadMapsStoredAnnotationObject() {
        Annotation stored = annotation("doc-1", 1, 5, "TAG-A");
        MongoCollection<Document> collection = mock(MongoCollection.class);
        FindIterable<Document> findResult = mock(FindIterable.class);
        when(collection.find(any(Bson.class))).thenReturn(findResult);
        when(findResult.first()).thenReturn(new Document("_id", stored.getId())
                .append("id", stored.getId())
                .append("docId", stored.getDocId())
                .append("start", stored.getStart())
                .append("end", stored.getEnd())
                .append("value", stored.getValue()));

        AnnotationObjectMapStore mapStore = new AnnotationObjectMapStore();
        ReflectionTestUtils.setField(mapStore, "collection", collection);

        assertThat(mapStore.load(stored.getId())).isEqualTo(stored);
    }

    @Test
    void loadAllKeysReturnsEmptyIterableToPreventEnumeratingMongoDocuments() {
        MongoCollection<Document> collection = mock(MongoCollection.class);
        AnnotationObjectMapStore mapStore = new AnnotationObjectMapStore();
        ReflectionTestUtils.setField(mapStore, "collection", collection);

        assertThat(mapStore.loadAllKeys()).isEmpty();

        verify(collection, never()).find();
    }

    private Annotation annotation(String docId, int start, int end, String value) {
        return Annotation.builder()
                .docId(docId)
                .start(start)
                .end(end)
                .value(value)
                .build();
    }

    private Annotation annotation(int start, int end, String value) {
        return annotation(null, start, end, value);
    }
}
