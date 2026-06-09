package dev.ekhart.hzdemo.client.api;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.ekhart.hzdemo.client.service.ClientAnnotationService;
import dev.ekhart.hzdemo.models.annotations.Annotation;
import dev.ekhart.hzdemo.models.cluster.CacheMapMemberStatsResponse;
import dev.ekhart.hzdemo.models.cluster.CacheMapStatsResponse;
import dev.ekhart.hzdemo.models.cluster.CacheStatisticsResponse;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(AnnotationController.class)
class AnnotationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private ClientAnnotationService annotationService;

    @Test
    void getReturnsEmptyJsonArrayWhenServiceReturnsEmptyList() throws Exception {
        when(annotationService.get("document-empty")).thenReturn(Optional.of(List.of()));

        mockMvc.perform(get("/api/annotations/document-empty"))
                .andExpect(status().isOk())
                .andExpect(content().json("[]"));
    }

    @Test
    void getReturnsNotFoundWhenServiceReturnsEmptyOptional() throws Exception {
        when(annotationService.get("document-missing")).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/annotations/document-missing"))
                .andExpect(status().isNotFound())
                .andExpect(status().reason("No cached annotation list for documentId document-missing"))
                .andExpect(content().string(""));
    }

    @Test
    void appendAllReturnsLatestList() throws Exception {
        Annotation first = annotation(0, 4, "PERSON");
        Annotation second = annotation(5, 8, "LOCATION");
        when(annotationService.appendAll("document-1", List.of(first, second))).thenReturn(List.of(first, second));

        mockMvc.perform(post("/api/annotations/document-1/batch")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(List.of(first, second))))
                .andExpect(status().isOk())
                .andExpect(content().json(objectMapper.writeValueAsString(List.of(first, second))));
    }

    @Test
    void appendAllRejectsEmptyPayload() throws Exception {
        mockMvc.perform(post("/api/annotations/document-1/batch")
                        .contentType("application/json")
                        .content("[]"))
                .andExpect(status().isBadRequest())
                .andExpect(status().reason("Annotation list must not be empty"));
    }

    @Test
    void cacheStatisticsReturnsConfiguredMapStats() throws Exception {
        CacheMapMemberStatsResponse memberStats = CacheMapMemberStatsResponse.builder()
                .member("[localhost]:5701")
                .ownedEntryCount(2)
                .build();
        CacheStatisticsResponse response = CacheStatisticsResponse.builder()
                .clusterName("hz-demo-cluster")
                .maps(List.of(CacheMapStatsResponse.builder()
                        .mapName("document-annotation-ids")
                        .totalEntryCount(2)
                        .memberStats(List.of(memberStats))
                        .build()))
                .build();
        when(annotationService.cacheStatistics()).thenReturn(response);

        mockMvc.perform(get("/api/annotations/cache-stats"))
                .andExpect(status().isOk())
                .andExpect(content().json(objectMapper.writeValueAsString(response)));
    }

    @Test
    void cacheStatisticsForSingleMapReturnsMapStats() throws Exception {
        CacheMapStatsResponse response = CacheMapStatsResponse.builder()
                .mapName("annotation-objects")
                .totalEntryCount(3)
                .memberStats(List.of())
                .build();
        when(annotationService.cacheStatistics("annotation-objects")).thenReturn(response);

        mockMvc.perform(get("/api/annotations/cache-stats/annotation-objects"))
                .andExpect(status().isOk())
                .andExpect(content().json(objectMapper.writeValueAsString(response)));
    }

    private Annotation annotation(int start, int end, String value) {
        return Annotation.builder()
                .start(start)
                .end(end)
                .value(value)
                .build();
    }
}
