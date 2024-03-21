/*
 * Copyright (C) 2022-2024 Hedera Hashgraph, LLC
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

import static com.hedera.services.bdd.junit.RecordStreamAccess.RECORD_STREAM_ACCESS;
import static com.hedera.services.bdd.suites.HapiSuite.ETH_SUFFIX;
import static com.hedera.services.bdd.suites.SuiteRunner.SUITE_NAME_WIDTH;
import static com.hedera.services.bdd.suites.SuiteRunner.rightPadded;
import static com.hedera.services.bdd.suites.TargetNetworkType.CI_DOCKER_NETWORK;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.DynamicContainer.dynamicContainer;
import static org.junit.jupiter.api.DynamicTest.dynamicTest;

import com.hedera.services.bdd.junit.validators.HgcaaLogValidator;
import com.hedera.services.bdd.junit.validators.QueryLogValidator;
import com.hedera.services.bdd.spec.HapiSpec;
import com.hedera.services.bdd.suites.HapiSuite;
import com.hedera.services.bdd.suites.records.ClosingTime;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DynamicContainer;
import org.junit.jupiter.api.DynamicTest;

/**
 * Base class with some utility methods that can be used by JUnit-based test classes.
 */
public abstract class TestBase {
    /**
     * This factory takes a list of suite suppliers and returns a dynamic test that runs all specs
     * from the suites concurrently. If any spec fails, the test fails. The assertion message will
     * list all the failed specs, organized by suite, with details about the cause of each failure,
     * including the exception detail message and the {@code toString()} of the failed operation.
     *
     * @param suiteSuppliers the suites to run concurrently
     * @return a dynamic test that runs all specs from the suites concurrently
     */
    @SafeVarargs
    @SuppressWarnings("varargs")
    protected final DynamicTest concurrentSpecsFrom(final Supplier<HapiSuite>... suiteSuppliers) {
        return internalSpecsFrom("", Arrays.asList(suiteSuppliers), TestBase::contextualizedSpecsFromConcurrent);
    }

    @SafeVarargs
    @SuppressWarnings("varargs")
    protected final DynamicTest concurrentEthSpecsFrom(final Supplier<HapiSuite>... suiteSuppliers) {
        return internalSpecsFrom(ETH_SUFFIX, Arrays.asList(suiteSuppliers), this::contextualizedEthSpecsFromConcurrent);
    }

    @SuppressWarnings("java:S3864")
    protected final DynamicTest internalSpecsFrom(
            final String suffix,
            final List<Supplier<HapiSuite>> suiteSuppliers,
            final Function<HapiSuite, Stream<HapiSpec>> internalSpecsExtractor) {
        final var commaSeparatedSuites = new StringBuilder();
        final var contextualizedSpecs =
                extractContextualizedSpecsFrom(suiteSuppliers, internalSpecsExtractor, suiteName -> commaSeparatedSuites
                        .append(commaSeparatedSuites.isEmpty() ? "" : ", ")
                        .append(suiteName)
                        .append(suffix));
        return dynamicTest(commaSeparatedSuites.toString(), () -> concurrentExecutionOf(contextualizedSpecs));
    }

    public static List<HapiSpec> extractContextualizedSpecsFrom(
            final List<Supplier<HapiSuite>> suiteSuppliers,
            final Function<HapiSuite, Stream<HapiSpec>> internalSpecsExtractor) {
        return extractContextualizedSpecsFrom(suiteSuppliers, internalSpecsExtractor, s -> {});
    }

    public static List<HapiSpec> extractContextualizedSpecsFrom(
            final List<Supplier<HapiSuite>> suiteSuppliers,
            final Function<HapiSuite, Stream<HapiSpec>> internalSpecsExtractor,
            final Consumer<String> nameConsumer) {
        return suiteSuppliers.stream()
                .map(Supplier::get)
                .peek(suite -> nameConsumer.accept(suite.name()))
                .flatMap(internalSpecsExtractor)
                .toList();
    }

    protected final DynamicTest hgcaaLogValidation(final String loc) {
        return dynamicTest("hgcaaLogValidation", () -> new HgcaaLogValidator(loc).validate());
    }

    protected final DynamicTest queriesLogValidation(final String loc) {
        return dynamicTest("queriesLogValidation", () -> new QueryLogValidator(loc).validate());
    }

    @SuppressWarnings("java:S1181")
    protected final DynamicTest recordStreamValidation(final String loc, final RecordStreamValidator... validators) {
        return dynamicTest("recordStreamValidation", () -> {
            final var closingTimeSpecs = TestBase.extractContextualizedSpecsFrom(
                    List.of(ClosingTime::new), TestBase::contextualizedSpecsFromConcurrent);
            concurrentExecutionOf(closingTimeSpecs);
            assertValidatorsPass(loc, Arrays.asList(validators));
        });
    }

