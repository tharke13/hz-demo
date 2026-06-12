package dev.ekhart.hzdemo.cacheservice.messaging;

import static com.mongodb.client.model.Filters.eq;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.ReplaceOptions;
import dev.ekhart.hzdemo.cacheservice.persistence.AnnotationObjectDocuments;
import dev.ekhart.hzdemo.models.annotations.Annotation;
import java.io.IOException;
import org.bson.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

@Component
public class AnnotationObjectMongoListener {

    private static final Logger LOGGER = LoggerFactory.getLogger(AnnotationObjectMongoListener.class);

    private final ObjectMapper objectMapper;
    private final MongoCollection<Document> collection;

    @Autowired
    public AnnotationObjectMongoListener(AnnotationObjectMongoSinkProperties properties, MongoClient mongoClient) {
        this(new ObjectMapper(), mongoClient.getDatabase(properties.getDatabase())
                .getCollection(properties.getCollection()));
    }

    AnnotationObjectMongoListener(ObjectMapper objectMapper, MongoCollection<Document> collection) {
        this.objectMapper = objectMapper;
        this.collection = collection;
    }

    @RabbitListener(queues = "${app.annotation-object-stream.mongo-queue-name:hz-demo.annotation-objects.mongo}")
    public void persist(byte[] payload,
            @Header(name = AnnotationObjectStreamHeaders.PUBLISHED_AT_EPOCH_MILLIS, required = false)
            String publishedAtEpochMillis) {
        Annotation annotation = deserialize(payload);
        collection.replaceOne(eq("_id", annotation.getId()), AnnotationObjectDocuments.toDocument(annotation),
                new ReplaceOptions().upsert(true));
        logPersistenceLatency(annotation, publishedAtEpochMillis);
    }

    private Annotation deserialize(byte[] payload) {
        try {
            JsonNode json = objectMapper.readTree(payload);
            JsonNode value = json.get("value");
            return Annotation.builder()
                    .docId(json.path("docId").textValue())
                    .start(json.path("start").asInt())
                    .end(json.path("end").asInt())
                    .value(value == null || value.isNull() ? null : value.asText())
                    .build();
        } catch (IOException e) {
            throw new IllegalStateException("Failed to deserialize annotation object message", e);
        }
    }

    private void logPersistenceLatency(Annotation annotation, String publishedAtEpochMillis) {
        if (publishedAtEpochMillis == null || publishedAtEpochMillis.isBlank()) {
            LOGGER.info("Persisted annotation object {} to Mongo; RabbitMQ publish timestamp header was missing",
                    annotation.getId());
            return;
        }

        try {
            long publishedAt = Long.parseLong(publishedAtEpochMillis);
            long elapsedMillis = Math.max(0L, System.currentTimeMillis() - publishedAt);
            LOGGER.info("Persisted annotation object {} to Mongo {} ms after RabbitMQ publish", annotation.getId(),
                    elapsedMillis);
        } catch (NumberFormatException e) {
            LOGGER.info("Persisted annotation object {} to Mongo; RabbitMQ publish timestamp header was invalid: {}",
                    annotation.getId(), publishedAtEpochMillis);
        }
    }
}
