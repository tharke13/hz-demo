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
import java.util.List;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

class DocumentAnnotationIdsMapStoreTest {

    @Test
    void storePersistsFullAnnotationIdListWithoutReadingMongoFirst() {
        MongoCollection<Document> collection = mock(MongoCollection.class);
        DocumentAnnotationIdsMapStore mapStore = new DocumentAnnotationIdsMapStore();
        ReflectionTestUtils.setField(mapStore, "collection", collection);

        mapStore.store("document-1", List.of("a-1", "a-2"));

        verify(collection, never()).find(any(Bson.class));
        verify(collection).replaceOne(any(Bson.class), any(Document.class), any(ReplaceOptions.class));

        Document document = captureDocument(collection);
        ReplaceOptions options = captureOptions(collection);

        assertThat(document.toJson()).contains("\"documentId\": \"document-1\"");
        assertThat(document.toJson()).contains("\"annotationIds\"");
        assertThat(document.toJson()).contains("\"a-1\"");
        assertThat(document.toJson()).contains("\"a-2\"");
        assertThat(options.isUpsert()).isTrue();
    }

    @Test
    void loadMapsStoredAnnotationIdList() {
        MongoCollection<Document> collection = mock(MongoCollection.class);
        FindIterable<Document> findResult = mock(FindIterable.class);
        when(collection.find(any(Bson.class))).thenReturn(findResult);
        when(findResult.first()).thenReturn(new Document("_id", "document-2")
                .append("documentId", "document-2")
                .append("annotationIds", List.of("a-1", "a-2")));

        DocumentAnnotationIdsMapStore mapStore = new DocumentAnnotationIdsMapStore();
        ReflectionTestUtils.setField(mapStore, "collection", collection);

        assertThat(mapStore.load("document-2")).containsExactly("a-1", "a-2");
    }

    @Test
    void loadAllKeysReturnsEmptyIterableToPreventEnumeratingMongoDocuments() {
        MongoCollection<Document> collection = mock(MongoCollection.class);
        DocumentAnnotationIdsMapStore mapStore = new DocumentAnnotationIdsMapStore();
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
}
