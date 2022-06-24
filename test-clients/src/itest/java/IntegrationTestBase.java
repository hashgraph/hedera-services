import com.hedera.services.bdd.junit.HederaContainer;
import com.hedera.services.bdd.junit.TestBase;
import com.hedera.services.bdd.spec.HapiApiSpec;
import com.hedera.services.bdd.spec.props.JutilPropertySource;
import org.junit.ClassRule;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.testcontainers.containers.Network;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.CopyOption;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.concurrent.TimeoutException;

/**
 * Base class for integration tests
 */
@Testcontainers
public abstract class IntegrationTestBase extends TestBase {

    @ClassRule
    private static ClassNameWatcher classNameWatcher = new ClassNameWatcher();

    private static final File WORKSPACE = new File(System.getProperty("networkWorkspaceDir"));

    private static final File WORKSPACE_ARCHIVE = new File(WORKSPACE, "archive");

    private static final DockerImageName IMAGE = DockerImageName.parse(System.getProperty("TAG"));

    private static final Network NETWORK = Network.newNetwork();

    /**
     * Using Testcontainers, create a node container. This code currently presupposes that a docker image has
     * been created out-of-band. The docker image name is supplied as TAG.
     */
    @Container
    private static final HederaContainer NODE_0 = new HederaContainer(IMAGE, 0)
            .withClasspathResourceMappingDir("config")
            .withWorkspace(WORKSPACE)
            .withNetwork(NETWORK);

    /**
     * Before any test runs, configure HapiApiSpec to use the Testcontainer we created
     */
    @BeforeAll
    static void beforeAll() {
        try {
            NODE_0.waitUntilActive(Duration.ofSeconds(30));
        } catch (TimeoutException e) {
            throw new RuntimeException(e);
        }

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
    static void afterAll() throws IOException {
        if (!WORKSPACE.exists()) {
            return;
        }

        final File archiveFolder = new File(WORKSPACE_ARCHIVE,
                String.format("%s%s%s", classNameWatcher.getClassName(), File.separator, Instant.now().toString()));
        final File[] workspaceFiles = WORKSPACE.listFiles(
                (dir, name) -> name != null && !name.trim().equals(WORKSPACE_ARCHIVE.getName()));

        if (workspaceFiles == null || workspaceFiles.length == 0) {
            return;
        }

        if (!archiveFolder.exists()) {
            if (!archiveFolder.mkdirs()) {
                throw new FileNotFoundException(archiveFolder.getAbsolutePath());
            }
        }

        for (final File f : workspaceFiles) {
            Files.move(f.toPath(), archiveFolder.toPath().resolve(f.getName()), StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
