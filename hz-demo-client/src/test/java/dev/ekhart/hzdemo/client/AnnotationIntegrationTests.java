package dev.ekhart.hzdemo.client;

import static org.assertj.core.api.Assertions.assertThat;

import com.mongodb.client.MongoClients;
import com.mongodb.client.model.ReplaceOptions;
import dev.ekhart.hzdemo.client.service.ClientAnnotationService;
import dev.ekhart.hzdemo.models.annotations.Annotation;
import dev.ekhart.hzdemo.models.cluster.CacheMapStatsResponse;
import dev.ekhart.hzdemo.models.cluster.CacheStatisticsResponse;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.bson.Document;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class AnnotationIntegrationTests extends AbstractHazelcastClusterIntegrationTest {

    @Autowired
    private ClientAnnotationService annotationService;

    @Test
    void appendsAnnotationsThroughHazelcastAndPersistsThemToMongo() throws InterruptedException {
        awaitCluster(annotationService, 3);

        Annotation first = Annotation.builder()
                .start(0)
                .end(4)
                .value("PERSON")
                .build();
        Annotation second = Annotation.builder()
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
        awaitCluster(annotationService, 3);

        CacheStatisticsResponse before = annotationService.cacheStatistics();

        Annotation first = Annotation.builder()
                .start(0)
                .end(4)
                .value("PERSON")
                .build();
        Annotation second = Annotation.builder()
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
        awaitCluster(annotationService, 3);

        Annotation existing = Annotation.builder()
                .start(1)
                .end(5)
                .value("TAG-A")
                .build();
        Annotation second = Annotation.builder()
                .start(8)
                .end(12)
                .value("TAG-B")
                .build();
        Annotation third = Annotation.builder()
                .start(15)
                .end(19)
                .value("TAG-C")
                .build();

        try (var mongoClient = MongoClients.create(MONGO.getConnectionString())) {
            mongoClient.getDatabase("hz-demo")
                    .getCollection("document-annotation-ids")
                    .replaceOne(new Document("_id", "document-batch"),
                            new Document("_id", "document-batch")
                                    .append("documentId", "document-batch")
                                    .append("annotationIds", List.of(existing.getId())),
                            new ReplaceOptions().upsert(true));
            mongoClient.getDatabase("hz-demo")
                    .getCollection("annotation-objects")
                    .replaceOne(new Document("_id", existing.getId()),
                            new Document("_id", existing.getId())
                                    .append("id", existing.getId())
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
        awaitCluster(annotationService, 3);

        Annotation existing = Annotation.builder()
                .start(1)
                .end(5)
                .value("TAG-A")
                .build();
        Annotation appended = Annotation.builder()
                .start(8)
                .end(12)
                .value("TAG-B")
                .build();

        try (var mongoClient = MongoClients.create(MONGO.getConnectionString())) {
            mongoClient.getDatabase("hz-demo")
                    .getCollection("document-annotation-ids")
                    .replaceOne(new Document("_id", "document-2"),
                            new Document("_id", "document-2")
                                    .append("documentId", "document-2")
                                    .append("annotationIds", List.of(existing.getId())),
                            new ReplaceOptions().upsert(true));
            mongoClient.getDatabase("hz-demo")
                    .getCollection("annotation-objects")
                    .replaceOne(new Document("_id", existing.getId()),
                            new Document("_id", existing.getId())
                                    .append("id", existing.getId())
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
        awaitCluster(annotationService, 3);

        Annotation existing = Annotation.builder()
                .start(1)
                .end(5)
                .value("TAG-A")
                .build();
        Annotation second = Annotation.builder()
                .start(8)
                .end(12)
                .value("TAG-B")
                .build();
        Annotation third = Annotation.builder()
                .start(15)
                .end(19)
                .value("TAG-C")
                .build();
        Annotation fourth = Annotation.builder()
                .start(22)
                .end(26)
                .value("TAG-D")
                .build();

        try (var mongoClient = MongoClients.create(MONGO.getConnectionString())) {
            mongoClient.getDatabase("hz-demo")
                    .getCollection("document-annotation-ids")
                    .replaceOne(new Document("_id", "document-3"),
                            new Document("_id", "document-3")
                                    .append("documentId", "document-3")
                                    .append("annotationIds", List.of(existing.getId())),
                            new ReplaceOptions().upsert(true));
            mongoClient.getDatabase("hz-demo")
                    .getCollection("annotation-objects")
                    .replaceOne(new Document("_id", existing.getId()),
                            new Document("_id", existing.getId())
                                    .append("id", existing.getId())
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

    private void assertMongoValues(String documentId, String... expectedValues) {
        try (var mongoClient = MongoClients.create(MONGO.getConnectionString())) {
            Document document = mongoClient.getDatabase("hz-demo")
                    .getCollection("document-annotation-ids")
                    .find(new Document("_id", documentId))
                    .first();
            assertThat(document).isNotNull();
            List<String> annotationIds = document.getList("annotationIds", String.class);
            assertThat(annotationIds).hasSize(expectedValues.length);

            Map<String, String> valuesById = new LinkedHashMap<>();
            mongoClient.getDatabase("hz-demo")
                    .getCollection("annotation-objects")
                    .find(new Document("_id", new Document("$in", annotationIds)))
                    .into(new java.util.ArrayList<>())
                    .forEach(stored -> valuesById.put(stored.getString("_id"), stored.getString("value")));

            List<String> values = annotationIds.stream()
                    .map(valuesById::get)
                    .collect(Collectors.toList());
            assertThat(values).containsExactly(expectedValues);
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
