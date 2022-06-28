import com.hedera.services.bdd.junit.HederaContainer;
import com.hedera.services.bdd.junit.TestBase;
import com.hedera.services.bdd.spec.HapiApiSpec;
import com.hedera.services.bdd.spec.props.JutilPropertySource;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testcontainers.containers.Network;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.io.File;
import java.time.Duration;
import java.util.Collections;
import java.util.concurrent.TimeoutException;

/**
 * Base class for integration tests
 */
@Testcontainers
@ExtendWith(ResultArchivingExtension.class)
public abstract class IntegrationTestBase extends TestBase {
    private static final File WORKSPACE = new File(System.getProperty("networkWorkspaceDir"));

    private static final DockerImageName IMAGE = DockerImageName.parse(System.getProperty("TAG"));

    private static final Network NETWORK = Network.newNetwork();

    /**
     * Using Testcontainers, create a node container. This code currently presupposes that a docker image has
     * been created out-of-band. The docker image name is supplied as TAG.
     */
    private static final HederaContainer NODE_0 = new HederaContainer(IMAGE, 0)
            .withClasspathResourceMappingDir("network/config")
            .withWorkspace(WORKSPACE)
            .withNetwork(NETWORK);

    /**
     * Before any test runs, configure HapiApiSpec to use the Testcontainer we created
     */
    @BeforeAll
    static void beforeAll() throws TimeoutException {
        NODE_0.start();
        NODE_0.waitUntilActive(Duration.ofSeconds(30));

        final var defaultProperties = JutilPropertySource.getDefaultInstance();
        HapiApiSpec.runInCiMode(
                "" + NODE_0.getFirstMappedPort(),
                defaultProperties.get("default.payer"),
                defaultProperties.get("default.node").split("\\.")[2],
                defaultProperties.get("tls"),
                defaultProperties.get("txn.proto.structure"),
                defaultProperties.get("node.selector"),
                Collections.emptyMap()
        );
    }

    @AfterAll
    static void afterAll() throws TimeoutException {
        NODE_0.stop();
        NODE_0.waitUntilStopped(Duration.ofMinutes(1));
    }
}