    @SuppressWarnings("java:S1181")
    public static void assertValidatorsPass(final String loc, final List<RecordStreamValidator> validators)
            throws IOException {
        final var streamData = RECORD_STREAM_ACCESS.readStreamDataFrom(loc, "sidecar");
        final var errorsIfAny = validators.stream()
                .flatMap(v -> {
                    try {
                        // The validator will complete silently if no errors are
                        // found
                        v.validateFiles(streamData.files());
                        v.validateRecordsAndSidecars(streamData.records());
                        return Stream.empty();
                    } catch (final Throwable t) {
                        return Stream.of(t);
                    }
                })
                .map(Throwable::getMessage)
                .toList();
        if (!errorsIfAny.isEmpty()) {
            Assertions.fail("Record stream validation failed with the following errors:\n  - "
                    + String.join("\n  - ", errorsIfAny));
        }
    }

    public static void concurrentExecutionOf(final List<HapiSpec> specs) {
        HapiSuite.runConcurrentSpecs(specs);
        final var failures = specs.stream().filter(HapiSpec::notOk).toList();
        if (!failures.isEmpty()) {
            final var failureReport =
                    """
                            %d specs FAILED. By suite,
                            %s
                            """;
            final var details = new StringBuilder();
            // The stream below is a bit tricky. It is grouping the specs by suite name, and then
            // for each suite, it is collecting the specs that failed, and then for each failed
            // spec,
            // it is collecting the details of the failure. The details are the exception message
            // and
            // the toString() of the failed operation.
            failures.stream()
                    .collect(Collectors.groupingBy(HapiSpec::getSuitePrefix, Collectors.toList()))
                    .forEach((suiteName, failedSpecs) -> {
                        details.append("  ")
                                .append(rightPadded(suiteName, SUITE_NAME_WIDTH))
                                .append(" -> ")
                                .append(failedSpecs.size())
                                .append(" failures:\n");
                        failedSpecs.forEach(failure -> details.append("    ")
                                .append(failure.getName())
                                .append(": ")
                                .append(failure.getCause())
                                .append("\n"));
                    });
            Assertions.fail(String.format(failureReport, failures.size(), details));
        }
    }

    public static Stream<HapiSpec> contextualizedSpecsFromConcurrent(final HapiSuite suite) {
        return suffixContextualizedSpecsFromConcurrent(suite, "");
    }

    private Stream<HapiSpec> contextualizedEthSpecsFromConcurrent(final HapiSuite suite) {
        return suffixContextualizedSpecsFromConcurrent(suite, ETH_SUFFIX);
    }

    private static Stream<HapiSpec> suffixContextualizedSpecsFromConcurrent(
            final HapiSuite suite, final String suffix) {
        if (!suite.canRunConcurrent()) {
            throw new IllegalArgumentException(suite.name() + " specs cannot run concurrently");
        }
        // Re-use gRPC channels for all specs
        suite.skipClientTearDown();
        // Don't log unnecessary detail
        suite.setOnlyLogHeader();
        return suite.getSpecsInSuiteWithOverrides().stream()
                .map(spec -> spec.setSuitePrefix(suite.name() + suffix).setTargetNetworkType(CI_DOCKER_NETWORK));
    }

    /**
     * Utility that creates a DynamicTest for each HapiApiSpec in the given suite.
     *
     * @param suiteSupplier
     * @return
     */
    protected final DynamicContainer extractSpecsFromSuite(final Supplier<HapiSuite> suiteSupplier) {
        return extractSpecsFromSuite(suiteSupplier, ".*");
    }

    /**
     * Utility that creates a DynamicTest for each HapiApiSpec in the given suite.
     *
     * @param suiteSupplier
     * @return
     */
    protected final DynamicContainer extractSpecsFromSuite(
            final Supplier<HapiSuite> suiteSupplier, final String filter) {
        final var suite = suiteSupplier.get();
        final var tests = suite.getSpecsInSuiteWithOverrides().stream()
                .map(s -> s.setTargetNetworkType(CI_DOCKER_NETWORK))
                .map(s -> dynamicTest(s.getName(), () -> {
                    s.run();
                    assertEquals(
                            s.getExpectedFinalStatus(),
                            s.getStatus(),
                            "Failure in SUITE {"
                                    + suite.getClass().getSimpleName()
                                    + "}, while "
                                    + "executing "
                                    + "SPEC {"
                                    + s.getName()
                                    + "}: "
                                    + s.getCause());
                }))
                .filter(t -> t.getDisplayName().matches(filter));
        return dynamicContainer(suite.getClass().getSimpleName(), tests);
    }

    protected final DynamicContainer extractSpecsFromSuiteForEth(final Supplier<HapiSuite> suiteSupplier) {
        final var suite = suiteSupplier.get();
        final var tests = suite.getSpecsInSuiteWithOverrides().stream()
                .map(s -> s.setTargetNetworkType(CI_DOCKER_NETWORK))
                .map(s -> dynamicTest(s.getName() + ETH_SUFFIX, () -> {
                    s.setSuitePrefix(suite.getClass().getSimpleName() + ETH_SUFFIX);
                    s.run();
                    assertEquals(
                            s.getExpectedFinalStatus(),
                            s.getStatus(),
                            "\n\t\t\tFailure in SUITE {"
                                    + suite.getClass().getSimpleName()
                                    + ETH_SUFFIX
                                    + "}, "
                                    + "while "
                                    + "executing "
                                    + "SPEC {"
                                    + s.getName()
                                    + ETH_SUFFIX
                                    + "}: "
                                    + s.getCause());
                }));
        return dynamicContainer(suite.getClass().getSimpleName(), tests);
    }
}
