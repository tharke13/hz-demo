package dev.ekhart.hzdemo.cacheservice.member;

import static org.assertj.core.api.Assertions.assertThat;

import com.hazelcast.config.Config;
import com.hazelcast.config.MapStoreConfig;
import com.hazelcast.config.MergePolicyConfig;
import com.hazelcast.config.YamlConfigBuilder;
import com.hazelcast.spi.merge.PutIfAbsentMergePolicy;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class HazelcastMemberConfigTest {

    @Test
    void configuresSplitBrainMergePoliciesForAnnotationMaps() throws Exception {
        Config config = new YamlConfigBuilder(memberConfigPath().toString()).build();

        assertMergePolicy(config, "document-annotation-ids",
                AnnotationIdListUnionMergePolicy.class.getName());
        assertMergePolicy(config, "annotation-objects",
                PutIfAbsentMergePolicy.class.getName());
        assertAnnotationObjectStreamProperties(config);
    }

    private void assertMergePolicy(Config config, String mapName, String expectedPolicy) {
        MergePolicyConfig mergePolicyConfig = config.getMapConfig(mapName).getMergePolicyConfig();

        assertThat(mergePolicyConfig.getPolicy()).isEqualTo(expectedPolicy);
        assertThat(mergePolicyConfig.getBatchSize()).isEqualTo(100);
    }

    private void assertAnnotationObjectStreamProperties(Config config) {
        MapStoreConfig mapStoreConfig = config.getMapConfig("annotation-objects").getMapStoreConfig();

        assertThat(mapStoreConfig.getProperty("rabbitmq-uri")).isEqualTo("amqp://guest:guest@rabbitmq:5672/%2f");
        assertThat(mapStoreConfig.getProperty("rabbitmq-exchange")).isEqualTo("hz-demo.annotation-objects");
        assertThat(mapStoreConfig.getProperty("rabbitmq-routing-key")).isEqualTo("annotation.objects");
    }

    private Path memberConfigPath() {
        Path moduleRelativePath = Path.of("..", "config", "hazelcast-member.yaml").normalize();
        if (Files.exists(moduleRelativePath)) {
            return moduleRelativePath;
        }
        return Path.of("config", "hazelcast-member.yaml").normalize();
    }
}
