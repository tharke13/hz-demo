package dev.ekhart.hzdemo.cacheservice.member;

import com.hazelcast.nio.ObjectDataInput;
import com.hazelcast.nio.ObjectDataOutput;
import com.hazelcast.spi.merge.SplitBrainMergePolicy;
import com.hazelcast.spi.merge.SplitBrainMergeTypes;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

public class AnnotationIdListUnionMergePolicy implements
        SplitBrainMergePolicy<List<String>, SplitBrainMergeTypes.MapMergeTypes<String, List<String>>, List<String>> {

    @Override
    public List<String> merge(SplitBrainMergeTypes.MapMergeTypes<String, List<String>> mergingValue,
            SplitBrainMergeTypes.MapMergeTypes<String, List<String>> existingValue) {
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

    static List<String> union(List<String> preferred, List<String> incoming) {
        LinkedHashSet<String> merged = new LinkedHashSet<>();
        addAll(merged, preferred);
        addAll(merged, incoming);
        return List.copyOf(new ArrayList<>(merged));
    }

    private static void addAll(LinkedHashSet<String> merged, List<String> annotationIds) {
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

    private static List<String> copyOf(List<String> annotationIds) {
        return annotationIds == null ? List.of() : List.copyOf(annotationIds);
    }
}
