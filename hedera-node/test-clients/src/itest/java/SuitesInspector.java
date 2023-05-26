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

import com.hedera.services.bdd.spec.HapiSpec;
import com.hedera.services.bdd.suites.HapiSuite;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.TreeSet;
import java.util.concurrent.Callable;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import org.apache.commons.lang3.tuple.Pair;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

// TODO: Write all tests as manifest, then use manifest instead of time-consuming
// initial load
// TODO: Write manifest in JSON so it can be read, need proper suite class naemes
// TODO: `Test` record to include etheereum flag rather than messing with suite name
// TODO: Execute a test, then execute tests
// TODO: Make sure the report looks nice - possibly integrate it with JUnit so
// individual tests can be reported (and exceptions handled, etc.)
// TODO: Do individual test setup that I skipped
// TODO: Run tests off a list in a file - then on finish rewrite the file with a
// pass/fail indicator so you can run only failed tests if you want

@Command(name = "inspector")
public class SuitesInspector implements Callable<Integer> {

    enum SuiteKind {
        all,
        sequential,
        concurrent,
        concurrentetherium
    }

    @Option(
            names = {"-l", "--list"},
            description = "List suite names")
    boolean doListSuites;

    @Option(
            names = {"-k", "--kinds"},
            description = "Suite kinds, from: ${COMPLETION-CANDIDATES} (default: all",
            split = ",",
            defaultValue = "all")
    EnumSet<SuiteKind> suiteKinds;

    @NonNull
    final String[] args;

    public SuitesInspector(@NonNull final String[] args) {
        this.args = args;
    }

    @Override
    public Integer call() throws Exception {
        System.out.printf("Arguments: %s%n", String.join(", ", args));

        {
            // normalize suite kinds
            if (suiteKinds.contains(SuiteKind.all)) {
                suiteKinds = EnumSet.allOf(SuiteKind.class);
                suiteKinds.remove(SuiteKind.all);
            }
        }

        System.out.printf("  doListSuites: %s%n", doListSuites);
        System.out.printf(
                "  suiteKinds: %s%n", suiteKinds.stream().map(SuiteKind::name).collect(Collectors.joining(",")));

        if (doListSuites) doListSuites();

        return 0;
    }

    public static void main(@NonNull final String... args) {
        int exitCode = new CommandLine(new SuitesInspector(args)).execute(args);
        System.exit(exitCode);
    }

    // SUITE WORK STARTS HERE

    record Test(@NonNull String suiteName, @NonNull HapiSpec spec, @NonNull SuiteKind kind) {
        public static Comparator<Test> getComparer() {
            return Comparator.comparing(Test::suiteName)
                    .thenComparing(test -> test.spec().getName());
        }
    }

    void doListSuites() {
        final var tests = new TreeSet<Test>(Test.getComparer());
        final var errors = new ArrayList<String>();

        if (suiteKinds.contains(SuiteKind.sequential)) {
            final var seq = specsBySuite(SuiteKind.sequential, "", SequentialSuites.all());
            tests.addAll(seq.getLeft());
            errors.addAll(seq.getRight());
        }

        if (suiteKinds.contains(SuiteKind.concurrent)) {
            final var seq = specsBySuite(SuiteKind.concurrent, "", ConcurrentSuites.all());
            tests.addAll(seq.getLeft());
            errors.addAll(seq.getRight());
        }

        if (suiteKinds.contains(SuiteKind.concurrentetherium)) {
            final var seq = specsBySuite(SuiteKind.concurrentetherium, "_Eth", ConcurrentSuites.ethereumSuites());
            tests.addAll(seq.getLeft());
            errors.addAll(seq.getRight());
        }

        final var nErrors = errors.size();

        final var nSuites = tests.stream().map(Test::suiteName).distinct().count();
        final var nSuitesSequential = tests.stream()
                .filter(test -> test.kind() == SuiteKind.sequential)
                .map(Test::suiteName)
                .distinct()
                .count();
        final var nSuitesConcurrent = tests.stream()
                .filter(test -> test.kind() == SuiteKind.concurrent)
                .map(Test::suiteName)
                .distinct()
                .count();
        final var nSuitesConcurrentEth = tests.stream()
                .filter(test -> test.kind() == SuiteKind.concurrentetherium)
                .map(Test::suiteName)
                .distinct()
                .count();

        final var nTests = tests.size();
        final var nTestsSequential = tests.stream()
                .filter(test -> test.kind() == SuiteKind.sequential)
                .count();
        final var nTestsConcurrent = tests.stream()
                .filter(test -> test.kind() == SuiteKind.concurrent)
                .count();
        final var nTestsConcurrentEth = tests.stream()
                .filter(test -> test.kind() == SuiteKind.concurrentetherium)
                .count();

        final var sb = new StringBuilder(10000);

        sb.append("suites: %d total, %d seq, %d conc, %d conceth%n"
                .formatted(nSuites, nSuitesSequential, nSuitesConcurrent, nSuitesConcurrentEth));
        sb.append("tests:  %d total, %d seq, %d conc, %d conceth%n"
                .formatted(nTests, nTestsSequential, nTestsConcurrent, nTestsConcurrentEth));
        sb.append("errors: %d total%n".formatted(nErrors));
        sb.append('\n');

        for (final var test : tests) {
            sb.append("%s.%s [%s]%n".formatted(test.suiteName(), test.spec().getName(), test.kind()));
        }
        sb.append('\n');
        for (final var s : errors) {
            sb.append("%s%n".formatted(s));
        }
        System.out.printf("%n%s%n", sb);
    }

    Pair<List<Test>, List<String>> specsBySuite(
            @NonNull final SuiteKind suiteKind,
            @NonNull final String suiteSuffix,
            @NonNull Supplier<HapiSuite>[] suiteSuppliers) {

        final var tests = new ArrayList<Test>();
        final var errors = new ArrayList<String>();

        for (final var suiteSupplier : suiteSuppliers) {
            HapiSuite suite = null;
            try {
                suite = suiteSupplier.get();
            } catch (final RuntimeException ex) {
                errors.add("exception getting suite %s: %s".formatted(suiteSupplier, ex));
                continue;
            }
            List<HapiSpec> suiteSpecs = null;
            try {
                suiteSpecs = suite.getSpecsInSuiteWithOverrides();
            } catch (final RuntimeException ex) {
                errors.add("exception getting tests from suite %s: %s".formatted(suite.name() + suiteSuffix, ex));
                continue;
            }
            for (final var spec : suiteSpecs) {
                spec.setSuitePrefix(suite.name() + suiteSuffix);
                tests.add(new Test(suite.name() + suiteSuffix, spec, suiteKind));
            }
        }

        // At this point tests aren't really ready to run.  Some more setup needs to be done.
        // See, for example, `TestBase.suffixContextualizedSpecsFromConcurrent`.

        return Pair.of(tests, errors);
    }
}
