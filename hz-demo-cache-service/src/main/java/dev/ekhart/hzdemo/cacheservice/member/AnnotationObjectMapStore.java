package dev.ekhart.hzdemo.cacheservice.member;

import static com.mongodb.client.model.Filters.eq;
import static com.mongodb.client.model.Filters.in;

import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.map.MapLoader;
import com.hazelcast.map.MapLoaderLifecycleSupport;
import com.hazelcast.map.MapStore;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.ReplaceOptions;
import dev.ekhart.hzdemo.models.annotations.Annotation;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import org.bson.Document;

public class AnnotationObjectMapStore implements MapLoader<String, Annotation>, MapStore<String, Annotation>,
        MapLoaderLifecycleSupport,
        MemberSideHazelcastComponent {

    private static final String CONNECTION_STRING_PROPERTY = "connection-string";
    private static final String DATABASE_PROPERTY = "database";
    private static final String COLLECTION_PROPERTY = "collection";

    private MongoClient mongoClient;
    private MongoCollection<Document> collection;

    @Override
    public void init(HazelcastInstance hazelcastInstance, Properties properties, String mapName) {
        String connectionString = requiredProperty(properties, CONNECTION_STRING_PROPERTY);
        String databaseName = requiredProperty(properties, DATABASE_PROPERTY);
        String collectionName = requiredProperty(properties, COLLECTION_PROPERTY);

        this.mongoClient = MongoClients.create(connectionString);
        MongoDatabase database = mongoClient.getDatabase(databaseName);
        this.collection = database.getCollection(collectionName);
    }

    @Override
    public void destroy() {
        if (mongoClient != null) {
            mongoClient.close();
        }
    }

    @Override
    public Annotation load(String key) {
        Document document = collection.find(eq("_id", key)).first();
        return document == null ? null : toAnnotation(document);
    }

    @Override
    public Map<String, Annotation> loadAll(Collection<String> keys) {
        Map<String, Annotation> annotationsById = new HashMap<>();
        for (Document document : collection.find(in("_id", keys))) {
            Annotation annotation = toAnnotation(document);
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
        // TODO do we write to queue replica/remote here?

        // Write to backing store, would be accumulo normally
        collection.replaceOne(eq("_id", key), toDocument(value), new ReplaceOptions().upsert(true));
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

    private Document toDocument(Annotation annotation) {
        return new Document("_id", annotation.getId())
                .append("id", annotation.getId())
                .append("start", annotation.getStart())
                .append("end", annotation.getEnd())
                .append("value", annotation.getValue());
    }

    private Annotation toAnnotation(Document document) {
        return Annotation.builder()
                .start(document.getInteger("start", 0))
                .end(document.getInteger("end", 0))
                .value(document.getString("value"))
                .build();
    }
}
