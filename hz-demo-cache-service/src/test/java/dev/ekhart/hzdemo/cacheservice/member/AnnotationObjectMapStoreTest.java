package dev.ekhart.hzdemo.cacheservice.member;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.mongodb.client.FindIterable;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.ReplaceOptions;
import dev.ekhart.hzdemo.models.annotations.Annotation;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

class AnnotationObjectMapStoreTest {

    @Test
    void storePersistsAnnotationWithoutReadingMongoFirst() {
        MongoCollection<Document> collection = mock(MongoCollection.class);
        AnnotationObjectMapStore mapStore = new AnnotationObjectMapStore();
        ReflectionTestUtils.setField(mapStore, "collection", collection);

        mapStore.store("a-1", annotation(0, 4, "PERSON"));

        verify(collection, never()).find(any(Bson.class));
        verify(collection).replaceOne(any(Bson.class), any(Document.class), any(ReplaceOptions.class));

        Document document = captureDocument(collection);
        ReplaceOptions options = captureOptions(collection);

        assertThat(document.toJson()).contains("\"id\":");
        assertThat(document.toJson()).contains("\"PERSON\"");
        assertThat(options.isUpsert()).isTrue();
    }

    @Test
    void loadMapsStoredAnnotationObject() {
        Annotation stored = annotation(1, 5, "TAG-A");
        MongoCollection<Document> collection = mock(MongoCollection.class);
        FindIterable<Document> findResult = mock(FindIterable.class);
        when(collection.find(any(Bson.class))).thenReturn(findResult);
        when(findResult.first()).thenReturn(new Document("_id", stored.getId())
                .append("id", stored.getId())
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

    private Annotation annotation(int start, int end, String value) {
        return Annotation.builder()
                .start(start)
                .end(end)
                .value(value)
                .build();
    }
}
