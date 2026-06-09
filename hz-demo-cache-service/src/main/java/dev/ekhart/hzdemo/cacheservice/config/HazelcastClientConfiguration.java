package dev.ekhart.hzdemo.cacheservice.config;

import com.hazelcast.client.HazelcastClient;
import com.hazelcast.client.config.ClientConfig;
import com.hazelcast.client.config.ClientConnectionStrategyConfig;
import com.hazelcast.client.config.ClientNetworkConfig;
import com.hazelcast.core.HazelcastInstance;
import java.util.Locale;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(HazelcastClientProperties.class)
public class HazelcastClientConfiguration {

    @Bean(destroyMethod = "shutdown")
    HazelcastInstance hazelcastInstance(HazelcastClientProperties properties) {
        ClientConfig clientConfig = new ClientConfig();
        clientConfig.setClusterName(properties.getClusterName());
        clientConfig.setInstanceName("hz-demo-client");
        clientConfig.getConnectionStrategyConfig()
                .setAsyncStart(properties.isAsyncStart())
                .setReconnectMode(ClientConnectionStrategyConfig.ReconnectMode.ASYNC);

        ClientNetworkConfig networkConfig = clientConfig.getNetworkConfig();
        networkConfig.addAddress(properties.getMembers().toArray(String[]::new));
        networkConfig.setSmartRouting(isSmartRouting(properties.getRoutingMode()));

        return HazelcastClient.newHazelcastClient(clientConfig);
    }

    private boolean isSmartRouting(String configuredMode) {
        String normalizedMode = configuredMode.toUpperCase(Locale.ROOT);
        if ("ALL_MEMBERS".equals(normalizedMode)) {
            return true;
        }
        if ("SINGLE_MEMBER".equals(normalizedMode)) {
            return false;
        }
        throw new IllegalArgumentException("Unsupported Hazelcast routing mode for Hazelcast 5.1: " + configuredMode);
    }
}
