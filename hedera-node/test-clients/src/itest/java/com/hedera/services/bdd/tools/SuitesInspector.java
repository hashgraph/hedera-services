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

package com.hedera.services.bdd.tools;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.PrettyPrinter;
import com.fasterxml.jackson.core.util.DefaultIndenter;
import com.fasterxml.jackson.core.util.DefaultPrettyPrinter;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.Sets;
import com.hedera.services.bdd.spec.HapiSpec;
import com.hedera.services.bdd.suites.HapiSuite;
import com.hedera.services.bdd.tools.impl.HapiTestRegistrar;
import com.hedera.services.bdd.tools.impl.SuiteProvider;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.apache.commons.lang3.time.StopWatch;
import org.apache.commons.lang3.tuple.Pair;
import org.jetbrains.annotations.NotNull;
import picocli.CommandLine;
import picocli.CommandLine.ArgGroup;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

// TODO: Write all tests as manifest, then use manifest instead of time-consuming
// initial load
// TODO: Write manifest in JSON so it can be read, need proper suite class names
// TODO: Execute a test, then execute tests
// TODO: Make sure the report looks nice - possibly integrate it with JUnit so
// individual tests can be reported in IntelliJ (and exceptions handled, etc.)
// TODO: Run tests off a list in a file - then on finish rewrite the file with a
// pass/fail indicator so you can run only failed tests if you want
// TODO: specify dir/name or fullpath (?) for manifest
// TODO: read manifest if present
// TODO: compare manifest to new enumeration of tests
// TODO: discover tests by using the classwalker library
// TODO: discover suites not run via itests (e.g., the schedule suites)
// TODO: fix ethereum.feepaymentmatrix for parameterized tests

@Command(name = "inspector")
public class SuitesInspector implements Callable<Integer> {

    @ArgGroup(multiplicity = "1")
    Operation operation;

    static class Operation {
        @Option(names = {"-l", "--list"})
        public boolean list;

        @Option(names = {"-w", "--write-manifest"})
        public boolean writeManifest;

        @Option(names = {"-a", "--analyze"})
        public boolean analyze;

        @Override
        public String toString() {
            if (list) return "--list";
            if (writeManifest) return "--write-manifest";
            return "--unknown-operation";
        }
    }

    @Option(
            names = {"-k", "--kinds"},
            description = "Suite kinds, from: ${COMPLETION-CANDIDATES} (default: all",
            split = ",",
            defaultValue = "all")
    EnumSet<SuiteKind> suiteKinds;

    @Option(
            names = {"-v", "--verbose"},
            description = "More verbose output")
    public boolean doVerbose;

    @NonNull
    final String[] args;

    static final HapiTestRegistrar registrar = new HapiTestRegistrar();

    static {
        HapiSuite.setBddSuiteRegistrar(registrar::registerSuite);
        HapiSpec.setBddTestRegistrar(registrar::registerSpec);
    }

    public SuitesInspector(@NonNull final String[] args) {
        this.args = args;
    }

    @Override
    public Integer call() {
        System.out.printf("Arguments: %s%n", String.join(", ", args));

        registrar.doVerbose(doVerbose);

        {
            // normalize suite kinds
            if (suiteKinds.contains(SuiteKind.all)) {
                suiteKinds = EnumSet.allOf(SuiteKind.class);
                suiteKinds.remove(SuiteKind.all);
            }

            // Always need the prerequisite suites
            suiteKinds.add(SuiteKind.prerequisite);
        }

        System.out.printf("  operation: %s%n", operation);
        System.out.printf(
                "  suiteKinds: %s%n", suiteKinds.stream().map(SuiteKind::name).collect(Collectors.joining(",")));

        if (operation.list) doListTests();
        if (operation.analyze) doAnalysis();
        if (operation.writeManifest) doWriteManifest();

        return 0;
    }

    public static void main(@NonNull final String... args) {
        int exitCode = new CommandLine(new SuitesInspector(args)).execute(args);
        System.exit(exitCode);
    }

    // SUITE WORK STARTS HERE

    public record Suite(@NonNull HapiSuite suite, @NonNull SuiteKind kind) implements Comparable<Suite> {
        public String name() {
            return suite.name();
        }

        private static final Comparator<Suite> comparer =
                Comparator.comparing((Suite suite) -> suite.suite.name()).thenComparing(suite -> suite.kind);

