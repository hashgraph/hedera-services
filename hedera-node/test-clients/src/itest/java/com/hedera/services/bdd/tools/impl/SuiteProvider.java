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

package com.hedera.services.bdd.tools.impl;

import com.hedera.services.bdd.suites.HapiSuite;
import com.hedera.services.bdd.suites.SuiteRunner;
import com.hedera.services.bdd.suites.leaky.FeatureFlagSuite;
import com.hedera.services.bdd.suites.regression.TargetNetworkPrep;
import com.hedera.services.bdd.tools.SuiteKind;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.lang.reflect.InaccessibleObjectException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiFunction;
import java.util.function.Supplier;
import org.jetbrains.annotations.NotNull;

/**
 * Provides instances of the test suite factories to the caller.
 *
 * Problem is that for some reason the test suite factory classes are in the Java "default"
 * package.  So you can't import them.  I don't know enough to change that by moving them
 * into a proper package:  There are things about how the build works and how CI invokes
 * these test suites I don't know.  So instead I'm using a nasty bit of reflection to
 * fetch the methods I need.  TODO: Replace this once the test suite factory classes get
 * moved to a proper Java package
 */
public class SuiteProvider implements AvailableIntegrationTestSuites {

    @NotNull
    @Override
    public List<Supplier<HapiSuite>> allSuitesOfKind(@NonNull final SuiteKind... kinds) {
        final var wanted = EnumSet.of(SuiteKind.prerequisite);
        wanted.addAll(Arrays.asList(kinds));
        if (wanted.contains(SuiteKind.all)) {
            wanted.addAll(EnumSet.allOf(SuiteKind.class));
            wanted.remove(SuiteKind.all);
        }

        final var r = new ArrayList<Supplier<HapiSuite>>(2500);
        for (final var kind : wanted) {
            r.addAll(
                    switch (kind) {
                        case all -> List.of();
                        case prerequisite -> allPrerequisiteSuites();
                        case sequential -> allSequentialSuites();
                        case concurrent -> allConcurrentSuites();
                        case concurrentetherium -> allConcurrentEthereumSuites();
                        case e2e -> allE2eSuites();
                        case e2eunused -> allE2eUnusedSuites();
                        case e2epackagerunner -> allE2ePackageRunnerSuites();
                        case suiterunner -> allSuiteRunnerSuites();
                    });
        }
        return r;
    }

    @NotNull
    public List<Supplier<HapiSuite>> allPrerequisiteSuites() {
        return List.of(TargetNetworkPrep::new, FeatureFlagSuite::new);
    }

    @NotNull
    public List<Supplier<HapiSuite>> allSequentialSuites() {
        return Arrays.asList(getSuites("SequentialSuites", "all"));
    }

    @NotNull
    public List<Supplier<HapiSuite>> allConcurrentSuites() {
        return Arrays.asList(getSuites("ConcurrentSuites", "all"));
    }

    @NotNull
    public List<Supplier<HapiSuite>> allConcurrentEthereumSuites() {
        return Arrays.asList(getSuites("ConcurrentSuites", "ethereumSuites"));
    }

    @NonNull
    public List<Supplier<HapiSuite>> allE2eSuites() {
        return List.of();
    }

    @NonNull
    public List<Supplier<HapiSuite>> allE2eUnusedSuites() {
        return List.of();
    }

    @NonNull
    public List<Supplier<HapiSuite>> allE2ePackageRunnerSuites() {
        return List.of();
    }

    @NonNull
    public List<Supplier<HapiSuite>> allSuiteRunnerSuites() {
        final var suiteRunnerSpy = new SuiteRunner() {
            @NonNull
            public Map<String, Supplier<HapiSuite[]>> getCategoryMap() {
                return CATEGORY_MAP;
            }
        };
        final var categoryMap = suiteRunnerSpy.getCategoryMap();

        return categoryMap.values().stream()
                .map(Supplier::get)
                .map(Arrays::asList)
                .flatMap(List::stream)
                .<Supplier<HapiSuite>>map(suite -> () -> suite)
                .toList();
    }

    /**
     * Invoke a _static_ method by class name+method name.
     */
    @NonNull
    Supplier<HapiSuite>[] getSuites(@NonNull final String klassName, @NonNull final String methodName) {

        BiFunction<String, Exception, RuntimeException> thrower =
                (message, ex) -> new RuntimeException(message.formatted(klassName, methodName), ex);

        // (There really should be a `try-catch-expression` like a `switch-expression`.)

        Class<?> specSupplierKlass;
        try {
            specSupplierKlass = Objects.requireNonNull(Class.forName(klassName), "Class.forName(klassName)");
        } catch (ClassNotFoundException e) {
            throw thrower.apply("Exception getting class '%s'", e);
        }

        Method[] specSupplierMethods;
        try {
            specSupplierMethods = specSupplierKlass.getDeclaredMethods();
        } catch (SecurityException e) {
            throw thrower.apply("Exception getting all methods of '%s'", e);
        }

        Method specSupplierMethod = Arrays.stream(Objects.requireNonNull(specSupplierMethods))
                .filter(method -> methodName.equals(method.getName()))
                .findFirst()
                .orElseThrow(() -> thrower.apply(
                        "Exception getting method '%s.%s'",
                        new NoSuchMethodException("%s not found in declared methods".formatted(methodName))));

        try {
            specSupplierMethod.setAccessible(true);
        } catch (InaccessibleObjectException | SecurityException ex) {
            throw thrower.apply("Exception setting accessibility of method '%s.%s", ex);
        }

        Object result;
        try {
            result = Objects.requireNonNull(
                    specSupplierMethod.invoke(null /* method is static */), "specSupplierMethod.invoke()");
        } catch (IllegalAccessException | InvocationTargetException e) {
            throw thrower.apply("Exception invoking method '%s.%s'", e);
        }

        try {
            return asArrayOfHapiSuiteSuppliers(result);
        } catch (ClassCastException e) {
            throw thrower.apply("Exception casting result of invoking '%s.'%s", e);
        }
    }

    @SuppressWarnings("unchecked")
    @NonNull
    Supplier<HapiSuite>[] asArrayOfHapiSuiteSuppliers(@NonNull final Object o) {
        return (Supplier<HapiSuite>[]) o;
    }
}
