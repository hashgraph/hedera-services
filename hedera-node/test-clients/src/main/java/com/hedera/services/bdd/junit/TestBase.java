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

import static com.hedera.services.bdd.suites.SuiteRunner.SUITE_NAME_WIDTH;
import static com.hedera.services.bdd.suites.SuiteRunner.rightPadded;

import com.hedera.services.bdd.spec.HapiSpec;
import com.hedera.services.bdd.suites.HapiSuite;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DynamicTest;

/**
 * Base class with some utility methods that can be used by JUnit-based test classes.
 */
public abstract class TestBase {
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

    private static Stream<HapiSpec> suffixContextualizedSpecsFromConcurrent(
            final HapiSuite suite, final String suffix) {
        if (!suite.canRunConcurrent()) {
            throw new IllegalArgumentException(suite.name() + " specs cannot run concurrently");
        }
        // Re-use gRPC channels for all specs
        suite.skipClientTearDown();
        // Don't log unnecessary detail
        suite.setOnlyLogHeader();
        return suite.getSpecsInSuiteWithOverrides().stream().map(spec -> spec.setSuitePrefix(suite.name() + suffix));
    }

    /**
     * Loops all the suppliers and their methods. If the method is annotated with the annotationClass annotation
     * we invoke the method get the Stream<DynamicTest> and collect it to a list.
     * @param suppliers Test class suppliers that contain tests methods returning Stream<DynamicTest>
     * @param ignoredTests The test methods that are ignored
     * @param annotationClass The target annotation
     * @return list of all Stream<DynamicTest> collected from all found methods
     */
    public static List<Stream<DynamicTest>> extractAllTestAnnotatedMethods(
            @NonNull Supplier<?>[] suppliers,
            @NonNull List<String> ignoredTests,
            @NonNull Class<? extends Annotation> annotationClass) {
        var allDynamicTests = new ArrayList<Stream<DynamicTest>>();
        for (Supplier<?> supplier : suppliers) {
            Object instance = supplier.get();
            for (Method method : instance.getClass().getDeclaredMethods()) {
                if (method.isAnnotationPresent(annotationClass)) {
                    if (ignoredTests.contains(method.getName())) {
                        continue;
                    }
                    method.setAccessible(true);
                    Stream<DynamicTest> testInvokeResult = null;
                    try {
                        testInvokeResult = (Stream<DynamicTest>) method.invoke(instance);
                    } catch (Exception e) {
                        throw new RuntimeException(e); // no handle for now
                    }
                    var dynamicTest = DynamicTest.dynamicTest(
                            method.getName(), testInvokeResult.toList().get(0).getExecutable());
                    allDynamicTests.add(Stream.of(dynamicTest));
                }
            }
        }
        return allDynamicTests;
    }
}
