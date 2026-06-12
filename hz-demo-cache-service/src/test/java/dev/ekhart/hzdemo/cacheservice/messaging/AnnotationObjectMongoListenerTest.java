package dev.ekhart.hzdemo.cacheservice.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.ReplaceOptions;
import dev.ekhart.hzdemo.models.annotations.Annotation;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

@ExtendWith(OutputCaptureExtension.class)
class AnnotationObjectMongoListenerTest {

    @Test
    void persistsAnnotationMessageToMongo(CapturedOutput output) throws Exception {
        MongoCollection<Document> collection = mock(MongoCollection.class);
        ObjectMapper objectMapper = new ObjectMapper();
        AnnotationObjectMongoListener listener = new AnnotationObjectMongoListener(objectMapper, collection);
        Annotation annotation = Annotation.builder()
                .start(0)
                .end(4)
                .value("PERSON")
                .build();

        listener.persist(objectMapper.writeValueAsBytes(annotation),
                Long.toString(System.currentTimeMillis() - 1000));

        Document document = captureDocument(collection);
        ReplaceOptions options = captureOptions(collection);

        assertThat(document.getString("_id")).isEqualTo(annotation.getId());
        assertThat(document.getString("id")).isEqualTo(annotation.getId());
        assertThat(document.getInteger("start")).isEqualTo(0);
        assertThat(document.getInteger("end")).isEqualTo(4);
        assertThat(document.getString("value")).isEqualTo("PERSON");
        assertThat(options.isUpsert()).isTrue();
        assertThat(output).contains("Persisted annotation object " + annotation.getId() + " to Mongo")
                .contains("ms after RabbitMQ publish");
    }

    private Document captureDocument(MongoCollection<Document> collection) {
        ArgumentCaptor<Document> documentCaptor = ArgumentCaptor.forClass(Document.class);
        verify(collection).replaceOne(any(Bson.class), documentCaptor.capture(), any(ReplaceOptions.class));
        return documentCaptor.getValue();
    }

    private ReplaceOptions captureOptions(MongoCollection<Document> collection) {
        ArgumentCaptor<ReplaceOptions> optionsCaptor = ArgumentCaptor.forClass(ReplaceOptions.class);
        verify(collection).replaceOne(any(Bson.class), any(Document.class), optionsCaptor.capture());
        return optionsCaptor.getValue();
    }
}
