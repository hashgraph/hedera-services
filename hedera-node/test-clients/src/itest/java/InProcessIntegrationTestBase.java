/*
 * Copyright (C) 2022-2023 Hedera Hashgraph, LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import static java.util.Objects.requireNonNull;

import com.hedera.node.app.Hedera;
import com.hedera.services.bdd.junit.TestBase;
import com.hedera.services.bdd.spec.HapiSpec;
import com.hedera.services.bdd.spec.props.JutilPropertySource;
import com.swirlds.common.config.BasicConfig;
import com.swirlds.common.config.ConsensusConfig;
import com.swirlds.common.config.OSHealthCheckConfig;
import com.swirlds.common.config.StateConfig;
import com.swirlds.common.config.WiringConfig;
import com.swirlds.common.config.singleton.ConfigurationHolder;
import com.swirlds.common.config.sources.LegacyFileConfigSource;
import com.swirlds.common.constructable.ConstructableRegistry;
import com.swirlds.common.context.DefaultPlatformContext;
import com.swirlds.common.crypto.CryptographyHolder;
import com.swirlds.common.crypto.config.CryptoConfig;
import com.swirlds.common.io.config.TemporaryFileConfig;
import com.swirlds.common.merkle.synchronization.config.ReconnectConfig;
import com.swirlds.common.metrics.Metrics;
import com.swirlds.common.metrics.config.MetricsConfig;
import com.swirlds.common.metrics.platform.DefaultMetricsProvider;
import com.swirlds.common.metrics.platform.prometheus.PrometheusConfig;
import com.swirlds.common.system.BasicSoftwareVersion;
import com.swirlds.common.system.NodeId;
import com.swirlds.common.system.SwirldState;
import com.swirlds.common.system.address.Address;
import com.swirlds.common.system.address.AddressBook;
import com.swirlds.config.api.spi.ConfigurationBuilderFactory;
import com.swirlds.fchashmap.config.FCHashMapConfig;
import com.swirlds.jasperdb.config.JasperDbConfig;
import com.swirlds.platform.Crypto;
import com.swirlds.platform.CryptoMetrics;
import com.swirlds.platform.SwirldsPlatform;
import com.swirlds.platform.config.AddressBookConfig;
import com.swirlds.platform.config.ThreadConfig;
import com.swirlds.platform.crypto.CryptoStatic;
import com.swirlds.platform.dispatch.DispatchConfiguration;
import com.swirlds.platform.gossip.chatter.config.ChatterConfig;
import com.swirlds.platform.gossip.sync.config.SyncConfig;
import com.swirlds.platform.recovery.EmergencyRecoveryManager;
import com.swirlds.platform.state.signed.ReservedSignedState;
import com.swirlds.platform.uptime.UptimeConfig;
import com.swirlds.virtualmap.config.VirtualMapConfig;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.net.InetSocketAddress;
import java.nio.file.Path;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.concurrent.Executors;
import java.util.function.Supplier;
import org.apache.logging.log4j.core.config.ConfigurationSource;
import org.apache.logging.log4j.core.config.Configurator;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.io.TempDir;

/**
 * Base class for integration tests. Not currently used. Once the e2e tests are all passing, we'll switch
 * {@link AllIntegrationTests} to use this instead of `DockerIntegrationTestBase` -- most likely.
 */
public abstract class InProcessIntegrationTestBase extends TestBase {
    static {
        System.setProperty("java.util.logging.manager", "org.apache.logging.log4j.jul.LogManager");
    }

    static Hedera hedera;

