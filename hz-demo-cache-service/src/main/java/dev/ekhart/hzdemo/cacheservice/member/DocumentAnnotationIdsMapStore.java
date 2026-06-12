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
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.stream.Collectors;
import org.bson.Document;

public class DocumentAnnotationIdsMapStore implements MapLoader<String, Set<String>>, MapStore<String, Set<String>>,
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
    public Set<String> load(String key) {
        return collection.find(eq("docId", key))
                .sort(new Document("start", 1).append("end", 1))
                .map(doc -> doc.getString("id"))
                .into(new LinkedHashSet<>());
    }

    @Override
    public Map<String, Set<String>> loadAll(Collection<String> keys) {
        return keys.stream()
                .collect(Collectors.toMap(
                        key -> key,
                        this::load
                ));
    }

    @Override
    public Iterable<String> loadAllKeys() {
        return java.util.List.of();
    }

    @Override
    public void store(String key, Set<String> value) {
        // The annotation IDs are not stored directly; they are derived from the docId field in the annotation objects.
    }

    @Override
    public void storeAll(Map<String, Set<String>> map) {
        // No-op
    }

    @Override
    public void delete(String key) {
        // We do not delete annotations when the document ID list is removed from the cache.
        // If document-level deletion is required, it should be handled explicitly.
    }

    @Override
    public void deleteAll(Collection<String> keys) {
        // No-op
    }

    private String requiredProperty(Properties properties, String key) {
        String value = properties.getProperty(key);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Missing Hazelcast MapStore property: " + key);
        }
        return value;
    }
}
