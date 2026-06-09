package dev.ekhart.hzdemo.models.cluster;

import java.io.Serializable;
import java.util.List;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class CacheMapStatsResponse implements Serializable {
    private static final long serialVersionUID = 1L;

    String mapName;
    int totalEntryCount;
    List<CacheMapMemberStatsResponse> memberStats;
}
