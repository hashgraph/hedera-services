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

import static com.hedera.services.bdd.tools.impl.SuiteRepoParameters.hapiAbstractSuiteBases;
import static com.hedera.services.bdd.tools.impl.SuiteRepoParameters.hapiSuiteRootPackageName;
import static java.util.Map.Entry.comparingByKey;

import com.google.common.collect.Multimap;
import com.google.common.collect.MultimapBuilder;
import com.google.common.collect.Sets;
import com.hedera.services.bdd.spec.HapiSpec;
import com.hedera.services.bdd.suites.HapiSuite;
import com.hedera.services.bdd.suites.tools.annotation.BddMethodIsNotATest;
import com.hedera.services.bdd.tools.SuiteKind;
import com.hedera.services.bdd.tools.SuitesInspector;
import com.hedera.services.bdd.tools.SuitesInspector.IgnoresFile;
import com.hedera.services.bdd.tools.SuitesInspector.ManifestFile;
import com.hedera.services.bdd.tools.impl.CallStack.Towards;
import com.hedera.services.bdd.tools.impl.CallStack.WithLineNumbers;
import com.swirlds.common.AutoCloseableNonThrowing;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Collection;
import java.util.HashSet;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.apache.commons.lang3.tuple.Pair;

/** Register each `HapiSuite` and `HapiSpec` as it is created, by callouts from the constructors of those
 * two classes.
 *
 * Note that the _same_ `HapiSuite` class (and thus its `HapiSpec` tests too) can be instantiated more than once.
 * This can happen for the "prerequisite" suites (`FeatureFlagSuite` and `TargetNetworkPrep` when run from the
 * `SuiteInspector`) and also for some of the suites in `ConcurrentSuites.ethereumSuites()`.  The former should
 * use only _one_ of the two (identical) instances, the latter should use both (as they are run with different
 * conditions).
 */
public class HapiTestRegistrar {

    final ReflectionUtils reflection = new ReflectionUtils();

    boolean doVerbose;

    public void doVerbose(final boolean doVerbose) {
        this.doVerbose = doVerbose;
    }

    SuiteKind currentSuiteKind;

    public AutoCloseableNonThrowing withSuiteKind(@NonNull final SuiteKind suiteKind) {
        currentSuiteKind = suiteKind;
        return () -> currentSuiteKind = null;
    }

    public record RegisteredSuite(
            @NonNull HapiSuite suite, @NonNull Class<?> klass, @NonNull SuiteKind kind, @Nullable String other) {

        @NonNull
        public String name() {
            return klass.getName();
        }
    }

    public record RegisteredSpec(
            @NonNull HapiSpec spec, @NonNull Method method, @NonNull SuiteKind kind, @Nullable String other) {

        @NonNull
        public Class<?> klass() {
            return method.getDeclaringClass();
        }

        @NonNull
        public String name() {
            return method.getName();
        }

        @NonNull
        public String nameWithoutSuffix() {
            return stripSuffixes(name());
        }

        static Collection<String> specSuffixes = Set.of("_Eth", "WithLongTermEnabled");

        @NonNull
        static String stripSuffixes(@NonNull String s) {
            final var sb = new StringBuilder(s);

            Change changed = Change.HAS;
            while (changed == Change.HAS) {
                changed = Change.HAS_NOT;
                for (final var suffix : specSuffixes) {
                    changed = stripSuffix(sb, suffix);
                    if (changed == Change.HAS) break;
                }
            }
            return sb.toString();
        }

        enum Change {
            HAS,
            HAS_NOT
        }

        @NonNull
        static Change stripSuffix(@NonNull final StringBuilder sb, @NonNull final String suffix) {
            final int i = sb.lastIndexOf(suffix);
            if (i < 0 || i + suffix.length() < sb.length()) return Change.HAS_NOT;
            sb.setLength(i);
            return Change.HAS;
        }
    }

    Multimap<String, RegisteredSuite> suites = createMultimap(250);
    Multimap<String, RegisteredSpec> specs = createMultimap(2500);

    public void registerSuite(@NonNull final HapiSuite suite) {
        final var callStack = CallStack.grabFramesUpToButNotIncluding(SuitesInspector.class);
        final var dump = callStack.dump(WithLineNumbers.YES);

        Class<?> klass = getSuiteClassFromStack(callStack);
        final var klassName = klass.getSimpleName();
        if (klassName.equals("SuiteRunner")) {
            System.out.printf("*** SuiteRunner as suite:%n%s%n".formatted(dump));
        }
        final var isAbstract = Modifier.isAbstract(klass.getModifiers());
        if (!isAbstract) { // `HapiSuite` and `LoadTest` are abstract
            final var regSuite = new RegisteredSuite(suite, klass, currentSuiteKind, dump);
            suites.put(regSuite.name(), regSuite);
        }
    }

