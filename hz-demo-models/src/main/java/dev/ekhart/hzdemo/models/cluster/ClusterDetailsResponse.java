package dev.ekhart.hzdemo.models.cluster;

import java.util.List;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class ClusterDetailsResponse {
    String clusterName;
    String mapName;
    int connectedMembers;
    List<String> members;
}
