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
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import org.bson.Document;

public class DocumentAnnotationIdsMapStore implements MapLoader<String, List<String>>, MapStore<String, List<String>>,
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
    public List<String> load(String key) {
        Document document = collection.find(eq("_id", key)).first();
        if (document == null) {
            return null;
        }
        List<String> annotationIds = document.getList("annotationIds", String.class);
        return annotationIds == null ? List.of() : List.copyOf(annotationIds);
    }

    @Override
    public Map<String, List<String>> loadAll(Collection<String> keys) {
        Map<String, List<String>> idsByDocumentId = new HashMap<>();
        for (Document document : collection.find(in("_id", keys))) {
            List<String> annotationIds = document.getList("annotationIds", String.class);
            idsByDocumentId.put(document.getString("_id"), annotationIds == null ? List.of() : List.copyOf(annotationIds));
        }
        return idsByDocumentId;
    }

    @Override
    public Iterable<String> loadAllKeys() {
        return List.of();
    }

    @Override
    public void store(String key, List<String> value) {
        collection.replaceOne(
                eq("_id", key),
                new Document("_id", key)
                        .append("documentId", key)
                        .append("annotationIds", value == null ? List.of() : List.copyOf(value)),
                new ReplaceOptions().upsert(true)
        );
    }

    @Override
    public void storeAll(Map<String, List<String>> map) {
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
}