    public void registerSpec(@NonNull final HapiSpec spec) {
        final var callStack = CallStack.grabFramesUpToButNotIncluding(SuitesInspector.class);
        final var dump = callStack.dump(WithLineNumbers.YES);

        final var method = getSpecMethodFromStack(callStack);
        final var methodName = method.getName();
        final var methodKlass = method.getDeclaringClass();
        final var methodKlassName = methodKlass.getSimpleName();
        final var regSpec = new RegisteredSpec(spec, method, currentSuiteKind, dump);
        specs.put(regSpec.name(), regSpec);
    }

    @NonNull
    public Pair<Multimap<String, RegisteredSuite>, Multimap<String, RegisteredSpec>> getRegistry() {
        return Pair.of(suites, specs);
    }

    @NonNull
    public HapiTestRegistrar doPostRegistration() {
        final var noLongerRedundant = removeRedundancyFromRegistry();
        suites = noLongerRedundant.getLeft();
        specs = noLongerRedundant.getRight();
        return this;
    }

    /** Return the registered suites & specs, normalized by removing redundant ones */
    @NonNull
    Pair<Multimap<String, RegisteredSuite>, Multimap<String, RegisteredSpec>> removeRedundancyFromRegistry() {

        // Find the prerequisite suites

        final var prerequisiteSuites = this.suites.entries().stream()
                .filter(kv -> kv.getValue().kind() == SuiteKind.prerequisite)
                .map(Entry::getValue)
                .collect(Collectors.toSet());
        final var prerequisiteSuiteNames =
                prerequisiteSuites.stream().map(RegisteredSuite::name).collect(Collectors.toSet());

        final var redundantSuites = this.suites.entries().stream()
                .filter(kv -> kv.getValue().kind() != SuiteKind.prerequisite)
                .filter(kv -> prerequisiteSuiteNames.contains(kv.getKey()))
                .map(Entry::getValue)
                .collect(Collectors.toSet());

        // Get rid of all other versions of prerequisite suites

        final Multimap<String, RegisteredSuite> suites = createMultimap(250);
        this.suites.entries().stream()
                .filter(kv -> !redundantSuites.contains(kv.getValue()))
                .forEach(kv -> suites.put(kv.getKey(), kv.getValue()));

        // Get rid of all other versions of prerequisite specs

        final Multimap<String, RegisteredSpec> specs = createMultimap(2500);
        this.specs.entries().stream()
                .filter(kv -> !isRedundantSpec(kv.getValue(), prerequisiteSuiteNames))
                .forEach(kv -> specs.put(kv.getKey(), kv.getValue()));

        return Pair.of(suites, specs);
    }

    static boolean isRedundantSpec(@NonNull final RegisteredSpec spec, @NonNull final Set<String> prereqSuiteNames) {
        return prereqSuiteNames.contains(spec.method().getDeclaringClass().getName())
                && spec.kind() != SuiteKind.prerequisite;
    }

