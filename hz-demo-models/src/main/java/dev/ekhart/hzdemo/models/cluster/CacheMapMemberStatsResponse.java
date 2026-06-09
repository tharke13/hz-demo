package dev.ekhart.hzdemo.models.cluster;

import java.io.Serializable;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class CacheMapMemberStatsResponse implements Serializable {
    private static final long serialVersionUID = 1L;

    String member;
    long ownedEntryCount;
    long backupEntryCount;
    int backupCount;
    long lockedEntryCount;
    long dirtyEntryCount;
    long hits;
    long putOperationCount;
    long getOperationCount;
    long removeOperationCount;
    long heapCost;
    long lastAccessTime;
    long lastUpdateTime;
}
