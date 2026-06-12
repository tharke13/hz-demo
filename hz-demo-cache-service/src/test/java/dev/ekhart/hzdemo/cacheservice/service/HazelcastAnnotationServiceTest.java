package dev.ekhart.hzdemo.cacheservice.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.core.IExecutorService;
import com.hazelcast.map.IMap;
import dev.ekhart.hzdemo.cacheservice.config.HazelcastClientProperties;
import dev.ekhart.hzdemo.models.annotations.Annotation;
import dev.ekhart.hzdemo.models.cluster.CacheMapMemberStatsResponse;
import dev.ekhart.hzdemo.models.cluster.CacheMapStatsResponse;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

class HazelcastAnnotationServiceTest {

    @Test
    void appendWritesAnnotationThenDocumentIndexAndReturnsResolvedList() {
        HazelcastClientProperties properties = new HazelcastClientProperties();
        HazelcastAnnotationService service = service(properties);
        @SuppressWarnings("unchecked")
        IMap<String, Set<String>> documentMap = mock(IMap.class);
        @SuppressWarnings("unchecked")
        IMap<String, Annotation> annotationMap = mock(IMap.class);
        Annotation first = annotation(0, 4, "A");
        Annotation second = annotation(5, 8, "B");

        when(documentMap.get("document-1")).thenReturn(linkedSet(first.getId()));
        when(annotationMap.putIfAbsent(second.getId(), second)).thenReturn(null);
        when(annotationMap.getAll(java.util.Set.of(first.getId(), second.getId()))).thenReturn(new LinkedHashMap<>(Map.of(
                first.getId(), first,
                second.getId(), second
        )));

        assertThat(service.append("document-1", second, documentMap, annotationMap)).containsExactly(first, second);

        InOrder inOrder = inOrder(documentMap, annotationMap);
        inOrder.verify(documentMap).lock("document-1");
        inOrder.verify(documentMap).get("document-1");
        inOrder.verify(annotationMap).putIfAbsent(second.getId(), second);
        inOrder.verify(documentMap).put("document-1", linkedSet(first.getId(), second.getId()));
        inOrder.verify(annotationMap).getAll(java.util.Set.of(first.getId(), second.getId()));
        inOrder.verify(documentMap).unlock("document-1");
    }

    @Test
    void appendAllWritesNewAnnotationObjectsOnceAndReturnsResolvedList() {
        HazelcastClientProperties properties = new HazelcastClientProperties();
        HazelcastAnnotationService service = service(properties);
        @SuppressWarnings("unchecked")
        IMap<String, Set<String>> documentMap = mock(IMap.class);
        @SuppressWarnings("unchecked")
        IMap<String, Annotation> annotationMap = mock(IMap.class);
        Annotation existing = annotation(0, 4, "A");
        Annotation second = annotation(5, 8, "B");
        Annotation third = annotation(9, 12, "C");

        when(documentMap.get("document-1")).thenReturn(linkedSet(existing.getId()));
        when(annotationMap.putIfAbsent(second.getId(), second)).thenReturn(null);
        when(annotationMap.putIfAbsent(third.getId(), third)).thenReturn(null);
        when(annotationMap.getAll(java.util.Set.of(existing.getId(), second.getId(), third.getId())))
                .thenReturn(new LinkedHashMap<>(Map.of(
                        existing.getId(), existing,
                        second.getId(), second,
                        third.getId(), third
                )));

        assertThat(service.appendAll("document-1", List.of(second, third), documentMap, annotationMap))
                .containsExactly(existing, second, third);

        InOrder inOrder = inOrder(documentMap, annotationMap);
        inOrder.verify(documentMap).lock("document-1");
        inOrder.verify(documentMap).get("document-1");
        inOrder.verify(annotationMap).putIfAbsent(second.getId(), second);
        inOrder.verify(annotationMap).putIfAbsent(third.getId(), third);
        inOrder.verify(documentMap).put("document-1", linkedSet(existing.getId(), second.getId(), third.getId()));
        inOrder.verify(annotationMap).getAll(java.util.Set.of(existing.getId(), second.getId(), third.getId()));
        inOrder.verify(documentMap).unlock("document-1");
    }

    @Test
    void appendRollsBackNewAnnotationObjectWhenDocumentIndexWriteFails() {
        HazelcastClientProperties properties = new HazelcastClientProperties();
        HazelcastAnnotationService service = service(properties);
        @SuppressWarnings("unchecked")
        IMap<String, Set<String>> documentMap = mock(IMap.class);
        @SuppressWarnings("unchecked")
        IMap<String, Annotation> annotationMap = mock(IMap.class);
        Annotation annotation = annotation(0, 4, "A");

        when(documentMap.get("document-1")).thenReturn(linkedSet());
        when(annotationMap.putIfAbsent(annotation.getId(), annotation)).thenReturn(null);
        doThrow(new IllegalStateException("boom")).when(documentMap).put("document-1", linkedSet(annotation.getId()));

        assertThatThrownBy(() -> service.append("document-1", annotation, documentMap, annotationMap))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("boom");

        verify(annotationMap).remove(annotation.getId(), annotation);
        verify(documentMap).unlock("document-1");
    }

