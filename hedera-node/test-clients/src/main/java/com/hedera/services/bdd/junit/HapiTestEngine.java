/*
 * Copyright (C) 2023 Hedera Hashgraph, LLC
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

package com.hedera.services.bdd.junit;

import static java.util.Objects.requireNonNull;
import static org.junit.platform.commons.util.ReflectionUtils.HierarchyTraversalMode.TOP_DOWN;

import com.hedera.node.app.Hedera;
import com.hedera.node.config.data.AccountsConfig;
import com.hedera.node.config.data.StakingConfig;
import com.hedera.node.config.data.TokensConfig;
import com.hedera.services.bdd.spec.HapiSpec;
import com.hedera.services.bdd.spec.props.JutilPropertySource;
import com.hedera.services.bdd.suites.HapiSuite;
import com.swirlds.common.config.BasicConfig;
import com.swirlds.common.config.ConsensusConfig;
import com.swirlds.common.config.EventConfig;
import com.swirlds.common.config.OSHealthCheckConfig;
import com.swirlds.common.config.SocketConfig;
import com.swirlds.common.config.StateConfig;
import com.swirlds.common.config.TransactionConfig;
import com.swirlds.common.config.WiringConfig;
import com.swirlds.common.config.singleton.ConfigurationHolder;
import com.swirlds.common.config.sources.LegacyFileConfigSource;
import com.swirlds.common.config.sources.SystemPropertiesConfigSource;
import com.swirlds.common.constructable.ConstructableRegistry;
import com.swirlds.common.context.DefaultPlatformContext;
import com.swirlds.common.crypto.CryptographyHolder;
import com.swirlds.common.crypto.config.CryptoConfig;
import com.swirlds.common.io.config.TemporaryFileConfig;
import com.swirlds.common.io.utility.FileUtils;
import com.swirlds.common.io.utility.RecycleBin;
import com.swirlds.common.merkle.synchronization.config.ReconnectConfig;
import com.swirlds.common.metrics.Metrics;
import com.swirlds.common.metrics.config.MetricsConfig;
import com.swirlds.common.metrics.platform.DefaultMetricsProvider;
import com.swirlds.common.metrics.platform.prometheus.PrometheusConfig;
import com.swirlds.common.notification.listeners.PlatformStatusChangeListener;
import com.swirlds.common.system.BasicSoftwareVersion;
import com.swirlds.common.system.NodeId;
import com.swirlds.common.system.address.Address;
import com.swirlds.common.system.address.AddressBook;
import com.swirlds.common.system.status.PlatformStatus;
import com.swirlds.common.system.status.PlatformStatusConfig;
import com.swirlds.config.api.spi.ConfigurationBuilderFactory;
import com.swirlds.fchashmap.config.FCHashMapConfig;
import com.swirlds.merkledb.config.MerkleDbConfig;
import com.swirlds.platform.Crypto;
import com.swirlds.platform.CryptoMetrics;
import com.swirlds.platform.SwirldsPlatform;
import com.swirlds.platform.config.AddressBookConfig;
import com.swirlds.platform.config.ThreadConfig;
import com.swirlds.platform.crypto.CryptoStatic;
import com.swirlds.platform.dispatch.DispatchConfiguration;
import com.swirlds.platform.event.preconsensus.PreconsensusEventStreamConfig;
import com.swirlds.platform.event.tipset.EventCreationConfig;
import com.swirlds.platform.gossip.chatter.config.ChatterConfig;
import com.swirlds.platform.gossip.sync.config.SyncConfig;
import com.swirlds.platform.recovery.EmergencyRecoveryManager;
import com.swirlds.platform.state.GenesisStateBuilder;
import com.swirlds.platform.state.signed.SignedState;
import com.swirlds.platform.uptime.UptimeConfig;
import com.swirlds.virtualmap.config.VirtualMapConfig;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.io.IOException;
import java.lang.reflect.Method;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;
import org.apache.logging.log4j.core.config.ConfigurationSource;
import org.apache.logging.log4j.core.config.Configurator;
import org.junit.jupiter.api.Disabled;
import org.junit.platform.commons.support.AnnotationSupport;
import org.junit.platform.commons.support.ReflectionSupport;
import org.junit.platform.commons.util.ReflectionUtils;
import org.junit.platform.engine.EngineDiscoveryRequest;
import org.junit.platform.engine.ExecutionRequest;
import org.junit.platform.engine.Filter;
import org.junit.platform.engine.TestDescriptor;
import org.junit.platform.engine.TestEngine;
import org.junit.platform.engine.UniqueId;
import org.junit.platform.engine.discovery.ClassSelector;
import org.junit.platform.engine.discovery.ClasspathRootSelector;
import org.junit.platform.engine.discovery.MethodSelector;
import org.junit.platform.engine.discovery.PackageNameFilter;
import org.junit.platform.engine.support.descriptor.AbstractTestDescriptor;
import org.junit.platform.engine.support.descriptor.ClassSource;
import org.junit.platform.engine.support.descriptor.EngineDescriptor;
import org.junit.platform.engine.support.descriptor.MethodSource;
import org.junit.platform.engine.support.hierarchical.HierarchicalTestEngine;
import org.junit.platform.engine.support.hierarchical.Node;

/**
 * An implementation of a JUnit {@link TestEngine} to execute HAPI Specification Tests.
 *
 * <p>This implementation automatically locates all test classes annotated with {@link HapiTestSuite}. Within those
 * classes, any methods that take no args and return a {@link HapiSpec} will be picked up as test methods, but will be
 * skipped. Any of those methods that are also annotated with {@link HapiTest} will be executed. Such methods also
 * support the JUnit Jupiter {@link Disabled} annotation.
 */
