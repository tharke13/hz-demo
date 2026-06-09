package dev.ekhart.hzdemo.cacheservice.service;

import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.core.HazelcastInstanceAware;
import com.hazelcast.map.IMap;
import com.hazelcast.map.LocalMapStats;
import dev.ekhart.hzdemo.models.cluster.CacheMapMemberStatsResponse;
import java.io.Serializable;
import java.util.concurrent.Callable;

class CacheMapStatsCallable implements Callable<CacheMapMemberStatsResponse>, HazelcastInstanceAware, Serializable {

    private static final long serialVersionUID = 1L;

    private final String mapName;
    private transient HazelcastInstance hazelcastInstance;

    CacheMapStatsCallable(String mapName) {
        this.mapName = mapName;
    }

    @Override
    public void setHazelcastInstance(HazelcastInstance hazelcastInstance) {
        this.hazelcastInstance = hazelcastInstance;
    }

    @Override
    public CacheMapMemberStatsResponse call() {
        IMap<Object, Object> map = hazelcastInstance.getMap(mapName);
        LocalMapStats localMapStats = map.getLocalMapStats();
        return CacheMapMemberStatsResponse.builder()
                .member(hazelcastInstance.getCluster().getLocalMember().getAddress().toString())
                .ownedEntryCount(localMapStats.getOwnedEntryCount())
                .backupEntryCount(localMapStats.getBackupEntryCount())
                .backupCount(localMapStats.getBackupCount())
                .lockedEntryCount(localMapStats.getLockedEntryCount())
                .dirtyEntryCount(localMapStats.getDirtyEntryCount())
                .hits(localMapStats.getHits())
                .putOperationCount(localMapStats.getPutOperationCount())
                .getOperationCount(localMapStats.getGetOperationCount())
                .removeOperationCount(localMapStats.getRemoveOperationCount())
                .heapCost(localMapStats.getHeapCost())
                .lastAccessTime(localMapStats.getLastAccessTime())
                .lastUpdateTime(localMapStats.getLastUpdateTime())
                .build();
    }
}
