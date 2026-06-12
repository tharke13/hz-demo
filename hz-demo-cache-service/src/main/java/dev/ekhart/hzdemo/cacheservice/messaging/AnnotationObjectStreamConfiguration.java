package dev.ekhart.hzdemo.cacheservice.messaging;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.ExchangeBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.annotation.EnableRabbit;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableRabbit
@EnableConfigurationProperties({
        AnnotationObjectStreamProperties.class,
        AnnotationObjectMongoSinkProperties.class
})
public class AnnotationObjectStreamConfiguration {

    @Bean
    public TopicExchange annotationObjectExchange(AnnotationObjectStreamProperties properties) {
        return ExchangeBuilder.topicExchange(properties.getExchangeName())
                .durable(true)
                .build();
    }

    @Bean
    public Queue annotationObjectMongoQueue(AnnotationObjectStreamProperties properties) {
        return QueueBuilder.durable(properties.getMongoQueueName())
                .build();
    }

    @Bean
    public Binding annotationObjectMongoBinding(AnnotationObjectStreamProperties properties,
            @Qualifier("annotationObjectMongoQueue") Queue queue,
            @Qualifier("annotationObjectExchange") TopicExchange exchange) {
        return BindingBuilder.bind(queue)
                .to(exchange)
                .with(properties.getRoutingKey());
    }

    @Bean(destroyMethod = "close")
    public MongoClient annotationObjectMongoClient(AnnotationObjectMongoSinkProperties properties) {
        return MongoClients.create(properties.getConnectionString());
    }
}
