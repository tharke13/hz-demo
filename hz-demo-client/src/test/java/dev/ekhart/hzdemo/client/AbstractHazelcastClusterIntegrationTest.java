package dev.ekhart.hzdemo.client;

import java.nio.file.Path;
import java.time.Duration;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.images.builder.ImageFromDockerfile;

@Testcontainers(disabledWithoutDocker = true)
abstract class AbstractHazelcastClusterIntegrationTest {

    private static final Path REPO_ROOT = Path.of("..").toAbsolutePath().normalize();
    private static final ImageFromDockerfile HAZELCAST_MEMBER_IMAGE
            = new ImageFromDockerfile("hz-demo-hazelcast-member-test", false)
            .withFileFromPath(
                    "docker/hazelcast-member/Dockerfile",
                    REPO_ROOT.resolve("docker").resolve("hazelcast-member").resolve("Dockerfile")
            )
            .withDockerfilePath("docker/hazelcast-member/Dockerfile")
            .withFileFromPath(
                    "config/hazelcast-member.yaml",
                    REPO_ROOT.resolve("config").resolve("hazelcast-member.yaml")
            )
            .withFileFromPath(
                    "hz-demo-cache-service/target/classes",
                    REPO_ROOT.resolve("hz-demo-cache-service").resolve("target").resolve("classes")
            )
            .withFileFromPath(
                    "hz-demo-cache-service/target/member-libs",
                    REPO_ROOT.resolve("hz-demo-cache-service").resolve("target").resolve("member-libs")
            );
    private static final Network NETWORK = Network.newNetwork();

    @Container
    static final MongoDBContainer MONGO = new MongoDBContainer(DockerImageName.parse("mongo:7.0"))
            .withNetwork(NETWORK)
            .withNetworkAliases("mongo");

    @Container
    static final GenericContainer<?> HAZELCAST_1 = hazelcastMember("hazelcast-1");

    @Container
    static final GenericContainer<?> HAZELCAST_2 = hazelcastMember("hazelcast-2");

    @Container
    static final GenericContainer<?> HAZELCAST_3 = hazelcastMember("hazelcast-3");

    @DynamicPropertySource
    static void hazelcastProperties(DynamicPropertyRegistry registry) {
        registry.add("app.hazelcast.routing-mode", () -> "SINGLE_MEMBER");
        registry.add("app.hazelcast.members[0]", () -> "localhost:" + HAZELCAST_1.getMappedPort(5701));
        registry.add("app.hazelcast.members[1]", () -> "localhost:" + HAZELCAST_2.getMappedPort(5701));
        registry.add("app.hazelcast.members[2]", () -> "localhost:" + HAZELCAST_3.getMappedPort(5701));
    }

    protected void awaitCluster(dev.ekhart.hzdemo.client.service.ClientAnnotationService annotationService,
            int expectedMembers) throws InterruptedException {
        long deadline = System.nanoTime() + Duration.ofSeconds(30).toNanos();
        while (System.nanoTime() < deadline) {
            try {
                if (annotationService.clusterDetails().getConnectedMembers() == expectedMembers) {
                    return;
                }
            }
            catch (RuntimeException ignored) {
                // The client may still be connecting while the cluster forms.
            }
            Thread.sleep(250);
        }
        throw new AssertionError("Hazelcast cluster did not reach " + expectedMembers + " members in time");
    }

    private static GenericContainer<?> hazelcastMember(String alias) {
        return new GenericContainer<>(HAZELCAST_MEMBER_IMAGE)
                .dependsOn(MONGO)
                .withNetwork(NETWORK)
                .withNetworkAliases(alias)
                .withExposedPorts(5701)
                .waitingFor(Wait.forListeningPort())
                .withStartupTimeout(Duration.ofMinutes(5));
    }
}