        @Override
        public int compareTo(@NotNull final SuitesInspector.Suite o) {
            return comparer.compare(this, o);
        }
    }

    public record Test(@NonNull Suite suite, @NonNull HapiSpec spec) implements Comparable<Test> {
        public SuiteKind kind() {
            return suite.kind;
        }

        private static final Comparator<Test> comparer =
                Comparator.comparing(Test::suite).thenComparing(test -> test.spec.getName());

        @Override
        public int compareTo(@NotNull final Test o) {
            return comparer.compare(this, o);
        }
    }

    record TestSkeleton(@NonNull SuiteKind kind, @NonNull String suite, @NonNull String spec)
            implements Comparable<TestSkeleton> {
        TestSkeleton(@NonNull Test test) {
            this(test.suite().kind(), test.suite().name(), test.spec.getName());
        }

        private static final Comparator<TestSkeleton> comparer = Comparator.comparing(TestSkeleton::suite)
                .thenComparing(TestSkeleton::spec)
                .thenComparing(TestSkeleton::kind);

        @Override
        public int compareTo(@NotNull final TestSkeleton o) {
            return comparer.compare(this, o);
        }

        public static PrettyPrinter getPrettyPrinter() {
            return new DefaultPrettyPrinter()
                    .withArrayIndenter(new DefaultIndenter())
                    .withObjectIndenter(new DefaultIndenter(" ", ""));
        }
    }

    void doWriteManifest() {

        final var watch = new StopWatch();
        watch.start();

        final var results = getTestsFromSuites();
        final var tests = results.getLeft();

        watch.stop();

        registrar.doPostRegistration();

        final var jsonWriter = new ObjectMapper().writer(TestSkeleton.getPrettyPrinter());

        try {
            final var sortedTests =
                    tests.stream().limit(2500).map(TestSkeleton::new).sorted().toList();
            final var serializedTests = jsonWriter.writeValueAsString(sortedTests);
            System.out.printf(
                    "%n%s%n(%.3f seconds elapsed)%n", serializedTests, watch.getTime(TimeUnit.MILLISECONDS) / 1000.0f);
        } catch (final JsonProcessingException ex) {
            System.err.printf("%nexception writing manifest: %s%n", ex);
        }
    }

    void doListTests() {

        final var watch = new StopWatch();
        watch.start();

        final var results = getTestsFromSuites();
        final var tests = results.getLeft();
        final var errors = results.getRight();

        watch.stop();

        registrar.doPostRegistration();

        final var nErrors = errors.size();

        final var nSuites = tests.stream().map(Test::suite).distinct().count();
        final var nSuitesPrerequisite = tests.stream()
                .filter(test -> test.kind() == SuiteKind.prerequisite)
                .map(Test::suite)
                .distinct()
                .count();
        final var nSuitesSequential = tests.stream()
                .filter(test -> test.kind() == SuiteKind.sequential)
                .map(Test::suite)
                .distinct()
                .count();
        final var nSuitesConcurrent = tests.stream()
                .filter(test -> test.kind() == SuiteKind.concurrent)
                .map(Test::suite)
                .distinct()
                .count();
        final var nSuitesConcurrentEth = tests.stream()
                .filter(test -> test.kind() == SuiteKind.concurrentetherium)
                .map(Test::suite)
                .distinct()
                .count();

        final var nTests = tests.size();
        final var nTestsPrerequisite = tests.stream()
                .filter(test -> test.kind() == SuiteKind.prerequisite)
                .count();
        final var nTestsSequential = tests.stream()
                .filter(test -> test.kind() == SuiteKind.sequential)
                .count();
        final var nTestsConcurrent = tests.stream()
                .filter(test -> test.kind() == SuiteKind.concurrent)
                .count();
        final var nTestsConcurrentEth = tests.stream()
                .filter(test -> test.kind() == SuiteKind.concurrentetherium)
                .count();

        final var sb = new StringBuilder(125_000);

        sb.append("suites: %d total, %d, prereq %d seq, %d conc, %d conceth%n"
                .formatted(nSuites, nSuitesPrerequisite, nSuitesSequential, nSuitesConcurrent, nSuitesConcurrentEth));
        sb.append("tests:  %d total, %d prereq, %d seq, %d conc, %d conceth%n"
                .formatted(nTests, nTestsPrerequisite, nTestsSequential, nTestsConcurrent, nTestsConcurrentEth));
        sb.append("errors: %d total%n".formatted(nErrors));
        sb.append('\n');

        for (final var test : tests) {
            sb.append("%s.%s [%s]%n".formatted(test.suite().name(), test.spec().getName(), test.kind()));
        }
        sb.append('\n');
        for (final var s : errors) {
            sb.append("%s%n".formatted(s));
        }
        System.out.printf(
                "%n%s%n(%.3f seconds elapsed to create specs)%n", sb, watch.getTime(TimeUnit.MILLISECONDS) / 1000.0f);
    }

