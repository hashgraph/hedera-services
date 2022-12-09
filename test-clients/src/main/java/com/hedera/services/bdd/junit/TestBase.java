/*
 * Copyright (C) 2022 Hedera Hashgraph, LLC
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

import static com.hedera.services.bdd.suites.HapiSuite.ETH_SUFFIX;
import static com.hedera.services.bdd.suites.SuiteRunner.SUITE_NAME_WIDTH;
import static com.hedera.services.bdd.suites.SuiteRunner.rightPadded;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.DynamicContainer.dynamicContainer;
import static org.junit.jupiter.api.DynamicTest.dynamicTest;

import com.hedera.services.bdd.spec.HapiSpec;
import com.hedera.services.bdd.suites.HapiSuite;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DynamicContainer;
import org.junit.jupiter.api.DynamicTest;

/** Base class with some utility methods that can be used by JUnit-based test classes. */
public abstract class TestBase {
    @SafeVarargs
    @SuppressWarnings("java:S3864")
    protected final DynamicTest specsFrom(final Supplier<HapiSuite>... suiteSuppliers) {
        final var commaSeparatedSuites = new StringBuilder();
        final var contextualizedSpecs =
                Arrays.stream(suiteSuppliers)
                        .map(Supplier::get)
                        .peek(
                                suite ->
                                        commaSeparatedSuites
                                                .append(commaSeparatedSuites.isEmpty() ? "" : ", ")
                                                .append(suite.name()))
                        .flatMap(this::contextualizedSpecsFromConcurrent)
                        .toList();
        return dynamicTest(
                commaSeparatedSuites.toString(), () -> concurrentExecutionOf(contextualizedSpecs));
    }

    private void concurrentExecutionOf(final List<HapiSpec> specs) {
        HapiSuite.runConcurrentSpecs(specs);
        final var failures = specs.stream().filter(HapiSpec::notOk).toList();
        if (!failures.isEmpty()) {
            final var failureReport =
                    """
                    %d specs FAILED. By suite,
                    %s
                    """;
            final var details = new StringBuilder();
            failures.stream()
                    .collect(Collectors.groupingBy(HapiSpec::getSuitePrefix, Collectors.toList()))
                    .forEach(
                            (suiteName, failedSpecs) -> {
                                details.append("  ")
                                        .append(rightPadded(suiteName, SUITE_NAME_WIDTH))
                                        .append(" -> ")
                                        .append(failedSpecs.size())
                                        .append(" failures:");
                                failedSpecs.forEach(
                                        failure ->
                                                details.append("\n  ")
                                                        .append(failure.getName())
                                                        .append("\n")
                                                        .append(
                                                                Objects.requireNonNull(
                                                                                failure.getCause())
                                                                        .getMessage())
                                                        .append("\n"));
                            });
            Assertions.fail(String.format(failureReport, failures.size(), details));
        }
    }

    private Stream<HapiSpec> contextualizedSpecsFromConcurrent(final HapiSuite suite) {
        if (!suite.canRunConcurrent()) {
            throw new IllegalArgumentException(suite.name() + " specs cannot run concurrently");
        }
        // Re-use gRPC channels for all specs
        suite.skipClientTearDown();
        // Don't log unnecessary detail
        suite.setOnlyLogHeader();
        return suite.getSpecsInSuite().stream().map(spec -> spec.setSuitePrefix(suite.name()));
    }

    /**
     * Utility that creates a DynamicTest for each HapiApiSpec in the given suite.
     *
     * @param suiteSupplier
     * @return
     */
    protected final DynamicContainer extractSpecsFromSuite(
            final Supplier<HapiSuite> suiteSupplier) {
        final var suite = suiteSupplier.get();
        final var tests =
                suite.getSpecsInSuite().stream()
                        .map(
                                s ->
                                        dynamicTest(
                                                s.getName(),
                                                () -> {
                                                    s.run();
                                                    assertEquals(
                                                            s.getExpectedFinalStatus(),
                                                            s.getStatus(),
                                                            "Failure in SUITE {"
                                                                    + suite.getClass()
                                                                            .getSimpleName()
                                                                    + "}, while "
                                                                    + "executing "
                                                                    + "SPEC {"
                                                                    + s.getName()
                                                                    + "}");
                                                }));
        return dynamicContainer(suite.getClass().getSimpleName(), tests);
    }

    protected final DynamicContainer extractSpecsFromSuiteForEth(
            final Supplier<HapiSuite> suiteSupplier) {
        final var suite = suiteSupplier.get();
        final var tests =
                suite.getSpecsInSuite().stream()
                        .map(
                                s ->
                                        dynamicTest(
                                                s.getName() + ETH_SUFFIX,
                                                () -> {
                                                    s.setSuitePrefix(
                                                            suite.getClass().getSimpleName()
                                                                    + ETH_SUFFIX);
                                                    s.run();
                                                    assertEquals(
                                                            s.getExpectedFinalStatus(),
                                                            s.getStatus(),
                                                            "\n\t\t\tFailure in SUITE {"
                                                                    + suite.getClass()
                                                                            .getSimpleName()
                                                                    + ETH_SUFFIX
                                                                    + "}, "
                                                                    + "while "
                                                                    + "executing "
                                                                    + "SPEC {"
                                                                    + s.getName()
                                                                    + ETH_SUFFIX
                                                                    + "}");
                                                }));
        return dynamicContainer(suite.getClass().getSimpleName(), tests);
    }
}
