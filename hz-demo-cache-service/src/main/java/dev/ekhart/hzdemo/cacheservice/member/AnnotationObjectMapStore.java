package dev.ekhart.hzdemo.cacheservice.member;

import static com.mongodb.client.model.Filters.eq;
import static com.mongodb.client.model.Filters.in;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.map.MapLoader;
import com.hazelcast.map.MapLoaderLifecycleSupport;
import com.hazelcast.map.MapStore;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.BuiltinExchangeType;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import dev.ekhart.hzdemo.cacheservice.messaging.AnnotationObjectStreamHeaders;
import dev.ekhart.hzdemo.cacheservice.persistence.AnnotationObjectDocuments;
import dev.ekhart.hzdemo.models.annotations.Annotation;
import java.io.IOException;
import java.net.URISyntaxException;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.TimeoutException;
import org.bson.Document;

public class AnnotationObjectMapStore implements MapLoader<String, Annotation>, MapStore<String, Annotation>,
        MapLoaderLifecycleSupport,
        MemberSideHazelcastComponent {

    private static final String CONNECTION_STRING_PROPERTY = "connection-string";
    private static final String DATABASE_PROPERTY = "database";
    private static final String COLLECTION_PROPERTY = "collection";
    private static final String RABBITMQ_URI_PROPERTY = "rabbitmq-uri";
    private static final String RABBITMQ_EXCHANGE_PROPERTY = "rabbitmq-exchange";
    private static final String RABBITMQ_ROUTING_KEY_PROPERTY = "rabbitmq-routing-key";

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Object publishMonitor = new Object();
    private MongoClient mongoClient;
    private MongoCollection<Document> collection;
    private Connection rabbitConnection;
    private Channel rabbitChannel;
    private String rabbitExchange;
    private String rabbitRoutingKey;

    @Override
    public void init(HazelcastInstance hazelcastInstance, Properties properties, String mapName) {
        String connectionString = requiredProperty(properties, CONNECTION_STRING_PROPERTY);
        String databaseName = requiredProperty(properties, DATABASE_PROPERTY);
        String collectionName = requiredProperty(properties, COLLECTION_PROPERTY);
        String rabbitUri = requiredProperty(properties, RABBITMQ_URI_PROPERTY);
        this.rabbitExchange = requiredProperty(properties, RABBITMQ_EXCHANGE_PROPERTY);
        this.rabbitRoutingKey = requiredProperty(properties, RABBITMQ_ROUTING_KEY_PROPERTY);

        this.mongoClient = MongoClients.create(connectionString);
        MongoDatabase database = mongoClient.getDatabase(databaseName);
        this.collection = database.getCollection(collectionName);
        this.collection.createIndex(new Document("docId", 1));
        configureRabbitMq(rabbitUri);
    }

    @Override
    public void destroy() {
        closeRabbitMq();
        if (mongoClient != null) {
            mongoClient.close();
        }
    }

    @Override
    public Annotation load(String key) {
        Document document = collection.find(eq("_id", key)).first();
        return document == null ? null : AnnotationObjectDocuments.toAnnotation(document);
    }

    @Override
    public Map<String, Annotation> loadAll(Collection<String> keys) {
        Map<String, Annotation> annotationsById = new HashMap<>();
        for (Document document : collection.find(in("_id", keys))) {
            Annotation annotation = AnnotationObjectDocuments.toAnnotation(document);
            annotationsById.put(annotation.getId(), annotation);
        }
        return annotationsById;
    }

    @Override
    public Iterable<String> loadAllKeys() {
        return List.of();
    }

    @Override
    public void store(String key, Annotation value) {
        if (value == null) {
            delete(key);
            return;
        }
        publish(value);
    }

    @Override
    public void storeAll(Map<String, Annotation> map) {
        map.forEach(this::store);
    }

    @Override
    public void delete(String key) {
        collection.deleteOne(eq("_id", key));
    }

    @Override
    public void deleteAll(Collection<String> keys) {
        if (keys.isEmpty()) {
            return;
        }
        collection.deleteMany(in("_id", keys));
    }

    private String requiredProperty(Properties properties, String key) {
        String value = properties.getProperty(key);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Missing Hazelcast MapStore property: " + key);
        }
        return value;
    }

    private void configureRabbitMq(String rabbitUri) {
        try {
            ConnectionFactory connectionFactory = new ConnectionFactory();
            connectionFactory.setUri(rabbitUri);
            connectionFactory.setAutomaticRecoveryEnabled(true);
            this.rabbitConnection = connectionFactory.newConnection("hz-demo-annotation-object-map-store");
            this.rabbitChannel = rabbitConnection.createChannel();
            this.rabbitChannel.exchangeDeclare(rabbitExchange, BuiltinExchangeType.TOPIC, true);
        } catch (IOException | TimeoutException | URISyntaxException | NoSuchAlgorithmException
                | KeyManagementException e) {
            throw new IllegalStateException("Failed to configure RabbitMQ annotation object publisher: "
                    + failureMessage(e));
        }
    }

    private void publish(Annotation annotation) {
        byte[] body = serialize(annotation);

        synchronized (publishMonitor) {
            if (rabbitChannel == null) {
                throw new IllegalStateException("RabbitMQ publisher is not initialized");
            }
            try {
                String publishedAtEpochMillis = Long.toString(System.currentTimeMillis());
                AMQP.BasicProperties properties = new AMQP.BasicProperties.Builder()
                        .contentType("application/json")
                        .deliveryMode(2)
                        .messageId(annotation.getId())
                        .headers(Map.of(AnnotationObjectStreamHeaders.PUBLISHED_AT_EPOCH_MILLIS,
                                publishedAtEpochMillis))
                        .build();
                rabbitChannel.basicPublish(rabbitExchange, rabbitRoutingKey, properties, body);
            } catch (IOException e) {
                throw new IllegalStateException("Failed to publish annotation object " + annotation.getId() + ": "
                        + failureMessage(e));
            }
        }
    }

    private byte[] serialize(Annotation annotation) {
        try {
            return objectMapper.writeValueAsBytes(annotation);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize annotation object " + annotation.getId() + ": "
                    + failureMessage(e));
        }
    }

    private String failureMessage(Exception e) {
        String message = e.getMessage();
        String description = e.getClass().getSimpleName() + (message == null ? "" : " - " + message);
        Throwable cause = e.getCause();
        if (cause == null) {
            return description;
        }
        String causeMessage = cause.getMessage();
        return description + "; cause=" + cause.getClass().getSimpleName()
                + (causeMessage == null ? "" : " - " + causeMessage);
    }

    private void closeRabbitMq() {
        closeChannel();
        closeConnection();
    }

    private void closeChannel() {
        if (rabbitChannel == null) {
            return;
        }
        try {
            rabbitChannel.close();
        } catch (IOException | TimeoutException ignored) {
        }
    }

    private void closeConnection() {
        if (rabbitConnection == null) {
            return;
        }
        try {
            rabbitConnection.close();
        } catch (IOException ignored) {
        }
    }
}