    void doAnalysis() {

        final var results = getTestsFromSuites();
        final var tests = results.getLeft();
        final var errors = results.getRight();

        if (!errors.isEmpty()) {
            System.out.print("*** errors while instantiating specs:%n".formatted());
            for (final var s : errors) System.out.print("   %s%n".formatted(s));
        } else {
            System.out.print("no errors while instantiating specs%n".formatted());
        }

        registrar.doPostRegistration().analyzeRegistry();

        // Debugging code to make sure we're getting all tests - counts are different - no conceth
        // via registry:     89 suites, 879 specs
        // via direct calls: 91 suites (2 prereq, 8 seq, 81 conc, 0 conceth)
        //                  854 specs  (3 prereq, 102 seq, 749 conc, 0 conceth)
        final var registry = registrar.getRegistry();

        final var onlySuiteNameRegex =
                ".*[.](.*?)(Suite)?$"; // Lots of suites don't end in "Suite" - but think about whether to remove the
        // suffix or not, might be more trouble than it's worth
        final var onlySuiteNamePattern = Pattern.compile(onlySuiteNameRegex);
        final var onlySuiteNameMatcher = onlySuiteNamePattern.matcher("");

        final var directSuiteNames = tests.stream()
                .map(Test::suite)
                .distinct()
                .map(Suite::name)
                .sorted()
                .distinct()
                .toList();
        final var registrySuiteNames = registry.getLeft().keySet().stream()
                .map(s -> {
                    try {
                        if (!onlySuiteNameMatcher.reset(s).matches())
                            throw new IllegalStateException("'matches' failed");
                        return onlySuiteNameMatcher.group(1);
                    } catch (final IllegalStateException | IndexOutOfBoundsException ex) {
                        System.out.print("*** Failed to match '%s': %s%n".formatted(s, ex.getMessage()));
                        return "***";
                    }
                })
                .sorted()
                .distinct()
                .toList();

        System.out.print("direct   suite names: %s%n".formatted(directSuiteNames));
        System.out.print("registry suite names: %s%n".formatted(registrySuiteNames));

        final var suitesInRegistryButNotDirect =
                Sets.difference(Set.copyOf(registrySuiteNames), Set.copyOf(directSuiteNames));
        final var suitesDirectButNotInRegistry =
                Sets.difference(Set.copyOf(directSuiteNames), Set.copyOf(registrySuiteNames));

        System.out.print("suites in registry but not direct:%n%s%n".formatted(suitesInRegistryButNotDirect));
        System.out.print("suites direct but not in registry:%n%s%n".formatted(suitesDirectButNotInRegistry));

        final var directSpecNames = tests.stream()
                .map(t -> t.suite().name() + '.' + t.spec().getName())
                .map(String::toLowerCase)
                .sorted()
                .distinct()
                .toList();
        final var registrySpecNames = registry.getRight().values().stream()
                .map(rs -> rs.method().getDeclaringClass().getName().transform(s -> {
                    try {
                        if (!onlySuiteNameMatcher.reset(s).matches())
                            throw new IllegalStateException("'matches' failed");
                        return onlySuiteNameMatcher.group(1);
                    } catch (final IllegalStateException | IndexOutOfBoundsException ex) {
                        System.out.print("*** Failed to match '%s': %s%n".formatted(s, ex.getMessage()));
                        return "***";
                    }
                })
                        + '.'
                        + rs.method().getName())
                .map(String::toLowerCase)
                .sorted()
                .distinct()
                .toList();

        System.out.printf("%n-------%n%n");
        System.out.print("direct   spec names: %s%n".formatted(directSpecNames));
        System.out.printf("%n-------%n%n");
        System.out.print("registry spec names: %s%n".formatted(registrySpecNames));

        final var specsInRegistryButNotDirect =
                Sets.difference(Set.copyOf(registrySpecNames), Set.copyOf(directSpecNames));
        final var specsDirectButNotInRegistry =
                Sets.difference(Set.copyOf(directSpecNames), Set.copyOf(registrySpecNames));

        System.out.printf("%n-------%n%n");
        System.out.print("%d specs in registry but not direct: %s%n"
                .formatted(
                        specsInRegistryButNotDirect.size(),
                        specsInRegistryButNotDirect.stream()
                                .sorted()
                                .toList()
                                .toString()
                                .replace(", ", "\n")));
        System.out.printf("%n-------%n%n");
        System.out.print("%d specs direct but not in registry: %s%n"
                .formatted(
                        specsDirectButNotInRegistry.size(),
                        specsDirectButNotInRegistry.stream()
                                .sorted()
                                .toList()
                                .toString()
                                .replace(", ", "\n")));
    }

