import com.hedera.services.bdd.junit.HederaContainer;
import com.hedera.services.bdd.junit.TestBase;
import com.hedera.services.bdd.spec.HapiApiSpec;
import com.hedera.services.bdd.spec.props.JutilPropertySource;
import org.junit.jupiter.api.BeforeAll;
import org.testcontainers.containers.Network;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.io.File;
import java.time.Duration;
import java.util.Collections;
import java.util.concurrent.TimeoutException;

/**
 * Base class for end-to-end tests
 */
@Testcontainers
public abstract class E2ETestBase extends TestBase {
    private static final File WORKSPACE = new File(System.getProperty("networkWorkspaceDir"));

    private static final DockerImageName IMAGE = DockerImageName.parse(System.getProperty("TAG"));

    private static final Network NETWORK = Network.newNetwork();

    /**
     * Using Testcontainers, create a node container. This code currently presupposes that a docker image has
     * been created out-of-band. The docker image name is supplied as TAG.
     */
    @Container
    private static final HederaContainer NODE_0 = new HederaContainer(IMAGE, 0)
            .withClasspathResourceMappingDir("network/config")
            .withWorkspace(WORKSPACE)
            .withNetwork(NETWORK);

    /**
     * Using Testcontainers, create a node container. This code currently presupposes that a docker image has
     * been created out-of-band. The docker image name is supplied as TAG.
     */
    @Container
    private static final HederaContainer NODE_1 = new HederaContainer(IMAGE, 1)
            .withClasspathResourceMappingDir("network/config")
            .withWorkspace(WORKSPACE)
            .withNetwork(NETWORK);

    /**
     * Using Testcontainers, create a node container. This code currently presupposes that a docker image has
     * been created out-of-band. The docker image name is supplied as TAG.
     */
    @Container
    private static final HederaContainer NODE_2 = new HederaContainer(IMAGE, 2)
            .withClasspathResourceMappingDir("network/config")
            .withWorkspace(WORKSPACE)
            .withNetwork(NETWORK);

    /**
     * Before any test runs, configure HapiApiSpec to use the Testcontainer we created
     */
    @BeforeAll
    static void beforeAll() {
        try {
            NODE_0.waitUntilActive(Duration.ofSeconds(30));
            NODE_1.waitUntilActive(Duration.ofSeconds(30));
            NODE_2.waitUntilActive(Duration.ofSeconds(30));
        } catch (TimeoutException e) {
            throw new RuntimeException(e);
        }

        final var defaultProperties = JutilPropertySource.getDefaultInstance();
        HapiApiSpec.runInCiMode(
                NODE_0.getFirstMappedPort() + ":" + NODE_1.getFirstMappedPort() + ":" + NODE_2.getFirstMappedPort(),
                defaultProperties.get("default.payer"),
                defaultProperties.get("default.node").split("\\.")[2],
                defaultProperties.get("tls"),
                defaultProperties.get("txn.proto.structure"),
                defaultProperties.get("node.selector"),
                Collections.emptyMap()
        );
    }
}