    public void analyzeRegistry(
            @NonNull final Optional<ManifestFile> manifest,
            @NonNull final Optional<IgnoresFile> ignores,
            final boolean removeErrorSuites,
            @NonNull Collection<String> suitesInError) {
        final var sb = new StringBuilder(10000);

        final var allRegisteredSuites =
                suites.values().stream().map(RegisteredSuite::klass).toList();
        final var allRegisteredSpecs =
                specs.values().stream().map(RegisteredSpec::spec).toList();

        if (doVerbose) {
            sb.append("%d suites registered, %d specs registered%n"
                    .formatted(allRegisteredSuites.size(), allRegisteredSpecs.size()));
            sb.append("registered suites:%n".formatted());

            for (final var suite : suites.keySet().stream().sorted().toList()) {
                sb.append("  %s%n".formatted(suite));
            }

            sb.append("registered specs:%n".formatted());
            for (final var spec :
                    specs.entries().stream().sorted(comparingByKey()).toList()) {
                sb.append("  %s (%s.%s)%n"
                        .formatted(
                                spec.getKey(),
                                spec.getValue().klass().getSimpleName(),
                                spec.getValue().name()));
            }
        }

        // Make sure all suites and specs are unique
        final var distinctRegisteredSuites =
                allRegisteredSuites.stream().distinct().toList();
        if (allRegisteredSuites.size() != distinctRegisteredSuites.size())
            sb.append("*** %d suites registered, only %d unique%n"
                    .formatted(allRegisteredSuites.size(), distinctRegisteredSuites.size()));

        final var distinctRegisteredSpecs =
                allRegisteredSpecs.stream().distinct().toList();
        if (allRegisteredSpecs.size() != distinctRegisteredSpecs.size())
            sb.append("*** %d specs registered, only %d unique%n"
                    .formatted(allRegisteredSpecs.size(), distinctRegisteredSpecs.size()));

        // Make sure all specs are part of a suite which is registered
        final var allSpecSuites =
                specs.values().stream().map(RegisteredSpec::klass).toList();

        final var suiteSet = Sets.newHashSet(allRegisteredSuites);
        final var specSuiteSet = Sets.newHashSet(allSpecSuites);
        final var suitesWithNoSpec = Sets.difference(suiteSet, specSuiteSet);
        final var specsWithNoSuite = specs.values().stream()
                .filter(rs -> !suiteSet.contains(rs.klass()))
                .collect(Collectors.toSet());

        // Show suites with no specs (after removing ignores)
        {
            final var swns = suitesWithNoSpec.copyInto(new HashSet<>(suitesWithNoSpec.size()));
            ignores.ifPresent(ignoresFile -> {
                final var r = reflection.getClassesFromNames(ignoresFile.suitesWithNoSpecs());
                r.onOk(swns::removeAll);
            });
            if (removeErrorSuites) {
                final var packagesWithSuites = reflection.getPackagesUnder(hapiSuiteRootPackageName);
                final var r = reflection.getClassesFromSimpleNames(
                        suitesInError, hapiSuiteRootPackageName, packagesWithSuites);
                r.onOk(swns::removeAll);
            }
            if (!swns.isEmpty()) {
                sb.append("*** suites (that initialized successfully) with no specs registered: %s%n".formatted(swns));
            }
        }

        if (!specsWithNoSuite.isEmpty())
            sb.append("*** some specs have no registered suite, missing suites: %s%n".formatted(specsWithNoSuite));

        // Report if there's anything to report
        if (!sb.isEmpty()) System.err.print(sb);
    }

    /** Given a stack trace which is calling out to us when creating a `HapiSuite` get the suite class */
    @NonNull
    static Class<?> getSuiteClassFromStack(@NonNull final CallStack callStack) {
        // Look for _deepest_ `HapiSuite` frame on stack (should be method `<init>`) - suite class is one frame
        // deeper than that
        final var hapiSuiteFrame = callStack.getTopmostFrameOfAnyOfTheseClassesSatisfying(
                hapiAbstractSuiteBases, sf -> sf.getMethodName().equals("<init>"));
        var targetSuiteFrame =
                hapiSuiteFrame.orElseThrow(() -> new IllegalStateException("HapiSuite.<init> frame not found"));
        while (true) {
            try {
                targetSuiteFrame = callStack.frameRelativeTo(targetSuiteFrame, Towards.BASE, 1);
            } catch (final RuntimeException ex) {
                throw new IllegalStateException("Cannot find most derived suite class from HapiSuite");
            }
            final var targetSuiteFrameClass = targetSuiteFrame.getDeclaringClass();
            if (!hapiAbstractSuiteBases.contains(targetSuiteFrameClass)) return targetSuiteFrameClass;
        }
    }

    /** Given a stack trace which is calling out to us when creating a `HapiSpec` get the spec's class and _method_ */
    @NonNull
    static Method getSpecMethodFromStack(@NonNull final CallStack callStack) {
        final int hapiSuiteFrame = callStack.size() - 1;
        if (hapiSuiteFrame < 2)
            throw new IllegalStateException("Topmost frame of `HapiSuite` is too close to top of stack");
        final var specKlass = callStack.getDeclaringClassOfFrame(hapiSuiteFrame - 1);
        return CallStack.getMethodFromFrame(callStack
                .getTopmostFrameOfClassSatisfying(specKlass, HapiTestRegistrar::frameMethodIsATest)
                .orElseThrow());
    }

    /** Checks that frame's method is _not_ marked with `@BddMethodIsNotATest` */
    static boolean frameMethodIsATest(@NonNull final StackWalker.StackFrame frame) {
        final var method = CallStack.getMethodFromFrame(frame);
        final var isNotATest = method.isAnnotationPresent(BddMethodIsNotATest.class);
        return !isNotATest;
    }

    @NonNull
    static <T> Multimap<String, T> createMultimap(final int expectedNHashKeys) {
        return MultimapBuilder.hashKeys(expectedNHashKeys).hashSetValues().build();
    }
}
