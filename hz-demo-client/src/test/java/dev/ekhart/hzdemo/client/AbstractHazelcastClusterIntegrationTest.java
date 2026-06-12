package dev.ekhart.hzdemo.client;

import com.mongodb.client.MongoClients;
import java.nio.file.Path;
import java.time.Duration;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.containers.wait.strategy.WaitAllStrategy;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.images.builder.ImageFromDockerfile;

@Testcontainers(disabledWithoutDocker = true)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
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
    static final GenericContainer<?> RABBITMQ = new GenericContainer<>(DockerImageName.parse("rabbitmq:3.13-management"))
            .withNetwork(NETWORK)
            .withNetworkAliases("rabbitmq")
            .withExposedPorts(5672)
            .waitingFor(Wait.forListeningPort());

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
        registry.add("spring.rabbitmq.host", RABBITMQ::getHost);
        registry.add("spring.rabbitmq.port", () -> RABBITMQ.getMappedPort(5672));
        registry.add("spring.rabbitmq.username", () -> "guest");
        registry.add("spring.rabbitmq.password", () -> "guest");
        registry.add("app.annotation-object-sink.mongo.connection-string", MONGO::getConnectionString);
    }

    protected void clearCollections() {
        try (var mongoClient = MongoClients.create(MONGO.getConnectionString())) {
            var database = mongoClient.getDatabase("hz-demo");
            database.getCollection("annotation-objects").drop();
        }
    }

    protected void awaitHazelcastReady(Runnable action) throws InterruptedException {
        long deadline = System.nanoTime() + Duration.ofSeconds(30).toNanos();
        while (System.nanoTime() < deadline) {
            try {
                action.run();
                return;
            } catch (RuntimeException ignored) {
                // Hazelcast clients can briefly go offline while the cluster stabilizes.
            }
            Thread.sleep(250);
        }
        throw new AssertionError("Hazelcast client did not become ready in time");
    }

    protected void awaitCluster(dev.ekhart.hzdemo.client.service.ClientAnnotationService annotationService,
            int expectedMembers) throws InterruptedException {
        long deadline = System.nanoTime() + Duration.ofSeconds(180).toNanos();
        while (System.nanoTime() < deadline) {
            try {
                if (annotationService.clusterDetails().getConnectedMembers() >= expectedMembers) {
                    return;
                }
            }
            catch (RuntimeException ignored) {
                // The client may still be connecting while the cluster forms.
            }
            Thread.sleep(500);
        }
        throw new AssertionError("Hazelcast cluster did not reach " + expectedMembers + " members in time");
    }

    private static GenericContainer<?> hazelcastMember(String alias) {
        return new GenericContainer<>(HAZELCAST_MEMBER_IMAGE)
                .dependsOn(MONGO, RABBITMQ)
                .withNetwork(NETWORK)
                .withNetworkAliases(alias)
                .withExposedPorts(5701)
                .waitingFor(new WaitAllStrategy()
                        .withStrategy(Wait.forListeningPort())
                        .withStrategy(Wait.forLogMessage("(?s).*is STARTED.*", 1)))
                .withStartupTimeout(Duration.ofMinutes(8));
    }
}
