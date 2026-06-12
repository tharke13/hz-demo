package dev.ekhart.hzdemo.cacheservice.persistence;

import dev.ekhart.hzdemo.models.annotations.Annotation;
import org.bson.Document;

public final class AnnotationObjectDocuments {

    private AnnotationObjectDocuments() {
    }

    public static Document toDocument(Annotation annotation) {
        return new Document("_id", annotation.getId())
                .append("id", annotation.getId())
                .append("docId", annotation.getDocId())
                .append("start", annotation.getStart())
                .append("end", annotation.getEnd())
                .append("value", annotation.getValue());
    }

    public static Annotation toAnnotation(Document document) {
        return Annotation.builder()
                .docId(document.getString("docId"))
                .start(document.getInteger("start", 0))
                .end(document.getInteger("end", 0))
                .value(document.getString("value"))
                .build();
    }
}
