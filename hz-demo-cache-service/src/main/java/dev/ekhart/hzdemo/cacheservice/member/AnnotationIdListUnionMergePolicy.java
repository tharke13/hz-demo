package dev.ekhart.hzdemo.cacheservice.member;

import com.hazelcast.nio.ObjectDataInput;
import com.hazelcast.nio.ObjectDataOutput;
import com.hazelcast.spi.merge.SplitBrainMergePolicy;
import com.hazelcast.spi.merge.SplitBrainMergeTypes;
import java.io.IOException;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

public class AnnotationIdListUnionMergePolicy implements
        SplitBrainMergePolicy<Set<String>, SplitBrainMergeTypes.MapMergeTypes<String, Set<String>>, Set<String>> {

    @Override
    public Set<String> merge(SplitBrainMergeTypes.MapMergeTypes<String, Set<String>> mergingValue,
            SplitBrainMergeTypes.MapMergeTypes<String, Set<String>> existingValue) {
        if (existingValue == null) {
            return copyOf(mergingValue.getValue());
        }
        return union(existingValue.getValue(), mergingValue.getValue());
    }

    @Override
    public void writeData(ObjectDataOutput out) throws IOException {
    }

    @Override
    public void readData(ObjectDataInput in) throws IOException {
    }

    static Set<String> union(Set<String> preferred, Set<String> incoming) {
        LinkedHashSet<String> merged = new LinkedHashSet<>();
        addAll(merged, preferred);
        addAll(merged, incoming);
        return Collections.unmodifiableSet(merged);
    }

    private static void addAll(LinkedHashSet<String> merged, Set<String> annotationIds) {
        if (annotationIds == null) {
            return;
        }
        for (String annotationId : annotationIds) {
            if (annotationId == null || annotationId.isBlank()) {
                continue;
            }
            merged.add(annotationId);
        }
    }

    private static Set<String> copyOf(Set<String> annotationIds) {
        if (annotationIds == null) {
            return Set.of();
        }
        LinkedHashSet<String> copy = new LinkedHashSet<>();
        addAll(copy, annotationIds);
        return Collections.unmodifiableSet(copy);
    }
}
