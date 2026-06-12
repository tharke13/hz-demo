package dev.ekhart.hzdemo.cacheservice.messaging;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.annotation-object-stream")
public class AnnotationObjectStreamProperties {

    private String exchangeName = "hz-demo.annotation-objects";
    private String mongoQueueName = "hz-demo.annotation-objects.mongo";
    private String routingKey = "annotation.objects";

    public String getExchangeName() {
        return exchangeName;
    }

    public void setExchangeName(String exchangeName) {
        this.exchangeName = exchangeName;
    }

    public String getMongoQueueName() {
        return mongoQueueName;
    }

    public void setMongoQueueName(String mongoQueueName) {
        this.mongoQueueName = mongoQueueName;
    }

    public String getRoutingKey() {
        return routingKey;
    }

    public void setRoutingKey(String routingKey) {
        this.routingKey = routingKey;
    }
}
