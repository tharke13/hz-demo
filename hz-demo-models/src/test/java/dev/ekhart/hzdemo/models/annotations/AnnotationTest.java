package dev.ekhart.hzdemo.models.annotations;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class AnnotationTest {

    @Test
    void derivesStableIdFromStartEndAndValue() {
        Annotation first = Annotation.builder()
                .start(1)
                .end(5)
                .value("TAG-A")
                .build();
        Annotation second = Annotation.builder()
                .start(1)
                .end(5)
                .value("TAG-A")
                .build();
        Annotation different = Annotation.builder()
                .start(1)
                .end(6)
                .value("TAG-A")
                .build();

        assertThat(first.getId()).isEqualTo(second.getId());
        assertThat(first.getId()).isNotBlank();
        assertThat(different.getId()).isNotEqualTo(first.getId());
    }
}
