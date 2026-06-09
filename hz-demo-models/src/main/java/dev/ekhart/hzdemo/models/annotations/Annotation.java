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
@JsonIgnoreProperties(value = "id", allowGetters = true)
public class Annotation implements Serializable {

    private static final long serialVersionUID = 1L;

    String id;
    int start;
    int end;
    String value;

    @Builder
    private Annotation(int start, int end, String value) {
        this.id = deriveId(start, end, value);
        this.start = start;
        this.end = end;
        this.value = value;
    }

    static String deriveId(int start, int end, String value) {
        return Hashing.murmur3_128()
                .newHasher()
                .putInt(start)
                .putInt(end)
                .putBoolean(value != null)
                .putString(value == null ? "" : value, StandardCharsets.UTF_8)
                .hash()
                .toString();
    }
}