    /** Create and start a node, and configure the HapiSpec to use that node. */
    @BeforeAll
    static void beforeAll(@TempDir @NonNull final Path tmpDir) throws Exception {
        // Setup logging
        try (final var log4j2ConfigFile = InProcessIntegrationTestBase.class.getResourceAsStream("/log4j2.xml")) {
            final var source = new ConfigurationSource(requireNonNull(log4j2ConfigFile));
            try (final var ignored = Configurator.initialize(null, source)) {
                // Nothing to do here.
            }
        }

        // Setup the constructable registry so the platform can deserialize the state.
        final var registry = ConstructableRegistry.getInstance();
        registry.reset();
        registry.registerConstructables("com.swirlds.merklemap");
        registry.registerConstructables("com.swirlds.jasperdb");
        registry.registerConstructables("com.swirlds.fcqueue");
        registry.registerConstructables("com.swirlds.virtualmap");
        registry.registerConstructables("com.swirlds.common.merkle");
        registry.registerConstructables("com.swirlds.common");
        registry.registerConstructables("com.swirlds.merkle");
        registry.registerConstructables("com.swirlds.merkle.tree");

        // 1. Create a configuration instance with any desired overrides.
        final var factory = ServiceLoader.load(ConfigurationBuilderFactory.class);
        final var configBuilder = factory.findFirst().orElseThrow().create();
        final var config = configBuilder
                .withConfigDataType(BasicConfig.class)
                .withConfigDataType(StateConfig.class)
                .withConfigDataType(CryptoConfig.class)
                .withConfigDataType(TemporaryFileConfig.class)
                .withConfigDataType(ReconnectConfig.class)
                .withConfigDataType(FCHashMapConfig.class)
                .withConfigDataType(JasperDbConfig.class)
                .withConfigDataType(ChatterConfig.class)
                .withConfigDataType(AddressBookConfig.class)
                .withConfigDataType(VirtualMapConfig.class)
                .withConfigDataType(ConsensusConfig.class)
                .withConfigDataType(ThreadConfig.class)
                .withConfigDataType(DispatchConfiguration.class)
                .withConfigDataType(MetricsConfig.class)
                .withConfigDataType(PrometheusConfig.class)
                .withConfigDataType(OSHealthCheckConfig.class)
                .withConfigDataType(WiringConfig.class)
                .withConfigDataType(SyncConfig.class)
                .withConfigDataType(UptimeConfig.class)
                // 2. Configure Settings
                .withSource(new LegacyFileConfigSource(tmpDir.resolve("settings.txt")))
                .build();

        ConfigurationHolder.getInstance().setConfiguration(config);
        CryptographyHolder.reset();

        final var port = new InetSocketAddress(0).getPort();

        System.setProperty("version.services", "0.40.0"); // TBD Get from actual build args...
        System.setProperty("version.hapi", "0.40.0"); // TBD Get from actual build args...
        System.setProperty(
                "hedera.recordStream.logDir", tmpDir.resolve("recordStream").toString());
        System.setProperty("accounts.storeOnDisk", "true");
        System.setProperty("grpc.port", "0");
        System.setProperty("grpc.tlsPort", "0");
        System.setProperty("grpc.workflowsPort", "0");
        System.setProperty("grpc.workflowsTlsPort", "0");
        System.setProperty("hedera.workflows.enabled", "CryptoCreate");

        // 3. Create a new Node ID for our node
        final var nodeId = new NodeId(0);

        // 4. Set up Metrics
        final var metricsProvider = new DefaultMetricsProvider(config);
        final Metrics globalMetrics = metricsProvider.createGlobalMetrics();
        CryptoMetrics.registerMetrics(globalMetrics);

        // 5. Create the Platform Context
        final var platformContext = new DefaultPlatformContext(
                config, metricsProvider.createPlatformMetrics(nodeId), CryptographyHolder.get());

        // 6. Create an Address Book
        final var addressBook = new AddressBook();
        addressBook.add(new Address(
                nodeId,
                "TEST0",
                "TEST0",
                1,
                new byte[] {127, 0, 0, 1},
                port,
                new byte[] {127, 0, 0, 1},
                port,
                "0.0.3"));

        // 7. Setup some cryptography
        //        final var crypto = CryptoSetup.initNodeSecurity(addressBook, config)[0];
        final var keysAndCertsForAllNodes =
                CryptoStatic.generateKeysAndCerts(addressBook, Executors.newFixedThreadPool(4));
        final var crypto = new Crypto(keysAndCertsForAllNodes.get(nodeId), Executors.newFixedThreadPool(4));
        CryptographyHolder.get().digestSync(addressBook);

        // 8. Create the Main
        hedera = new Hedera(registry);

        // 9. Create a SwirldsPlatform (using nasty reflection. Eek).
        final var constructor = SwirldsPlatform.class.getDeclaredConstructors()[0];
        constructor.setAccessible(true);
        // @NonNull Supplier<SwirldState> genesisStateBuilder, @NonNull ReservedSignedState loadedSignedState, @NonNull
        // EmergencyRecoveryManager emergencyRecoveryManager
        final SwirldsPlatform platform = (SwirldsPlatform) constructor.newInstance(
                platformContext,
                crypto,
                addressBook,
                nodeId,
                "Hedera",
                "Hedera",
                new BasicSoftwareVersion(
                        Long.MAX_VALUE), // TBD: Use the same as what we are passing to the services version
                (Supplier<SwirldState>) hedera::newState,
                ReservedSignedState.createNullReservation(),
                new EmergencyRecoveryManager(
                        (s, exitCode) -> {
                            System.out.println("Asked to shutdownGrpcServer because of " + s);
                            System.exit(exitCode.getExitCode());
                        },
                        tmpDir.resolve("recovery")));

        // 10. Init and Start
        hedera.init(platform, nodeId);
        platform.start();

        // 11. Initialize the HAPI Spec system
        final var defaultProperties = JutilPropertySource.getDefaultInstance();
        HapiSpec.runInCiMode(
                String.valueOf(hedera.getGrpcPort()),
                defaultProperties.get("default.payer"),
                defaultProperties.get("default.node").split("\\.")[2],
                defaultProperties.get("tls"),
                defaultProperties.get("txn.proto.structure"),
                defaultProperties.get("node.selector"),
                Map.of("recordStream.path", tmpDir.resolve("recordStream").toString()));
    }

    @AfterAll
    static void afterAll() {
        if (hedera != null) {
            hedera.shutdownGrpcServer();
        }
    }
}