    Pair<Set<Test>, List<String>> getTestsFromSuites() {
        final var tests = new TreeSet<Test>();
        final var errors = new ArrayList<String>(100);

        final var suiteFactories = new SuiteProvider();

        Set<String> prerequisiteSuiteNames;
        {
            final var specs = specsBySuite(SuiteKind.prerequisite, suiteFactories::allPrerequisiteSuites);
            prerequisiteSuiteNames = specs.getLeft().stream()
                    .map(t -> t.suite())
                    .map(Suite::name)
                    .distinct()
                    .collect(Collectors.toSet());
            tests.addAll(specs.getLeft());
            errors.addAll(specs.getRight());
        }

        if (suiteKinds.contains(SuiteKind.sequential)) {
            final var specs = specsBySuite(SuiteKind.sequential, suiteFactories::allSequentialSuites);

            // The prerequisite specs - which are always present - also appear in sequential.  Remove them so
            // they don't get added twice

            final var seqTests = specs.getLeft().stream()
                    .filter(test ->
                            !prerequisiteSuiteNames.contains(test.suite().name()))
                    .toList();
            tests.addAll(seqTests);
            errors.addAll(specs.getRight());
        }

        if (suiteKinds.contains(SuiteKind.concurrent)) {
            final var specs = specsBySuite(SuiteKind.concurrent, suiteFactories::allConcurrentSuites);
            tests.addAll(specs.getLeft());
            errors.addAll(specs.getRight());
        }

        if (suiteKinds.contains(SuiteKind.concurrentetherium)) {
            final var specs = specsBySuite(SuiteKind.concurrentetherium, suiteFactories::allConcurrentEthereumSuites);
            tests.addAll(specs.getLeft());
            errors.addAll(specs.getRight());
        }

        return Pair.of(tests, errors);
    }

    Pair<List<Test>, List<String>> specsBySuite(
            @NonNull final SuiteKind suiteKind, @NonNull Supplier<List<Supplier<HapiSuite>>> suiteSuppliers) {

        final var tests = new ArrayList<Test>(1000);
        final var errors = new ArrayList<String>(100);

        try (final var ＿ = registrar.WithSuiteKind(suiteKind)) {
            for (final var suiteSupplier : suiteSuppliers.get()) {
                HapiSuite suite;
                try {
                    suite = suiteSupplier.get();
                } catch (final RuntimeException ex) {
                    errors.add("exception getting suite %s (%s): %s".formatted(suiteSupplier, suiteKind, ex));
                    continue;
                }

                {
                    suite.skipClientTearDown();
                    suite.setOnlyLogHeader();
                }

                List<HapiSpec> suiteSpecs;
                try {
                    suiteSpecs = suite.getSpecsInSuiteWithOverrides();
                } catch (final RuntimeException ex) {
                    errors.add("exception getting tests from suite %s (%s): %s".formatted(suite.name(), suiteKind, ex));
                    continue;
                }

                for (final var spec : suiteSpecs) {
                    spec.setSuitePrefix(suite.name());
                    tests.add(new Test(new Suite(suite, suiteKind), spec));
                }
            }
        }

        return Pair.of(tests, errors);
    }
}
