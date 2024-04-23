/*
 * Copyright (C) 2023-2024 Hedera Hashgraph, LLC
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

import static com.hedera.services.bdd.junit.HapiTestEnv.HapiTestNodesType.IN_PROCESS_ALICE;
import static com.hedera.services.bdd.junit.HapiTestEnv.HapiTestNodesType.OUT_OF_PROCESS_ALICE;
import static com.hedera.services.bdd.junit.RecordStreamAccess.RECORD_STREAM_ACCESS;
import static org.junit.platform.commons.support.AnnotationSupport.findAnnotation;
import static org.junit.platform.commons.support.AnnotationSupport.isAnnotated;
import static org.junit.platform.commons.support.HierarchyTraversalMode.TOP_DOWN;

import com.hedera.node.app.Hedera;
import com.hedera.node.app.service.mono.statedumpers.accounts.BBMHederaAccount;
import com.hedera.services.bdd.junit.validators.AccountAliasValidator;
import com.hedera.services.bdd.junit.validators.BlockNoValidator;
import com.hedera.services.bdd.spec.HapiSpec;
import com.hedera.services.bdd.spec.props.JutilPropertySource;
import com.hedera.services.bdd.suites.BddMethodIsNotATest;
import com.hedera.services.bdd.suites.HapiSuite;
import com.hedera.services.bdd.suites.TargetNetworkType;
import com.hedera.services.bdd.suites.records.ClosingTime;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.io.File;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URI;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Stream;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Tags;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.platform.commons.support.ReflectionSupport;
import org.junit.platform.engine.EngineDiscoveryRequest;
import org.junit.platform.engine.ExecutionRequest;
import org.junit.platform.engine.Filter;
import org.junit.platform.engine.TestDescriptor;
import org.junit.platform.engine.TestEngine;
import org.junit.platform.engine.TestTag;
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

    public static final int NODE_COUNT = 4;

    static {
        // This is really weird, but it exists because we have to force JUL to use Log4J as early as possible.
        System.setProperty("java.util.logging.manager", "org.apache.logging.log4j.jul.LogManager");
    }

    /**
     * Tests whether a class is annotated with {@link HapiTestSuite}.
     */
    private static final Predicate<Class<?>> IS_HAPI_TEST_SUITE =
            classCandidate -> isAnnotated(classCandidate, HapiTestSuite.class);

    /**
     * Tests whether a method is annotated with {@link HapiTest}, or whether it is a no-arg method that returns a
     * {@link HapiSpec}. Any of the former type of method will be executed, while any of the latter will be skipped.
     */
    private static final Predicate<Method> IS_HAPI_TEST =
            methodCandidate -> isAnnotated(methodCandidate, HapiTest.class)
                    || (!isAnnotated(methodCandidate, BddMethodIsNotATest.class)
                            && methodCandidate.getParameterCount() == 0
                            && methodCandidate.getReturnType() == HapiSpec.class);

    private static final Comparator<ClassTestDescriptor> SUITE_DESCRIPTOR_COMPARATOR =
            Comparator.comparingInt(ClassTestDescriptor::order);
    private static final Comparator<Method> noSorting = (m1, m2) -> 0;
    private static final Comparator<Method> sortMethodsAscByOrderNumber = (m1, m2) -> {
        final var m1Order = m1.getAnnotation(Order.class);
        final var m1OrderValue = m1Order != null ? m1Order.value() : 0;
        final var m2Order = m2.getAnnotation(Order.class);
        final var m2OrderValue = m2Order != null ? m2Order.value() : 0;
        return m1OrderValue - m2OrderValue;
    };

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
        final List<ClassTestDescriptor> suites = new ArrayList<>();

        discoveryRequest.getSelectorsByType(MethodSelector.class).forEach(selector -> {
            final var javaClass = selector.getJavaClass();
            addChildToSuites(javaClass, discoveryRequest, engineDescriptor, suites);
        });

        discoveryRequest.getSelectorsByType(ClassSelector.class).forEach(selector -> {
            final var javaClass = selector.getJavaClass();
            addChildToSuites(javaClass, discoveryRequest, engineDescriptor, suites);
        });

        discoveryRequest.getSelectorsByType(ClasspathRootSelector.class).forEach(selector -> {
            appendSuitesInClasspathRoot(selector.getClasspathRoot(), engineDescriptor, discoveryRequest, suites);
        });

        suites.sort(SUITE_DESCRIPTOR_COMPARATOR);
        suites.forEach(engineDescriptor::addChild);

        // if hapi test suites are being run, add record stream validation to the engineDescriptor as well
        if (!suites.isEmpty()) {
            engineDescriptor.addChild(new RecordStreamValidationTestDescriptor(engineDescriptor));
        }

        return engineDescriptor;
    }

    private static void addChildToSuites(
            @NonNull final Class<?> javaClass,
            @NonNull final EngineDiscoveryRequest discoveryRequest,
            @NonNull final EngineDescriptor engineDescriptor,
            @NonNull final List<ClassTestDescriptor> orderedSuites) {
        if (IS_HAPI_TEST_SUITE.test(javaClass)) {
            final var classDescriptor = new ClassTestDescriptor(javaClass, engineDescriptor, discoveryRequest);
            if (!classDescriptor.skip) {
                orderedSuites.add(classDescriptor);
            }
        }
    }

    private void appendSuitesInClasspathRoot(
            @NonNull final URI uri,
            @NonNull final TestDescriptor engineDescriptor,
            @NonNull final EngineDiscoveryRequest discoveryRequest,
            List<ClassTestDescriptor> orderedSuites) {
        ReflectionSupport.findAllClassesInClasspathRoot(uri, IS_HAPI_TEST_SUITE, name -> true).stream()
                .filter(aClass -> discoveryRequest.getFiltersByType(PackageNameFilter.class).stream()
                        .map(Filter::toPredicate)
                        .allMatch(predicate -> predicate.test(aClass.getPackageName())))
                .map(aClass -> new ClassTestDescriptor(aClass, engineDescriptor, discoveryRequest))
                .filter(classTestDescriptor -> !classTestDescriptor.skip)
                .forEach(orderedSuites::add);
    }

    /**
     * {@inheritDoc}
     *
     * <p>We don't need to do anything here, just return our phony context
     */
    @Override
    protected HapiTestEngineExecutionContext createExecutionContext(ExecutionRequest request) {
        // Populating the data needed for the context
        return new HapiTestEngineExecutionContext();
    }

    private static final class HapiEngineDescriptor extends EngineDescriptor
            implements Node<HapiTestEngineExecutionContext> {

        /** The Hedera test environment to use. We start it once at the start of all tests and reuse it. */
        private HapiTestEnv env;

        public HapiEngineDescriptor(UniqueId uniqueId) {
            super(uniqueId, "Hapi Test");
        }

        @Override
        public HapiTestEngineExecutionContext before(HapiTestEngineExecutionContext context) {
            // If there are no children, then there is nothing to do.
            if (super.getChildren().isEmpty()) {
                return context;
            }

            // Allow for a simple switch to enable in-process Alice node for debugging
            final String debugEnv = System.getenv("HAPI_DEBUG_NODE");
            final boolean debugMode = Boolean.parseBoolean(debugEnv);;
            final var nodesType = debugMode ? IN_PROCESS_ALICE : OUT_OF_PROCESS_ALICE;
            // For now, switching to non-in process servers, because in process doesn't work for the
            // restart and reconnect testing.
            env = new HapiTestEnv("HAPI Tests", NODE_COUNT, nodesType);
            context.setEnv(env);

            final var tmpDir = Path.of("data");
            final var defaultProperties = JutilPropertySource.getDefaultInstance();
            final String recordStreamPath = tmpDir.resolve("recordStream").toString();
            final var parameters =
                    Map.of("recordStream.path", recordStreamPath, "ci.properties.map", "secondsWaitingServerUp=300");
            HapiSpec.runInCiMode(
                    String.valueOf(env.getNodeInfo()),
                    defaultProperties.get("default.payer"),
                    defaultProperties.get("default.node").split("\\.")[2],
                    defaultProperties.get("tls"),
                    defaultProperties.get("txn.proto.structure"),
                    defaultProperties.get("node.selector"),
                    parameters);
            return context;
        }

        @Override
        public void after(HapiTestEngineExecutionContext context) throws Exception {
            if (env != null && env.started()) {
                env.terminate();
                env = null;
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
        /** Whether a separate cluster of nodes should be created for this test class (or reset the normal cluster) */
        private final boolean isolated;

        private final boolean fuzzyMatch;
        private final int order;

        private final Set<TestTag> testTags;

        /** Creates a new descriptor for the given test class. */
        public ClassTestDescriptor(Class<?> testClass, TestDescriptor parent, EngineDiscoveryRequest discoveryRequest) {
            super(
                    parent.getUniqueId().append("class", testClass.getName()),
                    testClass.getSimpleName(),
                    ClassSource.from(testClass));
            this.testClass = testClass;
            this.testTags = getTagsIfAny(testClass);
            setParent(parent);

            // Currently we support only ASC MethodOrderer.OrderAnnotation sorting
            final var sort = testClass.getAnnotation(TestMethodOrder.class) != null
                    && testClass.getAnnotation(TestMethodOrder.class).value() == MethodOrderer.OrderAnnotation.class;

            // Look for any methods supported by this class.
            ReflectionSupport.findMethods(testClass, IS_HAPI_TEST, TOP_DOWN).stream()
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
                    .sorted(sort ? sortMethodsAscByOrderNumber : noSorting)
                    .map(method -> new MethodTestDescriptor(method, this))
                    .forEach(this::addChild);

            // Skip construction of the Hedera instance if there are no test methods
            skip = getChildren().isEmpty();

            // Determine whether this test class (suite) should be isolated in its own cluster of nodes
            final var annotation =
                    findAnnotation(testClass, HapiTestSuite.class).orElseThrow();
            this.isolated = annotation.isolated();
            this.fuzzyMatch = annotation.fuzzyMatch();
            this.order = annotation.order();
        }

        public int order() {
            return order;
        }

        @Override
        public Set<TestTag> getTags() {
            return this.testTags;
        }

        @Override
        public Type getType() {
            return Type.CONTAINER;
        }

        @Override
        public SkipResult shouldBeSkipped(HapiTestEngineExecutionContext context) {
            return skip ? SkipResult.skip("No test methods") : SkipResult.doNotSkip();
        }

        @Override
        public HapiTestEngineExecutionContext execute(
                HapiTestEngineExecutionContext context, DynamicTestExecutor dynamicTestExecutor) throws Exception {

            // If we are isolated AND there is already a started set of nodes, then we have to stop and destroy them
            // and get rid of all state, so when we bring them back up, it is genesis again.
            final var env = context.getEnv();
            if (isolated && (env.started())) {
                env.terminate();
                env.getNodes().forEach(HapiTestNode::clearState);
            }

            // If it hasn't been started, start it. The very first suite will find it isn't started, or any suite that
            // follows one that was isolated will find it wasn't started.
            if (!env.started()) {
                env.start();
            }

            return Node.super.execute(context, dynamicTestExecutor);
        }

        @Override
        public void after(HapiTestEngineExecutionContext context) throws Exception {
            // If we are isolated, then stop and destroy everything to prepare for a new genesis startup.
            final var env = context.getEnv();
            if (env != null && env.started() && isolated) {
                env.terminate();
                env.getNodes().forEach(HapiTestNode::clearState);
            }
        }
    }

    /**
     * Describes a {@link HapiSpec} test method, and contains logic for running the actual test.
     */
    private static final class MethodTestDescriptor extends AbstractTestDescriptor
            implements Node<HapiTestEngineExecutionContext> {
        private final Logger classLogger = LogManager.getLogger(getClass());

        /** The method under test */
        private final Method testMethod;

        private final Set<TestTag> testTags;

        public MethodTestDescriptor(Method testMethod, ClassTestDescriptor parent) {
            super(
                    parent.getUniqueId().append("method", testMethod.getName()),
                    testMethod.getName(),
                    MethodSource.from(testMethod));
            this.testMethod = testMethod;
            this.testTags = getTagsIfAny(testMethod);
            this.testTags.addAll(parent.getTags());
            setParent(parent);
        }

        @Override
        public Type getType() {
            return Type.TEST;
        }

        @Override
        public SkipResult shouldBeSkipped(HapiTestEngineExecutionContext context) {
            final var isHapiTestAnnotated = isAnnotated(testMethod, HapiTest.class);
            final var disabledAnnotation = findAnnotation(testMethod, Disabled.class);

            if (!isHapiTestAnnotated) {
                return SkipResult.skip(testMethod.getName() + " No @HapiTest annotation");
            } else if (disabledAnnotation.isPresent()) {
                final var msg = disabledAnnotation.get().value();
                return SkipResult.skip(msg == null || msg.isBlank() ? "Disabled" : msg);
            } else if (testMethod.getParameterCount() != 0) {
                final String message =
                        "%s requires %d parameters.".formatted(testMethod.getName(), testMethod.getParameterCount());
                return SkipResult.skip(message);
            }
            return SkipResult.doNotSkip();
        }

        @Override
        public HapiTestEngineExecutionContext execute(
                HapiTestEngineExecutionContext context, DynamicTestExecutor dynamicTestExecutor)
                throws NoSuchMethodException, InvocationTargetException, InstantiationException,
                        IllegalAccessException {
            // First, create an instance of the HapiSuite class (the class that owns this method).
            final var parent = (ClassTestDescriptor) getParent().get();
            final var suite =
                    (HapiSuite) parent.testClass.getDeclaredConstructor().newInstance();
            // Second, call the method to get the HapiSpec
            testMethod.setAccessible(true);
            if (testMethod.getParameterCount() == 0) {
                final var spec = (HapiSpec) testMethod.invoke(suite);
                spec.setTargetNetworkType(TargetNetworkType.HAPI_TEST_NETWORK);
                // Disabling fuzzy matching in CI until we can make them stable
                if (parent.fuzzyMatch && System.getenv("CI") == null) {
                    spec.addOverrideProperties(Map.of("recordStream.autoSnapshotManagement", "true"));
                }
                final var env = context.getEnv();
                // Third, call `runSuite` with just the one HapiSpec.
                final var result = suite.runSpecSync(spec, env.getNodes());
                // Fourth, report the result. YAY!!
                if (result == HapiSuite.FinalOutcome.SUITE_FAILED) {
                    throw new AssertionError();
                }
            } else {
                final String message = "Not running spec {}.  Method requires {} parameters.";
                classLogger.log(Level.INFO, message, testMethod.getName(), testMethod.getParameterCount());
            }
            return context;
        }

        @Override
        public Set<TestTag> getTags() {
            return this.testTags;
        }
    }

    private static final class RecordStreamValidationTestDescriptor extends AbstractTestDescriptor
            implements Node<HapiTestEngineExecutionContext> {

        private final Logger classLogger = LogManager.getLogger(getClass());

        private static final long MIN_GZIP_SIZE_IN_BYTES = 26;

        private static final String HAPI_TEST_STREAMS_LOC_TPL = //"hedera-node/data/recordStreams/record0.0.%d";
         "hedera-node/test-clients/build/hapi-test/node%d/data/recordStreams/record0.0.%d";

        private static final List<RecordStreamValidator> validators = List.of(
                new BlockNoValidator(),
                new TransactionBodyValidator(),
                new ExpiryRecordsValidator(),
                new BalanceReconciliationValidator(),
                new TokenReconciliationValidator(),
                new AccountAliasValidator());

        public RecordStreamValidationTestDescriptor(TestDescriptor parent) {
            super(parent.getUniqueId().append("validation", "recordStream"), "recordStreamValidation");
            setParent(parent);
        }

        @Override
        public Set<TestTag> getTags() {
            return Set.of(TestTag.create("RECORD_STREAM_VALIDATION"));
        }

        @Override
        public Type getType() {
            return Type.TEST;
        }

        @Override
        public HapiTestEngineExecutionContext execute(
                HapiTestEngineExecutionContext context, DynamicTestExecutor dynamicTestExecutor) throws Exception {
            final var env = context.getEnv();
            // run closing time specs
            runSpec(env, new ClosingTime(), "closeLastStreamFileWithNoBalanceImpact");

            // read record stream data
            var recordLocs = hapiTestStreamLocs();
            RecordStreamAccess.Data data = RecordStreamAccess.Data.EMPTY_DATA;
            for (final var recordLoc : recordLocs) {
                try {
                    classLogger.info("Trying to read record files from {}", recordLoc);
                    data = RECORD_STREAM_ACCESS.readStreamDataFrom(
                            recordLoc, "sidecar", f -> new File(f).length() > MIN_GZIP_SIZE_IN_BYTES);
                    classLogger.info(
                            "Read {} record files from {}", data.records().size(), recordLoc);
                } catch (Exception ignore) {
                    // We will try the next location, if any
                }
                if (!data.records().isEmpty()) {
                    break;
                }
            }

            Set<BBMHederaAccount> accountsFromState = StateAccess.readAccountsFromNodesState(HapiTestEngine.NODE_COUNT);

            // assert validators pass
            final var streamData = data;
            final var errorsIfAny = validators.stream()
                    .flatMap(v -> {
                        try {
                            // The validator will complete silently if no errors are
                            // found
                            v.validateFiles(streamData.files());
                            v.validateRecordsAndSidecarsHapi(env, streamData.records());
                            v.validateAccountAliases(accountsFromState, streamData.records());
                            return Stream.empty();
                        } catch (final Throwable t) {
                            return Stream.of(t);
                        }
                    })
                    .map(Throwable::getMessage)
                    .toList();
            if (!errorsIfAny.isEmpty()) {
                throw new AssertionError("Record stream validation failed with the following errors:\n  - "
                        + String.join("\n  - ", errorsIfAny));
            }

            return Node.super.execute(context, dynamicTestExecutor);
        }

        private List<String> hapiTestStreamLocs() {
            final List<String> locs = new ArrayList<>(HapiTestEngine.NODE_COUNT);
            for (int i = 0; i < HapiTestEngine.NODE_COUNT; i++) {
                locs.add(String.format(HAPI_TEST_STREAMS_LOC_TPL, i, i + 3));
            }
            return locs;
        }
    }

    public static void runSpec(HapiTestEnv env, HapiSuite suite, String specName)
            throws InvocationTargetException, IllegalAccessException {
        // Get the method
        final var testMethod =
                ReflectionSupport.findMethod(suite.getClass(), specName).get();
        // Call the method to get the HapiSpec
        testMethod.setAccessible(true);
        final var spec = (HapiSpec) testMethod.invoke(suite);
        spec.setTargetNetworkType(TargetNetworkType.HAPI_TEST_NETWORK);
        final var result = suite.runSpecSync(spec, env.getNodes());
        // Report the result. YAY!!
        if (result == HapiSuite.FinalOutcome.SUITE_FAILED) {
            throw new AssertionError(spec.getName() + ": " + spec.getCause());
        }
    }

    private static Set<TestTag> getTagsIfAny(Class<?> testClass) {
        // When a class has a single @Tag annotation, we retrieve it by filtering for Tag.class.
        // In cases where a class has multiple @Tag annotations, we use Tags.class to access all of them.
        // Ideally, Tags.class should encompass both single and multiple @Tag annotations,
        // but the current implementation does not support this.
        final var tagsAnnotation = testClass.getAnnotation(Tags.class);
        final var tagAnnotation = testClass.getAnnotation(Tag.class);

        return extractTags(tagsAnnotation, tagAnnotation);
    }

    private static Set<TestTag> getTagsIfAny(Method testMethod) {
        // When a method has a single @Tag annotation, we retrieve it by filtering for Tag.class.
        // In cases where a method has multiple @Tag annotations, we use Tags.class to access all of them.
        // Ideally, Tags.class should encompass both single and multiple @Tag annotations,
        // but the current implementation does not support this.
        final var tagsAnnotation = testMethod.getAnnotation(Tags.class);
        final var tagAnnotation = testMethod.getAnnotation(Tag.class);

        return extractTags(tagsAnnotation, tagAnnotation);
    }

    // A helper method that extracts the value from either a @Tags annotation or a @Tag annotation
    private static Set<TestTag> extractTags(Tags tagsAnnotation, Tag tagAnnotation) {
        final var tags = new HashSet<TestTag>();
        if (tagsAnnotation != null) {
            tags.addAll(Arrays.stream(tagsAnnotation.value())
                    .map(t -> TestTag.create(t.value()))
                    .toList());
        } else if (tagAnnotation != null) {
            tags.add(TestTag.create(tagAnnotation.value()));
        }
        return tags;
    }
}
