package dev.ekhart.hzdemo.models.annotations;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.google.common.hash.Hashing;
import java.io.Serializable;
import java.nio.charset.StandardCharsets;
import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

@Value
@Jacksonized
@Builder(toBuilder = true)
@JsonIgnoreProperties(value = "id", allowGetters = true)
public class Annotation implements Serializable {

    private static final long serialVersionUID = 1L;

    String docId;
    String id;
    int start;
    int end;
    String value;

    static String deriveId(String docId, int start, int end, String value) {
        return Hashing.murmur3_128()
                .newHasher()
                .putString(docId == null ? "" : docId, StandardCharsets.UTF_8)
                .putInt(start)
                .putInt(end)
                .putBoolean(value != null)
                .putString(value == null ? "" : value, StandardCharsets.UTF_8)
                .hash()
                .toString();
    }

    // Overrides lombok build()
    public static class AnnotationBuilder {
        public Annotation build() {
            return new Annotation(docId, deriveId(docId, start, end, value), start, end, value);
        }
    }
}
