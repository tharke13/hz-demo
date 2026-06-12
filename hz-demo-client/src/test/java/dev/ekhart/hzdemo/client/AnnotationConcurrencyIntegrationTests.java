package dev.ekhart.hzdemo.client;

import static org.assertj.core.api.Assertions.assertThat;

import com.mongodb.client.MongoClients;
import dev.ekhart.hzdemo.client.service.ClientAnnotationService;
import dev.ekhart.hzdemo.models.annotations.Annotation;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import org.bson.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class AnnotationConcurrencyIntegrationTests extends AbstractHazelcastClusterIntegrationTest {

    @Autowired
    private ClientAnnotationService annotationService;

    @BeforeEach
    void setUp() throws InterruptedException {
        awaitCluster(annotationService, 3);
        clearCollections();
        awaitHazelcastReady(annotationService::clearAll);
    }

    @Test
    void concurrentAppendsFromMultipleThreads() throws InterruptedException {
        String documentId = "concurrent-doc";
        int numThreads = 5;
        int appendsPerThread = 10;
        int totalAppends = numThreads * appendsPerThread;

        ExecutorService executor = Executors.newFixedThreadPool(numThreads);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(numThreads);
        List<String> expectedValues = Collections.synchronizedList(new ArrayList<>());

        for (int i = 0; i < numThreads; i++) {
            final int threadIdx = i;
            executor.submit(() -> {
                try {
                    startLatch.await();
                    for (int j = 0; j < appendsPerThread; j++) {
                        String value = "VAL-" + threadIdx + "-" + j;
                        Annotation ann = Annotation.builder()
                                .docId(documentId)
                                .start(threadIdx * 100 + j)
                                .end(threadIdx * 100 + j + 5)
                                .value(value)
                                .build();
                        List<Annotation> afterAppend = annotationService.append(documentId, ann);
                        assertThat(afterAppend).contains(ann);
                        expectedValues.add(value);
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                } finally {
                    endLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        boolean finished = endLatch.await(30, TimeUnit.SECONDS);
        assertThat(finished).withFailMessage("Test timed out waiting for threads to finish").isTrue();
        executor.shutdown();

        Optional<List<Annotation>> result = annotationService.get(documentId);
        assertThat(result).isPresent();
        assertThat(result.get()).hasSize(totalAppends);

        List<String> actualValues = result.get().stream()
                .map(Annotation::getValue)
                .collect(Collectors.toList());

        assertThat(actualValues).containsExactlyInAnyOrderElementsOf(expectedValues);

        // Verify persistence to Mongo
        assertMongoValues(documentId, expectedValues.toArray(new String[0]));
    }

    @Test
    void concurrentAppendAllFromMultipleThreads() throws InterruptedException {
        String documentId = "concurrent-doc-all";
        int numThreads = 5;
        int batchesPerThread = 5;
        int annotationsPerBatch = 3;
        int totalAppends = numThreads * batchesPerThread * annotationsPerBatch;

        ExecutorService executor = Executors.newFixedThreadPool(numThreads);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(numThreads);
        List<String> expectedValues = Collections.synchronizedList(new ArrayList<>());

        for (int i = 0; i < numThreads; i++) {
            final int threadIdx = i;
            executor.submit(() -> {
                try {
                    startLatch.await();
                    for (int j = 0; j < batchesPerThread; j++) {
                        List<Annotation> batch = new ArrayList<>();
                        for (int k = 0; k < annotationsPerBatch; k++) {
                            String value = "VAL-" + threadIdx + "-" + j + "-" + k;
                            Annotation ann = Annotation.builder()
                                    .docId(documentId)
                                    .start(threadIdx * 1000 + j * 100 + k)
                                    .end(threadIdx * 1000 + j * 100 + k + 5)
                                    .value(value)
                                    .build();
                            batch.add(ann);
                            expectedValues.add(value);
                        }
                        List<Annotation> afterAppend = annotationService.appendAll(documentId, batch);
                        assertThat(afterAppend).containsAll(batch);
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                } finally {
                    endLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        boolean finished = endLatch.await(30, TimeUnit.SECONDS);
        assertThat(finished).isTrue();
        executor.shutdown();

        Optional<List<Annotation>> result = annotationService.get(documentId);
        assertThat(result).isPresent();
        assertThat(result.get()).hasSize(totalAppends);

        List<String> actualValues = result.get().stream()
                .map(Annotation::getValue)
                .collect(Collectors.toList());

        assertThat(actualValues).containsExactlyInAnyOrderElementsOf(expectedValues);

        // Verify persistence to Mongo
        assertMongoValues(documentId, expectedValues.toArray(new String[0]));
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
                    .into(new java.util.ArrayList<>())
                    .stream()
                    .map(doc -> doc.getString("value"))
                    .collect(Collectors.toList());

            assertThat(values).containsExactlyInAnyOrder(expectedValues);
        }
    }
}
