package dev.ekhart.hzdemo.cacheservice.member;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.hazelcast.spi.merge.SplitBrainMergeTypes;
import java.util.List;
import org.junit.jupiter.api.Test;

class AnnotationIdListUnionMergePolicyTest {

    private final AnnotationIdListUnionMergePolicy mergePolicy = new AnnotationIdListUnionMergePolicy();

    @Test
    void mergesListsAndDedupesByAnnotationId() {
        List<String> merged = mergePolicy.merge(mapValue(List.of("a-1", "a-3")), mapValue(List.of("a-1", "a-2")));

        assertThat(merged).containsExactly("a-1", "a-2", "a-3");
    }

    @Test
    void returnsIncomingListWhenExistingValueIsMissing() {
        assertThat(mergePolicy.merge(mapValue(List.of("a-1")), null)).containsExactly("a-1");
    }

    private SplitBrainMergeTypes.MapMergeTypes<String, List<String>> mapValue(List<String> annotationIds) {
        @SuppressWarnings("unchecked")
        SplitBrainMergeTypes.MapMergeTypes<String, List<String>> mergingValue = mock(SplitBrainMergeTypes.MapMergeTypes.class);
        when(mergingValue.getValue()).thenReturn(annotationIds);
        return mergingValue;
    }
}
