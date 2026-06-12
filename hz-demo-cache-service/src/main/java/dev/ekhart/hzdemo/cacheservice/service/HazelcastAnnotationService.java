package dev.ekhart.hzdemo.cacheservice.service;

import com.hazelcast.cluster.Member;
import com.hazelcast.core.IExecutorService;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.map.IMap;
import dev.ekhart.hzdemo.cacheservice.config.HazelcastClientProperties;
import dev.ekhart.hzdemo.models.annotations.Annotation;
import dev.ekhart.hzdemo.models.cluster.CacheMapMemberStatsResponse;
import dev.ekhart.hzdemo.models.cluster.CacheMapStatsResponse;
import dev.ekhart.hzdemo.models.cluster.CacheStatisticsResponse;
import dev.ekhart.hzdemo.models.cluster.ClusterDetailsResponse;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

@Service
public class HazelcastAnnotationService {

    private static final String CACHE_MAP_STATS_EXECUTOR = "cache-map-stats";

    private final HazelcastInstance hazelcastInstance;
    private final HazelcastClientProperties properties;

    public HazelcastAnnotationService(HazelcastInstance hazelcastInstance, HazelcastClientProperties properties) {
        this.hazelcastInstance = hazelcastInstance;
        this.properties = properties;
    }

    public List<Annotation> append(String documentId, Annotation annotation) {
        return appendAll(documentId, List.of(annotation), documentAnnotations(), annotationObjects());
    }

    public List<Annotation> appendAll(String documentId, List<Annotation> annotations) {
        return appendAll(documentId, annotations, documentAnnotations(), annotationObjects());
    }

    public Optional<List<Annotation>> get(String documentId) {
        return get(documentId, documentAnnotations(), annotationObjects());
    }

    public CacheStatisticsResponse cacheStatistics() {
        return CacheStatisticsResponse.builder()
                .clusterName(properties.getClusterName())
                .maps(List.of(
                        cacheStatistics(properties.getDocumentMapName()),
                        cacheStatistics(properties.getAnnotationMapName())
                ))
                .build();
    }

    public CacheMapStatsResponse cacheStatistics(String mapName) {
        return cacheStatistics(mapName, hazelcastInstance.getMap(mapName), executorService());
    }

    List<Annotation> append(String documentId, Annotation annotation, IMap<String, Set<String>> documentAnnotations,
            IMap<String, Annotation> annotationObjects) {
        return appendAll(documentId, List.of(annotation), documentAnnotations, annotationObjects);
    }

    List<Annotation> appendAll(String documentId, List<Annotation> annotations,
            IMap<String, Set<String>> documentAnnotations, IMap<String, Annotation> annotationObjects) {
        documentAnnotations.lock(documentId);
        try {
            LinkedHashSet<String> currentIds = currentAnnotationIds(documentId, documentAnnotations);
            Map<String, Annotation> newlyCreated = new java.util.LinkedHashMap<>();
            for (Annotation annotation : annotations) {
                if (annotation.getDocId() == null || !annotation.getDocId().equals(documentId)) {
                    annotation = annotation.toBuilder().docId(documentId).build();
                }
                Annotation existing = annotationObjects.putIfAbsent(annotation.getId(), annotation);
                if (existing != null && !existing.equals(annotation)) {
                    throw new IllegalStateException("annotationId collision for id " + annotation.getId());
                }
                if (existing == null) {
                    newlyCreated.put(annotation.getId(), annotation);
                }
                currentIds.add(annotation.getId());
            }

            try {
                documentAnnotations.put(documentId, Collections.unmodifiableSet(new LinkedHashSet<>(currentIds)));
            } catch (RuntimeException e) {
                for (Annotation annotation : newlyCreated.values()) {
                    annotationObjects.remove(annotation.getId(), annotation);
                }
                throw e;
            }

            return resolveAnnotations(currentIds, annotationObjects);
        } finally {
            documentAnnotations.unlock(documentId);
        }
    }

    Optional<List<Annotation>> get(String documentId, IMap<String, Set<String>> documentAnnotations,
            IMap<String, Annotation> annotationObjects) {
        documentAnnotations.lock(documentId);
        try {
            Set<String> annotationIds = documentAnnotations.get(documentId);
            if (annotationIds == null) {
                return Optional.empty();
            }
            return Optional.of(resolveAnnotations(annotationIds, annotationObjects));
        } finally {
            documentAnnotations.unlock(documentId);
        }
    }

    public void clearAll() {
        annotationObjects().clear();
    }

    public ClusterDetailsResponse clusterDetails() {
        List<String> members = hazelcastInstance.getCluster().getMembers().stream()
                .map(Member::getAddress)
                .map(Object::toString)
                .sorted(Comparator.naturalOrder())
                .collect(Collectors.toList());
        return ClusterDetailsResponse.builder()
                .clusterName(properties.getClusterName())
                .mapName(properties.getDocumentMapName() + " + " + properties.getAnnotationMapName())
                .connectedMembers(members.size())
                .members(members)
                .build();
    }

    protected IMap<String, Set<String>> documentAnnotations() {
        return hazelcastInstance.getMap(properties.getDocumentMapName());
    }

    protected IMap<String, Annotation> annotationObjects() {
        return hazelcastInstance.getMap(properties.getAnnotationMapName());
    }

    protected IExecutorService executorService() {
        return hazelcastInstance.getExecutorService(CACHE_MAP_STATS_EXECUTOR);
    }

    private LinkedHashSet<String> currentAnnotationIds(String documentId, IMap<String, Set<String>> documentAnnotations) {
        Set<String> currentIds = documentAnnotations.get(documentId);
        return currentIds == null ? new LinkedHashSet<>() : new LinkedHashSet<>(currentIds);
    }

    private List<Annotation> resolveAnnotations(Set<String> annotationIds, IMap<String, Annotation> annotationObjects) {
        Map<String, Annotation> annotationsById = annotationObjects.getAll(annotationIds);
        List<Annotation> resolved = new ArrayList<>(annotationIds.size());
        for (String annotationId : annotationIds) {
            Annotation annotation = annotationsById.get(annotationId);
            if (annotation == null) {
                throw new IllegalStateException("Missing annotation object for id " + annotationId);
            }
            resolved.add(annotation);
        }
        return List.copyOf(resolved);
    }

    CacheMapStatsResponse cacheStatistics(String mapName, IMap<?, ?> map, IExecutorService executorService) {
        return CacheMapStatsResponse.builder()
                .mapName(mapName)
                .totalEntryCount(map.size())
                .memberStats(loadMemberStats(mapName, executorService))
                .build();
    }

    private List<CacheMapMemberStatsResponse> loadMemberStats(String mapName, IExecutorService executorService) {
        Map<Member, Future<CacheMapMemberStatsResponse>> futures =
                executorService.submitToAllMembers(new CacheMapStatsCallable(mapName));
        List<CacheMapMemberStatsResponse> memberStats = new ArrayList<>(futures.size());
        for (Future<CacheMapMemberStatsResponse> future : futures.values()) {
            try {
                memberStats.add(future.get());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Interrupted while loading cache map statistics for " + mapName, e);
            } catch (ExecutionException e) {
                throw new IllegalStateException("Failed to load cache map statistics for " + mapName, e);
            }
        }
        return memberStats.stream()
                .sorted(Comparator.comparing(CacheMapMemberStatsResponse::getMember))
                .collect(Collectors.toList());
    }
}
