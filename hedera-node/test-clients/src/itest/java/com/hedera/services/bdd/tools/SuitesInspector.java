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
import com.hedera.services.bdd.tools.impl.HapiTestRegistrar.RegisteredSuite;
import com.hedera.services.bdd.tools.impl.JsonFileLoader;
import com.hedera.services.bdd.tools.impl.SuiteProvider;
import com.hedera.services.bdd.tools.impl.SuiteSearcher;
import edu.umd.cs.findbugs.annotations.NonNull;
import io.github.classgraph.ClassGraphException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;
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
// TODO: fix ethereum.feepaymentmatrix/matrixedPayerRelayerTest for parameterized tests

@Command(name = "inspector")
public class SuitesInspector implements Callable<Integer> {

    // start of picocli argumnent specifications

    enum ManifestType {
        DIRECT, // with HapiSpec names
        REGISTERED // with actual method names
    }

    @ArgGroup(multiplicity = "1")
    Operation operation;

    static class Operation {
        @Option(names = {"-l", "--list"})
        public boolean list;

        @Option(names = {"-w", "--write-manifest"})
        public ManifestType writeManifest;

        @Option(names = {"-a", "--analyze"})
        public boolean analyze;

        @Option(names = {"-s", "--suite-search"})
        public boolean suiteSearch;

        @Override
        public String toString() {
            if (list) return "--list";
            if (writeManifest != null) return "--write-manifest=" + writeManifest;
            if (analyze) return "--analyze";
            if (suiteSearch) return "--suite-search";
            return "--𝙪𝙣𝙠𝙣𝙤𝙬𝙣-𝙤𝙥𝙚𝙧𝙖𝙩𝙞𝙤𝙣";
        }
    }

    @Option(
            names = {"-k", "--kinds"},
            description = "Suite kinds, from: ${COMPLETION-CANDIDATES} (default: all",
            split = ",",
            defaultValue = "all")
    EnumSet<SuiteKind> suiteKinds;

    @Option(
            names = {"-i", "--ignore", "--ignore-file"},
            description = "File containing analysis 'ignores' - known bad suites and specs")
    Optional<Path> ignoresPath;

    @Option(
            names = {"-m", "--manifest", "--manifest-file"},
            description = "File containing manifest of all suites and specs")
    Optional<Path> manifestPath;

    @Option(names = {"--remove-error-suites"})
    public boolean removeErrorSuites;

    @Option(
            names = {"-v", "--verbose"},
            description = "More verbose output")
    public boolean doVerbose;

    // end of picocli argument specifications

    @NonNull
    final String[] args;

    @NonNull
    static final HapiTestRegistrar registrar = new HapiTestRegistrar();

    @NonNull
    Optional<ManifestFile> manifest = Optional.empty();

    @NonNull
    Optional<IgnoresFile> ignores = Optional.empty();

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

        {
            final var loader = new JsonFileLoader();
            manifestPath.ifPresent(path -> manifest = loader.load(ManifestFile.class, path, "test manifest"));
            ignoresPath.ifPresent(path -> ignores = loader.load(IgnoresFile.class, path, "ignores file"));
        }

        if (operation.list) doListTests();
        if (operation.analyze) doAnalysis();
        if (operation.writeManifest != null) doWriteManifest(operation.writeManifest);
        if (operation.suiteSearch) doSuiteSearch();

