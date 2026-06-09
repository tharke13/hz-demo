package dev.ekhart.hzdemo.cacheservice.config;

import java.util.ArrayList;
import java.util.List;

import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.hazelcast")
public class HazelcastClientProperties {

    @Setter
    private String clusterName = "hz-demo-cluster";
    @Setter
    private String documentMapName = "document-annotation-ids";
    @Setter
    private String annotationMapName = "annotation-objects";
    @Setter
    private String routingMode = "ALL_MEMBERS";
    @Setter
    private boolean asyncStart = true;
    private List<String> members = new ArrayList<>(List.of(
            "localhost:5701",
            "localhost:5702",
            "localhost:5703"
    ));

    public String getClusterName() {
        return clusterName;
    }

    public String getDocumentMapName() {
        return documentMapName;
    }

    public String getAnnotationMapName() {
        return annotationMapName;
    }

    public String getRoutingMode() {
        return routingMode;
    }

    public boolean isAsyncStart() {
        return asyncStart;
    }

    public List<String> getMembers() {
        return members;
    }

    public void setMembers(List<String> members) {
        this.members = new ArrayList<>(members);
    }
}
