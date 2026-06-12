package dev.ekhart.hzdemo.cacheservice.member;

import static com.mongodb.client.model.Filters.eq;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.mongodb.client.FindIterable;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoIterable;
import com.mongodb.client.model.ReplaceOptions;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

class DocumentAnnotationIdsMapStoreTest {

    @Test
    void storeIsNoOp() {
        MongoCollection<Document> collection = mock(MongoCollection.class);
        DocumentAnnotationIdsMapStore mapStore = new DocumentAnnotationIdsMapStore();
        ReflectionTestUtils.setField(mapStore, "collection", collection);

        mapStore.store("document-1", Set.of("a-1", "a-2"));

        verify(collection, never()).replaceOne(any(Bson.class), any(Document.class), any(ReplaceOptions.class));
    }

    @Test
    void loadQueriesAnnotationObjectsByDocId() {
        MongoCollection<Document> collection = mock(MongoCollection.class);
        FindIterable<Document> findResult = mock(FindIterable.class);
        MongoIterable<String> mongoIterable = mock(MongoIterable.class);
        
        when(collection.find(any(Bson.class))).thenReturn(findResult);
        when(findResult.sort(any(Bson.class))).thenReturn(findResult);
        org.mockito.Mockito.doReturn(mongoIterable).when(findResult).map(any());
        when(mongoIterable.into(any())).thenAnswer(invocation -> {
            Set<String> set = invocation.getArgument(0);
            set.add("a-1");
            set.add("a-2");
            return set;
        });

        DocumentAnnotationIdsMapStore mapStore = new DocumentAnnotationIdsMapStore();
        ReflectionTestUtils.setField(mapStore, "collection", collection);

        assertThat(mapStore.load("document-2"))
                .isInstanceOf(LinkedHashSet.class)
                .containsExactly("a-1", "a-2");
        verify(collection).find(eq("docId", "document-2"));
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
