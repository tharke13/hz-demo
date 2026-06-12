package dev.ekhart.hzdemo.cacheservice.messaging;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.annotation-object-sink.mongo")
public class AnnotationObjectMongoSinkProperties {

    private String connectionString = "mongodb://localhost:27017/hz-demo";
    private String database = "hz-demo";
    private String collection = "annotation-objects";

    public String getConnectionString() {
        return connectionString;
    }

    public void setConnectionString(String connectionString) {
        this.connectionString = connectionString;
    }

    public String getDatabase() {
        return database;
    }

    public void setDatabase(String database) {
        this.database = database;
    }

    public String getCollection() {
        return collection;
    }

    public void setCollection(String collection) {
        this.collection = collection;
    }
}