        return 0;
    }

    public record ManifestFile(@NonNull Set<String> suites, @NonNull Set<String> specs) {
        public ManifestFile {
            if (suites == null) suites = Set.of();
            if (specs == null) specs = Set.of();
        }
    }

    public record IgnoresFile(
            @NonNull Set<String> suites, @NonNull Set<String> specs, @NonNull Set<String> suitesWithNoSpecs) {
        public IgnoresFile {
            if (suites == null) suites = Set.of();
            if (specs == null) specs = Set.of();
            if (suitesWithNoSpecs == null) suitesWithNoSpecs = Set.of();
        }
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

        public String className() {
            return suite.getClass().getName();
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
            return suite.kind();
        }

        public String specName() {
            return spec.getName();
        }

        public String className() {
            return suite.className();
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
            this(test.suite().kind(), test.suite().className(), test.spec.getName());
        }

        TestSkeleton(@NonNull HapiTestRegistrar.RegisteredSpec spec) {
            this(spec.kind(), spec.klass().getName(), spec.method().getName());
        }

        private static final Comparator<TestSkeleton> comparer = Comparator.comparing(TestSkeleton::kind)
                .thenComparing(TestSkeleton::suite)
                .thenComparing(TestSkeleton::spec);

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

    record Manifest(int count, @NonNull List<TestSkeleton> tests) {}

    void doWriteManifest(@NonNull final ManifestType type) {

        final var results = getTestsDirectFromSuites();
        final var tests = results.getLeft();

        registrar.doPostRegistration();

        final var sortedTests = (switch (type) {
                    case DIRECT -> tests.stream().map(TestSkeleton::new);
                    case REGISTERED -> registrar.getRegistry().getRight().values().stream()
                            .map(TestSkeleton::new);
                })
                .sorted()
                .toList();

        try {
            final var jsonWriter = new ObjectMapper().writer(TestSkeleton.getPrettyPrinter());
            final var manifest = new Manifest(sortedTests.size(), sortedTests);
            final var serializedManifest = jsonWriter.writeValueAsString(manifest);
            System.out.printf("%n%s%n", serializedManifest);
        } catch (final JsonProcessingException ex) {
            System.err.printf("%nexception writing manifest (%s): %s%n", type, ex);
        }
    }

    void doListTests() {

        final var watch = new StopWatch();
        watch.start();

        final var results = getTestsDirectFromSuites();
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

    @NonNull
    static String getSuiteFromError(@NonNull final String message) {
        final var m = Pattern.compile("^.* from suite (\\S+) [(].*$").matcher(message);
        if (m.find()) return m.group(1);
        throw new IllegalStateException("suite name missing in message where it was expected");
    }

    void doAnalysis() {

        final var results = getTestsDirectFromSuites();
        final var tests = results.getLeft();
        final var errors = results.getRight();
        final var suitesInError = errors.stream()
                .filter(s -> s.contains("tests from suite"))
                .map(SuitesInspector::getSuiteFromError)
                .toList();

        if (!errors.isEmpty()) {
            System.out.printf("*** errors while instantiating specs:%n");
            for (final var s : errors) System.out.printf("   %s%n", s);
        } else {
            System.out.printf("no errors while instantiating specs%n");
        }

        registrar.doPostRegistration().analyzeRegistry(manifest, ignores, removeErrorSuites, suitesInError);

        final var searchedSuiteClasses = new SuiteSearcher().getAllHapiSuiteConcreteSubclasses();

        // Debugging code to make sure we're getting all tests - counts are different - no conceth
        // via registry:     89 suites, 879 specs
        // via direct calls: 89 suites (2 prereq, 6 seq, 81 conc, 0 conceth)
        //                  854 specs  (3 prereq, 102 seq, 749 conc, 0 conceth)
        // (counts are old) (remaining difference is due to `EthereumSuite` "matrix" parameterized test)
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
                        System.out.printf("*** Failed to match '%s': %s%n", s, ex.getMessage());
                        return "***";
                    }
                })
                .sorted()
                .distinct()
                .toList();

        System.out.printf("%n-------%n%n");
        System.out.printf("direct   suite names: %s%n", directSuiteNames);
        System.out.printf("%n-------%n%n");
        System.out.printf("registry suite names: %s%n", registrySuiteNames);

        final var suitesInRegistryButNotDirect = Sets.difference(
                        Set.copyOf(registrySuiteNames), Set.copyOf(directSuiteNames))
                .copyInto(new HashSet<>(registrySuiteNames.size() + directSuiteNames.size()));
        if (removeErrorSuites) suitesInRegistryButNotDirect.removeAll(suitesInError);

        final var suitesDirectButNotInRegistry =
                Sets.difference(Set.copyOf(directSuiteNames), Set.copyOf(registrySuiteNames));

        if (!suitesInRegistryButNotDirect.isEmpty())
            System.out.printf("*** suites in registry but not direct:%n%s%n", suitesInRegistryButNotDirect);
        if (!suitesDirectButNotInRegistry.isEmpty())
            System.out.printf("*** suites direct but not in registry:%n%s%n", suitesDirectButNotInRegistry);

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
                                System.out.printf("*** Failed to match '%s': %s%n", s, ex.getMessage());
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
        System.out.printf("direct   spec names: %s%n", directSpecNames);
        System.out.printf("%n-------%n%n");
        System.out.printf("registry spec names: %s%n", registrySpecNames);

        Set<String> specsInRegistryButNotDirect = Sets.difference(
                        Set.copyOf(registrySpecNames), Set.copyOf(directSpecNames))
                .copyInto(new HashSet<>(registrySpecNames.size() + directSpecNames.size()));
        if (removeErrorSuites) {
            final Predicate<String> anyOfPrefixes = startsWithAnyOfPredicate(suitesInError);
            specsInRegistryButNotDirect = specsInRegistryButNotDirect.stream()
                    .filter(s -> !anyOfPrefixes.test(s))
                    .collect(Collectors.toSet());
        }
        final var specsDirectButNotInRegistry =
                Sets.difference(Set.copyOf(directSpecNames), Set.copyOf(registrySpecNames));

        System.out.printf("%n-------%n%n");
        System.out.printf(
                "*** %d specs in registry but not direct: %s%n",
                specsInRegistryButNotDirect.size(),
                specsInRegistryButNotDirect.stream()
                        .sorted()
                        .toList()
                        .toString()
                        .replace(", ", "\n"));
        System.out.printf("%n-------%n%n");
        System.out.printf(
                "*** %d specs direct but not in registry: %s%n",
                specsDirectButNotInRegistry.size(),
                specsDirectButNotInRegistry.stream()
                        .sorted()
                        .toList()
                        .toString()
                        .replace(", ", "\n"));

        // compare against searched suite classes

        final var suiteNamesInRegistry = registry.getLeft().values().stream()
                .map(RegisteredSuite::klass)
                .map(Class::getName)
                .collect(Collectors.toSet());
        final var suiteNamesFromSearch =
                searchedSuiteClasses.stream().map(Class::getName).collect(Collectors.toSet());

        final var suitesInRegistryButNotFromSearch = Sets.difference(suiteNamesInRegistry, suiteNamesFromSearch);
        final var suitesFromSearchButNotFromRegistry = Sets.difference(suiteNamesFromSearch, suiteNamesInRegistry);

        System.out.printf("%n-------%n%n");
        if (!suitesInRegistryButNotFromSearch.isEmpty())
            System.out.printf(
                    "*** %d suites in registry but not found in search: %s%n",
                    suitesInRegistryButNotFromSearch.size(),
                    suitesInRegistryButNotFromSearch.stream()
                            .sorted()
                            .toList()
                            .toString()
                            .replace(", ", "\n"));
        if (!suitesFromSearchButNotFromRegistry.isEmpty())
            System.out.printf(
                    "*** %d suites found in search but not in registry: %s%n",
                    suitesFromSearchButNotFromRegistry.size(),
                    suitesFromSearchButNotFromRegistry.stream()
                            .sorted()
                            .toList()
                            .toString()
                            .replace(", ", "\n"));
    }

    // returns a predicate that tests if a string starts with a value
    static Predicate<String> startsWithPredicate(@NonNull final String prefix) {
        return s -> s.startsWith(prefix);
    }

    // returns a predicate that tests if _any_ of the predicates supplied returns true on the given object
    @SafeVarargs
    static <T> Predicate<T> anyPredicateMatchesPredicate(@NonNull final Predicate<T>... predicates) {
        return s -> Arrays.stream(predicates).anyMatch(p -> p.test(s));
    }

    @SuppressWarnings("unchecked")
    static Predicate<String> startsWithAnyOfPredicate(@NonNull final Collection<String> prefixes) {
        final var prefixPredicates = (Predicate<String>[]) prefixes.stream()
                .map(String::toLowerCase)
                .map(SuitesInspector::startsWithPredicate)
                .toArray(Predicate[]::new);
        return anyPredicateMatchesPredicate(prefixPredicates);
    }

    void doSuiteSearch() {
        List<Class<?>> suiteKlasses;
        try {
            final var searcher = new SuiteSearcher();
            suiteKlasses = searcher.getAllHapiSuiteConcreteSubclasses();
        } catch (final ClassGraphException ex) {
            System.err.printf("*** Error while scanning for BDD suites and methods: %s%n", ex);
            return;
        }
        System.out.printf("%d suites found by search%n", suiteKlasses.size());
        for (final var suiteName :
                suiteKlasses.stream().map(Class::getName).sorted().toList()) {
            System.out.printf("  %s%n", suiteName);
        }
    }

    Pair<Set<Test>, List<String>> getTestsDirectFromSuites() {
        final var tests = new TreeSet<Test>();
        final var errors = new ArrayList<String>(100);

        final var suiteFactories = new SuiteProvider();

        // Do the prerequisites first so that we have their suite names so that they can be filtered out of other suites
        Set<String> prerequisiteSuiteNames;
        {
            final var specs = specsBySuite(SuiteKind.prerequisite, suiteFactories::allPrerequisiteSuites);
            prerequisiteSuiteNames =
                    specs.getLeft().stream().map(Test::suite).map(Suite::name).collect(Collectors.toSet());
            tests.addAll(specs.getLeft());
            errors.addAll(specs.getRight());
        }

        for (final var kind : suiteKinds) {
            // No need to do the prerequisites again
            if (kind == SuiteKind.prerequisite) continue;
            // Need to filter out the prerequisite suites from all other suites - they're already added
            final var rawSpecs = specsBySuite(kind, () -> suiteFactories.allSuitesOfKind(kind));
            final var specs = rawSpecs.getLeft().stream()
                    .filter(test ->
                            !prerequisiteSuiteNames.contains(test.suite().name()))
                    .toList();
            tests.addAll(specs);
            errors.addAll(rawSpecs.getRight());
        }

        return Pair.of(tests, errors);
    }

    static Pair<List<Test>, List<String>> specsBySuite(
            @NonNull final SuiteKind suiteKind, @NonNull Supplier<List<Supplier<HapiSuite>>> suiteSuppliers) {

        final var tests = new ArrayList<Test>(1000);
        final var errors = new ArrayList<String>(100);

        try (final var ＿ = registrar.withSuiteKind(suiteKind)) {
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