public class HapiTestEngine extends HierarchicalTestEngine<HapiTestEngineExecutionContext> /* implements TestEngine */ {
    static {
        // This is really weird, but it exists because we have to force JUL to use Log4J as early as possible.
        System.setProperty("java.util.logging.manager", "org.apache.logging.log4j.jul.LogManager");
    }

    /**
     * Tests whether a class is annotated with {@link HapiTestSuite}.
     */
    private static final Predicate<Class<?>> IS_HAPI_TEST_SUITE =
            classCandidate -> AnnotationSupport.isAnnotated(classCandidate, HapiTestSuite.class);

    /**
     * Tests whether a method is annotated with {@link HapiTest}, or whether it is a no-arg method that returns a
     * {@link HapiSpec}. Any of the former type of method will be executed, while any of the latter will be skipped.
     */
    private static final Predicate<Method> IS_HAPI_TEST =
            methodCandidate -> AnnotationSupport.isAnnotated(methodCandidate, HapiTest.class)
                    || (methodCandidate.getParameterCount() == 0 && methodCandidate.getReturnType() == HapiSpec.class);

    @Override
    public String getId() {
        return "hapi-suite-test";
    }

    /**
     * {@inheritDoc}
     *
     * <p>This method is responsible for discovering the classes and methods that form our tests. It constructs a tree
     * of {@link TestDescriptor}s, one for each class annotated with {@link HapiTestSuite}, where each such
     * {@link ClassTestDescriptor} has a child for each spec method (whether, or not annotated with {@link HapiTest}).
     */
    @Override
    public TestDescriptor discover(EngineDiscoveryRequest discoveryRequest, UniqueId uniqueId) {
        final var engineDescriptor = new HapiEngineDescriptor(uniqueId);

        discoveryRequest.getSelectorsByType(MethodSelector.class).forEach(selector -> {
            final var javaClass = selector.getJavaClass();
            addChildToEngineDescriptor(javaClass, discoveryRequest, engineDescriptor);
        });

        discoveryRequest.getSelectorsByType(ClassSelector.class).forEach(selector -> {
            final var javaClass = selector.getJavaClass();
            addChildToEngineDescriptor(javaClass, discoveryRequest, engineDescriptor);
        });

        discoveryRequest.getSelectorsByType(ClasspathRootSelector.class).forEach(selector -> {
            appendTestsInClasspathRoot(selector.getClasspathRoot(), engineDescriptor, discoveryRequest);
        });

        return engineDescriptor;
    }

    private static void addChildToEngineDescriptor(
            Class<?> javaClass, EngineDiscoveryRequest discoveryRequest, EngineDescriptor engineDescriptor) {
        if (IS_HAPI_TEST_SUITE.test(javaClass)) {
            discoveryRequest.getConfigurationParameters().keySet().forEach(System.out::println);
            final var classDescriptor = new ClassTestDescriptor(javaClass, engineDescriptor, discoveryRequest);
            if (!classDescriptor.skip) {
                engineDescriptor.addChild(classDescriptor);
            }
        }
    }