    @Test
    void appendAllRollsBackOnlyNewAnnotationObjectsWhenDocumentIndexWriteFails() {
        HazelcastClientProperties properties = new HazelcastClientProperties();
        HazelcastAnnotationService service = service(properties);
        @SuppressWarnings("unchecked")
        IMap<String, Set<String>> documentMap = mock(IMap.class);
        @SuppressWarnings("unchecked")
        IMap<String, Annotation> annotationMap = mock(IMap.class);
        Annotation existing = annotation(0, 4, "A");
        Annotation reused = annotation(5, 8, "B");
        Annotation created = annotation(9, 12, "C");

        when(documentMap.get("document-1")).thenReturn(linkedSet(existing.getId()));
        when(annotationMap.putIfAbsent(reused.getId(), reused)).thenReturn(reused);
        when(annotationMap.putIfAbsent(created.getId(), created)).thenReturn(null);
        doThrow(new IllegalStateException("boom")).when(documentMap).put(
                "document-1",
                linkedSet(existing.getId(), reused.getId(), created.getId())
        );

        assertThatThrownBy(() -> service.appendAll("document-1", List.of(reused, created), documentMap, annotationMap))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("boom");

        verify(annotationMap).remove(created.getId(), created);
        verify(documentMap).unlock("document-1");
    }

    @Test
    void getResolvesOrderedAnnotationsFromIdList() {
        HazelcastClientProperties properties = new HazelcastClientProperties();
        HazelcastAnnotationService service = service(properties);
        @SuppressWarnings("unchecked")
        IMap<String, Set<String>> documentMap = mock(IMap.class);
        @SuppressWarnings("unchecked")
        IMap<String, Annotation> annotationMap = mock(IMap.class);
        Annotation shared = annotation(0, 4, "A");
        Annotation second = annotation(5, 8, "B");

        when(documentMap.get("document-1")).thenReturn(linkedSet(shared.getId(), second.getId()));
        when(annotationMap.getAll(java.util.Set.of(shared.getId(), second.getId()))).thenReturn(new LinkedHashMap<>(Map.of(
                shared.getId(), shared,
                second.getId(), second
        )));

        Optional<List<Annotation>> result = service.get("document-1", documentMap, annotationMap);

        assertThat(result).contains(List.of(shared, second));
        verify(documentMap).lock("document-1");
        verify(documentMap).unlock("document-1");
    }

    @Test
    void cacheStatisticsReturnsClusterwideSizeAndSortedMemberStats() {
        HazelcastClientProperties properties = new HazelcastClientProperties();
        HazelcastAnnotationService service = service(properties);
        @SuppressWarnings("unchecked")
        IMap<String, Set<String>> documentMap = mock(IMap.class);
        IExecutorService executorService = mock(IExecutorService.class);
        CacheMapMemberStatsResponse hazelcast2 = CacheMapMemberStatsResponse.builder()
                .member("[localhost]:5702")
                .ownedEntryCount(1)
                .putOperationCount(3)
                .build();
        CacheMapMemberStatsResponse hazelcast1 = CacheMapMemberStatsResponse.builder()
                .member("[localhost]:5701")
                .ownedEntryCount(2)
                .putOperationCount(4)
                .build();
        @SuppressWarnings("unchecked")
        Map<com.hazelcast.cluster.Member, java.util.concurrent.Future<CacheMapMemberStatsResponse>> futures =
                new LinkedHashMap<>();
        futures.put(mock(com.hazelcast.cluster.Member.class), CompletableFuture.completedFuture(hazelcast2));
        futures.put(mock(com.hazelcast.cluster.Member.class), CompletableFuture.completedFuture(hazelcast1));

        when(documentMap.size()).thenReturn(3);
        when(executorService.submitToAllMembers(org.mockito.ArgumentMatchers.any(CacheMapStatsCallable.class)))
                .thenReturn(futures);

        CacheMapStatsResponse result = service.cacheStatistics("document-annotation-ids", documentMap, executorService);

        assertThat(result.getMapName()).isEqualTo("document-annotation-ids");
        assertThat(result.getTotalEntryCount()).isEqualTo(3);
        assertThat(result.getMemberStats()).containsExactly(hazelcast1, hazelcast2);
    }

    private HazelcastAnnotationService service(HazelcastClientProperties properties) {
        return new HazelcastAnnotationService(mock(HazelcastInstance.class), properties);
    }

    private Annotation annotation(int start, int end, String value) {
        return Annotation.builder()
                .docId("document-1")
                .start(start)
                .end(end)
                .value(value)
                .build();
    }

    private Set<String> linkedSet(String... values) {
        return new LinkedHashSet<>(List.of(values));
    }
}
