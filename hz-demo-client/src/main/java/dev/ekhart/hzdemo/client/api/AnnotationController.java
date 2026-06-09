package dev.ekhart.hzdemo.client.api;

import dev.ekhart.hzdemo.client.service.ClientAnnotationService;
import dev.ekhart.hzdemo.models.annotations.Annotation;
import dev.ekhart.hzdemo.models.cluster.CacheMapStatsResponse;
import dev.ekhart.hzdemo.models.cluster.CacheStatisticsResponse;
import dev.ekhart.hzdemo.models.cluster.ClusterDetailsResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/annotations")
@Tag(name = "Annotations", description = "Annotation-list cache and persistence endpoints")
public class AnnotationController {

    private final ClientAnnotationService annotationService;

    public AnnotationController(ClientAnnotationService annotationService) {
        this.annotationService = annotationService;
    }

    @PostMapping("/{documentId}")
    @Operation(summary = "Append a single annotation without resubmitting the full list")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Annotation appended successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid request payload", content = @Content)
    })
    List<Annotation> append(@PathVariable String documentId, @RequestBody Annotation request) {
        validateDocumentId(documentId);
        validateAnnotation(request);
        return annotationService.append(documentId, request);
    }

    @PostMapping("/{documentId}/batch")
    @Operation(summary = "Append multiple annotations without resubmitting the full list")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Annotations appended successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid request payload", content = @Content)
    })
    List<Annotation> appendAll(@PathVariable String documentId, @RequestBody List<Annotation> request) {
        validateDocumentId(documentId);
        validateAnnotations(request);
        return annotationService.appendAll(documentId, request);
    }

    @GetMapping("/{documentId}")
    @Operation(summary = "Fetch the annotation list for a document")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Annotation list found"),
            @ApiResponse(responseCode = "404", description = "No cached annotation list for the supplied documentId",
                    content = @Content)
    })
    List<Annotation> get(@PathVariable String documentId) {
        return annotationService.get(documentId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "No cached annotation list for documentId " + documentId
                ));
    }

    @GetMapping("/cluster")
    @Operation(summary = "Inspect Hazelcast cluster connectivity details")
    @ApiResponse(responseCode = "200", description = "Cluster details returned")
    ClusterDetailsResponse cluster() {
        return annotationService.clusterDetails();
    }

    @GetMapping("/cache-stats")
    @Operation(summary = "Show cache statistics for the configured Hazelcast maps")
    @ApiResponse(responseCode = "200", description = "Cache statistics returned")
    CacheStatisticsResponse cacheStatistics() {
        return annotationService.cacheStatistics();
    }

    @GetMapping("/cache-stats/{mapName}")
    @Operation(summary = "Show cache statistics for a single Hazelcast map")
    @ApiResponse(responseCode = "200", description = "Cache map statistics returned")
    CacheMapStatsResponse cacheStatistics(@PathVariable String mapName) {
        validateMapName(mapName);
        return annotationService.cacheStatistics(mapName);
    }

    private void validateDocumentId(String documentId) {
        if (documentId == null || documentId.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "documentId must not be blank");
        }
    }

    private void validateMapName(String mapName) {
        if (mapName == null || mapName.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "mapName must not be blank");
        }
    }

    private void validateAnnotation(Annotation annotation) {
        if (annotation == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Annotation entry must not be null");
        }
        if (annotation.getStart() < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "start must be greater than or equal to 0");
        }
        if (annotation.getEnd() < annotation.getStart()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "end must be greater than or equal to start");
        }
        if (annotation.getValue() == null || annotation.getValue().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "value must not be blank");
        }
    }

    private void validateAnnotations(List<Annotation> annotations) {
        if (annotations == null || annotations.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Annotation list must not be empty");
        }
        annotations.forEach(this::validateAnnotation);
    }
}
