package dev.ekhart.hzdemo.client;

import static org.assertj.core.api.Assertions.assertThat;

import com.mongodb.client.MongoClients;
import com.mongodb.client.model.ReplaceOptions;
import dev.ekhart.hzdemo.client.service.ClientAnnotationService;
import dev.ekhart.hzdemo.models.annotations.Annotation;
import dev.ekhart.hzdemo.models.cluster.CacheMapStatsResponse;
import dev.ekhart.hzdemo.models.cluster.CacheStatisticsResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.bson.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class AnnotationIntegrationTests extends AbstractHazelcastClusterIntegrationTest {

    @Autowired
    private ClientAnnotationService annotationService;

    @BeforeEach
    void setUp() throws InterruptedException {
        awaitCluster(annotationService, 3);
        clearCollections();
        annotationService.clearAll();
    }

    @Test
    void appendsAnnotationsThroughHazelcastAndPersistsThemToMongo() throws InterruptedException {
        Annotation first = Annotation.builder()
                .docId("document-1")
                .start(0)
                .end(4)
                .value("PERSON")
                .build();
        Annotation second = Annotation.builder()
                .docId("document-1")
                .start(10)
                .end(16)
                .value("LOCATION")
                .build();

        List<Annotation> afterFirstAppend = annotationService.append("document-1", first);
        List<Annotation> afterSecondAppend = annotationService.append("document-1", second);

        assertThat(afterFirstAppend).containsExactly(first);
        assertThat(afterSecondAppend).containsExactly(first, second);
        assertThat(annotationService.get("document-1")).contains(List.of(first, second));
        assertMongoValues("document-1", "PERSON", "LOCATION");
    }

    @Test
    void reportsCacheMapStatisticsForBothMaps() throws InterruptedException {
        CacheStatisticsResponse before = annotationService.cacheStatistics();

        Annotation first = Annotation.builder()
                .docId("document-stats")
                .start(0)
                .end(4)
                .value("PERSON")
                .build();
        Annotation second = Annotation.builder()
                .docId("document-stats")
                .start(10)
                .end(16)
                .value("LOCATION")
                .build();

        annotationService.appendAll("document-stats", List.of(first, second));

        CacheStatisticsResponse cacheStatistics = annotationService.cacheStatistics();
        int documentEntriesBefore = totalEntryCount(before, "document-annotation-ids");
        int annotationEntriesBefore = totalEntryCount(before, "annotation-objects");
        assertThat(cacheStatistics.getClusterName()).isEqualTo("hz-demo-cluster");
        assertThat(cacheStatistics.getMaps()).extracting(CacheMapStatsResponse::getMapName)
                .containsExactly("document-annotation-ids", "annotation-objects");
        assertThat(cacheStatistics.getMaps()).filteredOn(stats -> stats.getMapName().equals("document-annotation-ids"))
                .singleElement()
                .extracting(CacheMapStatsResponse::getTotalEntryCount)
                .isEqualTo(documentEntriesBefore + 1);
        assertThat(cacheStatistics.getMaps()).filteredOn(stats -> stats.getMapName().equals("annotation-objects"))
                .singleElement()
                .extracting(CacheMapStatsResponse::getTotalEntryCount)
                .isEqualTo(annotationEntriesBefore + 2);
        assertThat(cacheStatistics.getMaps()).allSatisfy(stats -> assertThat(stats.getMemberStats()).hasSize(3));
    }

    @Test
    void appendsMultipleAnnotationsAtOnceAndReturnsTheLatestList() throws InterruptedException {
        Annotation existing = Annotation.builder()
                .docId("document-batch")
                .start(1)
                .end(5)
                .value("TAG-A")
                .build();
        Annotation second = Annotation.builder()
                .docId("document-batch")
                .start(8)
                .end(12)
                .value("TAG-B")
                .build();
        Annotation third = Annotation.builder()
                .docId("document-batch")
                .start(15)
                .end(19)
                .value("TAG-C")
                .build();

        try (var mongoClient = MongoClients.create(MONGO.getConnectionString())) {
            mongoClient.getDatabase("hz-demo")
                    .getCollection("annotation-objects")
                    .replaceOne(new Document("_id", existing.getId()),
                            new Document("_id", existing.getId())
                                    .append("id", existing.getId())
                                    .append("docId", "document-batch")
                                    .append("start", existing.getStart())
                                    .append("end", existing.getEnd())
                                    .append("value", existing.getValue()),
                            new ReplaceOptions().upsert(true));
        }

        assertThat(annotationService.appendAll("document-batch", List.of(second, third)))
                .containsExactly(existing, second, third);
        assertThat(annotationService.get("document-batch")).contains(List.of(existing, second, third));
        assertMongoValues("document-batch", "TAG-A", "TAG-B", "TAG-C");
    }

    @Test
    void appendsToMongoBackedDocumentWithoutPreloadingTheListIntoHazelcast() throws InterruptedException {
        Annotation existing = Annotation.builder()
                .docId("document-2")
                .start(1)
                .end(5)
                .value("TAG-A")
                .build();
        Annotation appended = Annotation.builder()
                .docId("document-2")
                .start(8)
                .end(12)
                .value("TAG-B")
                .build();

        try (var mongoClient = MongoClients.create(MONGO.getConnectionString())) {
            mongoClient.getDatabase("hz-demo")
                    .getCollection("annotation-objects")
                    .replaceOne(new Document("_id", existing.getId()),
                            new Document("_id", existing.getId())
                                    .append("id", existing.getId())
                                    .append("docId", "document-2")
                                    .append("start", existing.getStart())
                                    .append("end", existing.getEnd())
                                    .append("value", existing.getValue()),
                            new ReplaceOptions().upsert(true));
        }

        assertThat(annotationService.append("document-2", appended)).containsExactly(existing, appended);
        assertThat(annotationService.get("document-2")).contains(List.of(existing, appended));
        assertMongoValues("document-2", "TAG-A", "TAG-B");
    }

    @Test
    void appendsMultipleAnnotationsAndKeepsTheFullListCached() throws InterruptedException {
        Annotation existing = Annotation.builder()
                .docId("document-3")
                .start(1)
                .end(5)
                .value("TAG-A")
                .build();
        Annotation second = Annotation.builder()
                .docId("document-3")
                .start(8)
                .end(12)
                .value("TAG-B")
                .build();
        Annotation third = Annotation.builder()
                .docId("document-3")
                .start(15)
                .end(19)
                .value("TAG-C")
                .build();
        Annotation fourth = Annotation.builder()
                .docId("document-3")
                .start(22)
                .end(26)
                .value("TAG-D")
                .build();

        try (var mongoClient = MongoClients.create(MONGO.getConnectionString())) {
            mongoClient.getDatabase("hz-demo")
                    .getCollection("annotation-objects")
                    .replaceOne(new Document("_id", existing.getId()),
                            new Document("_id", existing.getId())
                                    .append("id", existing.getId())
                                    .append("docId", "document-3")
                                    .append("start", existing.getStart())
                                    .append("end", existing.getEnd())
                                    .append("value", existing.getValue()),
                            new ReplaceOptions().upsert(true));
        }

        assertThat(annotationService.append("document-3", second)).containsExactly(existing, second);
        assertThat(annotationService.append("document-3", third)).containsExactly(existing, second, third);
        assertThat(annotationService.append("document-3", fourth)).containsExactly(existing, second, third, fourth);
        assertThat(annotationService.get("document-3")).contains(List.of(existing, second, third, fourth));
        assertMongoValues("document-3", "TAG-A", "TAG-B", "TAG-C", "TAG-D");
    }

    private void assertMongoValues(String documentId, String... expectedValues) throws InterruptedException {
        long deadline = System.nanoTime() + Duration.ofSeconds(15).toNanos();
        AssertionError lastFailure = null;
        while (System.nanoTime() < deadline) {
            try {
                assertMongoValuesOnce(documentId, expectedValues);
                return;
            } catch (AssertionError e) {
                lastFailure = e;
            }
            Thread.sleep(250);
        }
        if (lastFailure != null) {
            throw lastFailure;
        }
        throw new AssertionError("Timed out waiting for annotation objects to persist to Mongo");
    }

    private void assertMongoValuesOnce(String documentId, String... expectedValues) {
        try (var mongoClient = MongoClients.create(MONGO.getConnectionString())) {
            List<String> values = mongoClient.getDatabase("hz-demo")
                    .getCollection("annotation-objects")
                    .find(new Document("docId", documentId))
                    .sort(new Document("start", 1).append("end", 1))
                    .into(new java.util.ArrayList<>())
                    .stream()
                    .map(doc -> doc.getString("value"))
                    .collect(Collectors.toList());

            assertThat(values).containsExactlyInAnyOrder(expectedValues);
        }
    }

    private int totalEntryCount(CacheStatisticsResponse cacheStatistics, String mapName) {
        return cacheStatistics.getMaps().stream()
                .filter(stats -> stats.getMapName().equals(mapName))
                .findFirst()
                .map(CacheMapStatsResponse::getTotalEntryCount)
                .orElseThrow();
    }
}