    private void appendTestsInClasspathRoot(
            URI uri, TestDescriptor engineDescriptor, EngineDiscoveryRequest discoveryRequest) {
        ReflectionSupport.findAllClassesInClasspathRoot(uri, IS_HAPI_TEST_SUITE, name -> true).stream()
                .filter(aClass -> discoveryRequest.getFiltersByType(PackageNameFilter.class).stream()
                        .map(Filter::toPredicate)
                        .allMatch(predicate -> predicate.test(aClass.getPackageName())))
                .map(aClass -> new ClassTestDescriptor(aClass, engineDescriptor, discoveryRequest))
                .filter(classTestDescriptor -> !classTestDescriptor.skip)
                .forEach(engineDescriptor::addChild);
    }

    /**
     * {@inheritDoc}
     *
     * <p>We don't need to do anything here, just return our phony context
     */
    @Override
    protected HapiTestEngineExecutionContext createExecutionContext(ExecutionRequest request) {
        // Populating the data needed for the context
        return new HapiTestEngineExecutionContext(Path.of("data"), Path.of("eventstreams"));
    }

    private static final class HapiEngineDescriptor extends EngineDescriptor
            implements Node<HapiTestEngineExecutionContext> {
        /** The Hedera instance we are testing */
        private Hedera hedera;

        public HapiEngineDescriptor(UniqueId uniqueId) {
            super(uniqueId, "Hapi Test");
        }

        @Override
        public HapiTestEngineExecutionContext before(HapiTestEngineExecutionContext context) {
            // If there are no children, then there is nothing to do.
            if (super.getChildren().isEmpty()) {
                return context;
            }

            try {
                // Deleting the test data. Currently, we are deleting the data/saved and the eventstreams folders.
                // We need to do that in order to be able to run all tests at the same time. Without that the tests
                // are interfering with each other.
                // Also, If we encounter a scenario where tests in the same suite are interfering with each other we
                // can move this logic inside the after method in the MethodTestDescriptor class.
                // This way we will clean up the data after each test.
                if (context.getSavedStateDirectory() != null)
                    FileUtils.deleteDirectory(context.getSavedStateDirectory());
                if (context.getEventsLogDir() != null) FileUtils.deleteDirectory(context.getEventsLogDir());

                final var tmpDir = Files.createTempDirectory("hapiTest");

                // Setup logging
                try (final var log4j2ConfigFile = HapiTestEngine.class.getResourceAsStream("/log4j2.xml")) {
                    final var source = new ConfigurationSource(requireNonNull(log4j2ConfigFile));
                    try (final var ignored = Configurator.initialize(null, source)) {
                        // Nothing to do here.
                    }
                }

                // Setup the constructable registry so the platform can deserialize the state.
                final var registry = ConstructableRegistry.getInstance();
                registry.reset();
                registry.registerConstructables("com.swirlds.merklemap");
                registry.registerConstructables("com.swirlds.merkledb");
                registry.registerConstructables("com.swirlds.fcqueue");
                registry.registerConstructables("com.swirlds.virtualmap");
                registry.registerConstructables("com.swirlds.common.merkle");
                registry.registerConstructables("com.swirlds.common");
                registry.registerConstructables("com.swirlds.merkle");
                registry.registerConstructables("com.swirlds.merkle.tree");

                // 1. Create a configuration instance with any desired overrides.
                System.setProperty("version.services", "0.40.0"); // TBD Get from actual build args...
                System.setProperty("version.hapi", "0.40.0"); // TBD Get from actual build args...
                System.setProperty(
                        "hedera.recordStream.logDir",
                        tmpDir.resolve("recordStream").toString());
                System.setProperty("accounts.storeOnDisk", "true");
                System.setProperty("grpc.port", "0");
                System.setProperty("grpc.tlsPort", "0");
                System.setProperty("grpc.workflowsPort", "0");
                System.setProperty("grpc.workflowsTlsPort", "0");
                System.setProperty("hedera.workflows.enabled", "CryptoCreate");
                System.setProperty("platformStatus.observingStatusDelay", "0");
                // This setting is needed for a single node network to run correctly.
                // This is by default set to 0 in platform code, which will not work for single node network.
                System.setProperty("event.creation.maxCreationRate", "20");

                final var factory = ServiceLoader.load(ConfigurationBuilderFactory.class);
                final var configBuilder = factory.findFirst().orElseThrow().create();
                final var config = configBuilder
                        .withConfigDataType(BasicConfig.class)
                        .withConfigDataType(StateConfig.class)
                        .withConfigDataType(CryptoConfig.class)
                        .withConfigDataType(TemporaryFileConfig.class)
                        .withConfigDataType(ReconnectConfig.class)
                        .withConfigDataType(FCHashMapConfig.class)
                        .withConfigDataType(PreconsensusEventStreamConfig.class)
                        .withConfigDataType(EventConfig.class)
                        .withConfigDataType(TransactionConfig.class)
                        .withConfigDataType(EventCreationConfig.class)
                        .withConfigDataType(SocketConfig.class)
                        .withConfigDataType(MerkleDbConfig.class)
                        .withConfigDataType(ChatterConfig.class)
                        .withConfigDataType(AddressBookConfig.class)
                        .withConfigDataType(VirtualMapConfig.class)
                        .withConfigDataType(ThreadConfig.class)
                        .withConfigDataType(DispatchConfiguration.class)
                        .withConfigDataType(MetricsConfig.class)
                        .withConfigDataType(PrometheusConfig.class)
                        .withConfigDataType(OSHealthCheckConfig.class)
                        .withConfigDataType(WiringConfig.class)
                        .withConfigDataType(SyncConfig.class)
                        .withConfigDataType(UptimeConfig.class)
                        .withConfigDataType(PlatformStatusConfig.class)
                        // Configure all services configs
                        .withConfigDataType(ConsensusConfig.class)
                        .withConfigDataType(AccountsConfig.class)
                        .withConfigDataType(TokensConfig.class)
                        .withConfigDataType(StakingConfig.class)
                        // 2. Configure Settings
                        .withSource(new LegacyFileConfigSource(tmpDir.resolve("settings.txt")))
                        .withSource(SystemPropertiesConfigSource.getInstance())
                        .build();

                ConfigurationHolder.getInstance().setConfiguration(config);
                CryptographyHolder.reset();

                final var port = new InetSocketAddress(0).getPort();

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
                addressBook.add(
                        new Address(nodeId, "TEST0", "TEST0", 1, "127.0.0.1", port, "127.0.0.1", port, "0.0.3"));

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

                final var recycleBin = new RecycleBin() {
                    @Override
                    public void recycle(@NonNull Path path) throws IOException {
                        // TODO No-op for now
                    }
                };

                final var initialState = GenesisStateBuilder.buildGenesisState(
                        platformContext, addressBook, new BasicSoftwareVersion(Long.MAX_VALUE), hedera.newState());
                final var initialSignedState =
                        new SignedState(platformContext, initialState.get().getState(), "Genesis");

                final SwirldsPlatform platform = (SwirldsPlatform) constructor.newInstance(
                        platformContext,
                        crypto,
                        recycleBin,
                        nodeId,
                        "Hedera", // main class name
                        "Hedera", // swirld name
                        new BasicSoftwareVersion(Long.MAX_VALUE), // App Version :TODO USE REAL VERSION NUMBER
                        true,
                        initialSignedState,
                        new AddressBook(),
                        new EmergencyRecoveryManager(
                                platformContext.getConfiguration().getConfigData(StateConfig.class),
                                (s, exitCode) -> {
                                    System.out.println("Asked to shutdownGrpcServer because of " + s);
                                    System.exit(exitCode.getExitCode());
                                },
                                tmpDir.resolve("recovery")));

                // 10. Init and Start
                hedera.init(platform, nodeId);
                final var latch = new CountDownLatch(1);
                platform.getNotificationEngine().register(PlatformStatusChangeListener.class, notification -> {
                    if (notification.getNewStatus() == PlatformStatus.ACTIVE) {
                        latch.countDown();
                    }
                });
                platform.start();

                // 11. Initialize the HAPI Spec system
                latch.await(30, TimeUnit.SECONDS);
                hedera.run();

                final var defaultProperties = JutilPropertySource.getDefaultInstance();
                HapiSpec.runInCiMode(
                        String.valueOf(hedera.getGrpcPort()),
                        defaultProperties.get("default.payer"),
                        defaultProperties.get("default.node").split("\\.")[2],
                        defaultProperties.get("tls"),
                        defaultProperties.get("txn.proto.structure"),
                        defaultProperties.get("node.selector"),
                        Map.of(
                                "recordStream.path",
                                tmpDir.resolve("recordStream").toString()));

                return context;
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

        @Override
        public void after(HapiTestEngineExecutionContext context) throws Exception {
            if (hedera != null) {
                hedera.shutdown();
                hedera = null;
            }
        }
    }

    /**
     * Represents a class annotated with {@link HapiTestSuite}. A fresh, new consensus node will be started for each
     * such test class, and terminated after it has been run. Each instance of this class is used both during discovery
     * (thanks to {@link AbstractTestDescriptor}, and during test execution (thanks to the {@link Node} interface).
     */
    private static final class ClassTestDescriptor extends AbstractTestDescriptor
            implements Node<HapiTestEngineExecutionContext> {
        /** The class annotated with {@link HapiTestSuite} */
        private final Class<?> testClass;
        /** We will skip initialization of a {@link Hedera} instance if there are no test methods */
        private final boolean skip;

        /** Creates a new descriptor for the given test class. */
        public ClassTestDescriptor(Class<?> testClass, TestDescriptor parent, EngineDiscoveryRequest discoveryRequest) {
            super(
                    parent.getUniqueId().append("class", testClass.getName()),
                    testClass.getSimpleName(),
                    ClassSource.from(testClass));
            this.testClass = testClass;
            setParent(parent);

            // Look for any methods supported by this class.
            ReflectionUtils.findMethods(testClass, IS_HAPI_TEST, TOP_DOWN).stream()
                    .filter(method -> {
                        // The selectors tell me if some specific method was selected by the IDE or command line,
                        // so I will filter out and only include test methods that were in the selectors, if there
                        // are any such selectors. NOTE: We're not doing class selectors and such, a more robust
                        // implementation probably should.
                        final var selectors = discoveryRequest.getSelectorsByType(MethodSelector.class);
                        for (final var selector : selectors) {
                            if (!selector.getJavaMethod().equals(method)) {
                                return false;
                            }
                        }
                        return true;
                    })
                    .map(method -> new MethodTestDescriptor(method, this))
                    .forEach(this::addChild);

            // Skip construction of the Hedera instance if there are no test methods
            skip = getChildren().isEmpty();
        }

        @Override
        public Type getType() {
            return Type.CONTAINER;
        }

        @Override
        public SkipResult shouldBeSkipped(HapiTestEngineExecutionContext context) {
            return skip ? SkipResult.skip("No test methods") : SkipResult.doNotSkip();
        }
    }

    /**
     * Describes a {@link HapiSpec} test method, and contains logic for running the actual test.
     */
    private static final class MethodTestDescriptor extends AbstractTestDescriptor
            implements Node<HapiTestEngineExecutionContext> {
        /** The method under test */
        private final Method testMethod;

        public MethodTestDescriptor(Method testMethod, ClassTestDescriptor parent) {
            super(
                    parent.getUniqueId().append("method", testMethod.getName()),
                    testMethod.getName(),
                    MethodSource.from(testMethod));
            this.testMethod = testMethod;
            setParent(parent);
        }

        @Override
        public Type getType() {
            return Type.TEST;
        }

        @Override
        public SkipResult shouldBeSkipped(HapiTestEngineExecutionContext context) {
            final var annotation = AnnotationSupport.findAnnotation(testMethod, Disabled.class);
            if (!AnnotationSupport.isAnnotated(testMethod, HapiTest.class)) {
                return SkipResult.skip(testMethod.getName() + " No @HapiTest annotation");
            } else if (annotation.isPresent()) {
                final var msg = annotation.get().value();
                return SkipResult.skip(msg == null || msg.isBlank() ? "Disabled" : msg);
            }
            return SkipResult.doNotSkip();
        }

        @Override
        public HapiTestEngineExecutionContext execute(
                HapiTestEngineExecutionContext context, DynamicTestExecutor dynamicTestExecutor) throws Exception {
            // First, create an instance of the HapiSuite class (the class that owns this method).
            final var parent = (ClassTestDescriptor) getParent().get();
            final var suite =
                    (HapiSuite) parent.testClass.getDeclaredConstructor().newInstance();
            // Second, call the method to get the HapiSpec
            testMethod.setAccessible(true);
            final var spec = (HapiSpec) testMethod.invoke(suite);
            // Third, call `runSuite` with just the one HapiSpec.
            final var result = suite.runSpecSync(spec);
            // Fourth, report the result. YAY!!
            if (result == HapiSuite.FinalOutcome.SUITE_FAILED) {
                throw new AssertionError();
            }
            return context;
        }
    }
}
