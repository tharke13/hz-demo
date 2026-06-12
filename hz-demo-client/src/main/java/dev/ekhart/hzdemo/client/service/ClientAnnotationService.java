package dev.ekhart.hzdemo.client.service;

import dev.ekhart.hzdemo.cacheservice.service.HazelcastAnnotationService;
import dev.ekhart.hzdemo.models.annotations.Annotation;
import dev.ekhart.hzdemo.models.cluster.CacheMapStatsResponse;
import dev.ekhart.hzdemo.models.cluster.CacheStatisticsResponse;
import dev.ekhart.hzdemo.models.cluster.ClusterDetailsResponse;
import java.util.List;
import java.util.Optional;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ClientAnnotationService {

    private final HazelcastAnnotationService hazelcastAnnotationService;

    public List<Annotation> append(String documentId, Annotation annotation) {
        return hazelcastAnnotationService.append(documentId, annotation);
    }

    public List<Annotation> appendAll(String documentId, List<Annotation> annotations) {
        return hazelcastAnnotationService.appendAll(documentId, annotations);
    }

    public Optional<List<Annotation>> get(String documentId) {
        return hazelcastAnnotationService.get(documentId);
    }

    public void clearAll() {
        hazelcastAnnotationService.clearAll();
    }

    public ClusterDetailsResponse clusterDetails() {
        return hazelcastAnnotationService.clusterDetails();
    }

    public CacheStatisticsResponse cacheStatistics() {
        return hazelcastAnnotationService.cacheStatistics();
    }

    public CacheMapStatsResponse cacheStatistics(String mapName) {
        return hazelcastAnnotationService.cacheStatistics(mapName);
    }
}
