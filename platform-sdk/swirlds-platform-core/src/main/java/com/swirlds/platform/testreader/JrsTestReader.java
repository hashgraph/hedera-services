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

package com.swirlds.platform.testreader;

import static com.swirlds.common.formatting.HorizontalAlignment.ALIGNED_RIGHT;
import static com.swirlds.common.threading.interrupt.Uninterruptable.abortAndThrowIfInterrupted;
import static com.swirlds.common.units.TimeUnit.UNIT_MILLISECONDS;
import static com.swirlds.platform.testreader.TestStatus.FAIL;
import static com.swirlds.platform.testreader.TestStatus.PASS;
import static com.swirlds.platform.testreader.TestStatus.UNKNOWN;

import com.swirlds.common.formatting.UnitFormatter;
import com.swirlds.common.utility.CompareTo;
import com.swirlds.platform.util.CommandResult;
import com.swirlds.platform.util.VirtualTerminal;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * Utilities for reading JRS test results and creating a report.
 */
public final class JrsTestReader {

    private JrsTestReader() {}

    /**
     * Test directories contain a timestamp in the format "20230630-053633-ignoredArbitraryString". Attempt to parse the
     * timestamp from this string, and return it if found.
     *
     * @return the parsed timestamp, or null if no timestamp could be parsed.
     */
    @Nullable
    public static Instant parseTimestampFromDirectory(@NonNull final String timestampString) {
        final String[] parts = timestampString.split("-");

        if (parts.length < 2 || parts[0].length() != 8 || parts[1].length() != 6) {
            return null;
        }

        try {
            final int year = Integer.parseInt(parts[0].substring(0, 4));
            final int month = Integer.parseInt(parts[0].substring(4, 6));
            final int day = Integer.parseInt(parts[0].substring(6, 8));

            final int hour = Integer.parseInt(parts[1].substring(0, 2));
            final int minute = Integer.parseInt(parts[1].substring(2, 4));
            final int second = Integer.parseInt(parts[1].substring(4, 6));

            // A bit of a hack, but there isn't a clean API to convert date info to an instant.

            final String instantString = year + "-" + ALIGNED_RIGHT.pad(Integer.toString(month), '0', 2)
                    + "-" + ALIGNED_RIGHT.pad(Integer.toString(day), '0', 2)
                    + "T" + ALIGNED_RIGHT.pad(Integer.toString(hour), '0', 2)
                    + ":" + ALIGNED_RIGHT.pad(Integer.toString(minute), '0', 2)
                    + ":" + ALIGNED_RIGHT.pad(Integer.toString(second), '0', 2)
                    + "Z";

            return Instant.parse(instantString);
        } catch (final NumberFormatException | DateTimeParseException e) {
            return null;
        }
    }

    /**
     * Test paths contain a timestamp in the format ".../20230630-053633-ignoredArbitraryString/...". Attempt to parse
     * the timestamp from this string, and return it if found.
     *
     * @return the parsed timestamp, or null if no timestamp could be parsed.
     */
    @Nullable
    public static Instant parseTimestampFromPath(@NonNull final String pathString) {
        final String[] directories = pathString.split("/");
        for (final String directory : directories) {
            final Instant instant = parseTimestampFromDirectory(directory);
            if (instant != null) {
                return instant;
            }
        }
        return null;
    }

    /**
     * Get the contents of a remote directory.
     *
     * @param terminal  the virtual terminal
     * @param remoteDir the remote directory to look into
     * @return a list of fully qualified directories in the remote directory
     */
    @NonNull
    public static List<String> lsRemoteDir(@NonNull final VirtualTerminal terminal, @NonNull final String remoteDir) {

        final CommandResult result = terminal.run("gsutil", "ls", remoteDir);

        if (!result.isSuccessful()) {
            throw new RuntimeException("Failed to list remote directory: " + remoteDir);
        }

        final String[] directories = result.out().split("\n");
        return List.of(directories);
    }

    /**
     * Descend into a directory tree and return all nested test panel directories. Test panel directories contain a
     * timestamp (as parsed by {@link #parseTimestampFromDirectory(String)}). The subtrees beneath timestamp directories
     * are not explored by this method.
     *
     * @param terminal        the virtual terminal
     * @param executorService the executor service to use
     * @param rootDirectory   the root of the directory tree to explore
     * @param now             the current time
     * @param maximumAge      the maximum age of tests to consider
     * @return a list of directories that contain a timestamp, excluding directories with timestamps less than
     * {@code minimumTimestamp}
     */
    public static List<String> findTestPanelDirectories(
            @NonNull final VirtualTerminal terminal,
            @NonNull final ExecutorService executorService,
            @NonNull final String rootDirectory,
            @NonNull final Instant now,
            @NonNull final Duration maximumAge) {

        terminal.getProgressIndicator().writeMessage("Searching for panel directories.");

        final Queue<String> directoriesToExplore = new LinkedBlockingQueue<>();
        directoriesToExplore.add(rootDirectory);

        final Queue<String> directoriesWithTimestamps = new LinkedBlockingQueue<>();

        while (!directoriesToExplore.isEmpty()) {

            final CountDownLatch latch = new CountDownLatch(directoriesToExplore.size());

            // Handle the next bach of work in parallel
            while (!directoriesToExplore.isEmpty()) {
                final String next = directoriesToExplore.remove();
                executorService.submit(() -> {
                    final Instant timestamp = parseTimestampFromPath(next);


                    if (timestamp == null) {
                        final List<String> subDirectories = lsRemoteDir(terminal, next);
                        directoriesToExplore.addAll(subDirectories);
                    } else {
                        final Duration age = Duration.between(timestamp, now);
                        if (CompareTo.isGreaterThan(age, maximumAge)) {
                            // Test is too old, ignore it
                            latch.countDown();
                            return;
                        }

                        directoriesWithTimestamps.add(next);
                    }
                    latch.countDown();
                });
            }

            abortAndThrowIfInterrupted(latch::await, "interrupted while waiting for directory search to complete");
        }

        terminal.getProgressIndicator()
                .writeMessage("Found " + directoriesWithTimestamps.size() + " panel directories.");

        final List<String> dirList = new ArrayList<>(directoriesWithTimestamps.size());
        dirList.addAll(directoriesWithTimestamps);
        return dirList;
    }

    /**
     * Descend into a directory tree and return all nested test directories.
     *
     * @param terminal        the virtual terminal
     * @param executorService the executor service to use
     * @param rootDirectory   the root of the directory tree to explore
     * @param now             the current time
     * @param maximumAge      the maximum age of tests to consider
     * @return a list of directories that contain a timestamp, excluding directories with timestamps less than
     * {@code minimumTimestamp}
     */
    public static List<String> findTestDirectories(
            @NonNull final VirtualTerminal terminal,
            @NonNull final ExecutorService executorService,
            @NonNull final String rootDirectory,
            @NonNull final Instant now,
            @NonNull final Duration maximumAge) {

        final List<String> panelDirectories =
                findTestPanelDirectories(terminal, executorService, rootDirectory, now, maximumAge);

        terminal.getProgressIndicator().writeMessage("Searching for test directories.");

        final Queue<String> testDirectories = new LinkedBlockingDeque<>();
        final CountDownLatch latch = new CountDownLatch(panelDirectories.size());

        for (final String panelDirectory : panelDirectories) {
            executorService.submit(() -> {
                final List<String> panelContents = lsRemoteDir(terminal, panelDirectory);

                for (final String potentialTestDirectory : panelContents) {
                    if (potentialTestDirectory.endsWith(".log")) {
                        continue;
                    }
                    testDirectories.add(potentialTestDirectory);
                }
                latch.countDown();
            });
        }

        abortAndThrowIfInterrupted(latch::await, "interrupted while waiting for directory search to complete");

        terminal.getProgressIndicator().writeMessage("Found " + testDirectories.size() + " test directories.");

        final List<String> dirList = new ArrayList<>(testDirectories.size());
        dirList.addAll(testDirectories);

        return dirList;
    }

    /**
     * Get a list of test results.
     *
     * @param terminal        the virtual terminal
     * @param executorService the executor service to use
     * @param rootDirectory   the root of the directory tree to explore
     * @param now             the current time
     * @param maximumAge      the maximum age of tests to consider
     * @return a list of test results
     */
    @NonNull
    public static List<JrsTestResult> findTestResults(
            @NonNull final VirtualTerminal terminal,
            @NonNull final ExecutorService executorService,
            @NonNull final String rootDirectory,
            @NonNull final Instant now,
            @NonNull final Duration maximumAge) {

        final List<String> testDirectories =
                findTestDirectories(terminal, executorService, rootDirectory, now, maximumAge);

        terminal.getProgressIndicator().writeMessage("Scanning tests for data.");

        final Queue<JrsTestResult> testResults = new LinkedBlockingQueue<>();

        final CountDownLatch latch = new CountDownLatch(testDirectories.size());

        for (final String testDirectory : testDirectories) {
            final Runnable task = () -> {
                final List<String> testFiles = lsRemoteDir(terminal, testDirectory);

                TestStatus status = UNKNOWN;

                for (final String testFile : testFiles) {
                    if (testFile.endsWith("test-passed")) {
                        status = PASS;
                        break;
                    } else if (testFile.endsWith("test-failed")) {
                        status = FAIL;
                        break;
                    }
                }

                final String[] parts = testDirectory.split("/");

                if (parts.length < 3) {
                    System.out.println("Invalid test directory structure");
                    latch.countDown();
                    return;
                }

                final String timestampString = parts[parts.length - 2];
                final Instant timestamp = parseTimestampFromDirectory(timestampString);
                if (timestamp == null) {
                    System.out.println("Unable to parse timestamp from string: " + testDirectory);
                    latch.countDown();
                    return;
                }

                final String testName = parts[parts.length - 1];
                final String panelName = parts[parts.length - 3];

                final JrsTestResult result = new JrsTestResult(
                        new JrsTestIdentifier(panelName, testName),
                        status,
                        timestamp,
                        testDirectory);

                testResults.add(result);
                latch.countDown();
            };

            executorService.submit(task);
        }
        abortAndThrowIfInterrupted(latch::await, "interrupted while waiting for test search to complete");

        terminal.getProgressIndicator().writeMessage("Found results for " + testResults.size() + " tests.");

        return new ArrayList<>(testResults);
    }

//    /**
//     * Generate a test report.
//     *
//     * @param terminal         the virtual terminal
//     * @param executorService  the executor service to use
//     * @param rootDirectory    the root of the directory tree to explore
//     * @param minimumTimestamp any test with a timestamp less than this will be ignored
//     * @param reportPath       the path to write the report to
//     */
//    public static void generateTestReport(
//            @NonNull final VirtualTerminal terminal,
//            @NonNull final ExecutorService executorService,
//            @NonNull final String rootDirectory,
//            @NonNull final Instant minimumTimestamp,
//            @NonNull final Path reportPath) {
//        final List<JrsTestResult> results =
//                findTestResults(terminal, executorService, rootDirectory, minimumTimestamp);
//        Collections.sort(results);
//
//        final StringBuilder sb = new StringBuilder();
//        JrsTestResult.renderCsvHeader(sb);
//        for (final JrsTestResult result : results) {
//            result.renderCsvLine(sb);
//        }
//
//        final String report = sb.toString();
//        terminal.getProgressIndicator().writeMessage(
//                "\n\n===================================================================================\n\n");
//        System.out.println(report);
//
//        if (Files.exists(reportPath)) {
//            try {
//                Files.delete(reportPath);
//            } catch (final IOException e) {
//                throw new UncheckedIOException("unable to delete existing test report", e);
//            }
//        }
//
//        try {
//            Files.write(reportPath, report.getBytes());
//        } catch (final IOException e) {
//            throw new UncheckedIOException("unable to generate test report", e);
//        }
//    }

//    /**
//     * Process a test string. A test string is a file path from a google bucket. Some file paths describe a test, others
//     * do not. This method will return a {@link JrsTestResult} if the test string describes a test, or null otherwise.
//     *
//     * <p>
//     * A valid test path has the following format (but without the newlines and comments):
//     * <pre>
//     * gs://swirlds-circleci-jrs-results/
//     * swirlds-automation/
//     * develop/
//     * 6N_1C/
//     * NDReconnectCorrectness/                              // this is the panel
//     * 20230802-064208-GCP-ND-Reconnect-Correctness-6N-1C/  // timestamp
//     * AllProtectedFilesUpdate-NDReconnect-1-16m/           // this is the name
//     * "test-failed" or "test-passed"                       // pass/fail info
//     * </pre>
//     *
//     * @param testString the test string to process
//     * @return
//     */
//    @Nullable
//    private static JrsTestResult processTestString(@NonNull final String testString) {
//        final String[] parts = testString.split("/");
//        if (parts.length < 4) {
//            // This is not a test result file
//            return null;
//        }
//
//        final boolean passed;
//        if (parts[parts.length - 1].equals("test-passed")) {
//            passed = true;
//        } else if (parts[parts.length - 1].equals("test-failed")) {
//            passed = false;
//        } else {
//            // This is not a test result file
//            return null;
//        }
//
//        final String timestampString = parts[parts.length - 3];
//        final Instant timestamp = parseTimestampFromDirectory(timestampString);
//        if (timestamp == null) {
//            System.out.println("Unable to parse timestamp from string: " + testString);
//            return null;
//        }
//
//        final String testName = parts[parts.length - 2];
//        final String panelName = parts[parts.length - 4];
//
//        // The test directory is the immediate parent of the test result
//        final String[] directoryParts = new String[parts.length - 1];
//        System.arraycopy(parts, 0, directoryParts, 0, directoryParts.length);
//        final String testDirectory = String.join("/", directoryParts);
//
//        return new JrsTestResult(
//                new JrsTestIdentifier(panelName, testName),
//                passed ? PASS : FAIL,
//                timestamp,
//                testDirectory);
//    }

//    /**
//     * Generate a test report.
//     *
//     * @param terminal      the virtual terminal
//     * @param rootDirectory the root of the gs directory tree to explore
//     */
//    @NonNull
//    public static List<JrsTestResult> getTestResults(
//            @NonNull final VirtualTerminal terminal,
//            @NonNull final String rootDirectory) {
//
//        final List<JrsTestResult> results = new ArrayList<>();
//
//        final ProgressIndicator progressIndicator = new ProgressIndicator(1);
//        progressIndicator.setColorEnabled(true);
//
//        final Consumer<String> processTestString = s -> {
//            final JrsTestResult result = processTestString(s);
//            if (result != null) {
//                results.add(result);
//                final int count = results.size();
//                progressIndicator.setEndOfLineMessage("Test" + (count == 1 ? "" : "s") + " found: " + count);
//            }
//            progressIndicator.increment();
//        };
//
//        terminal.run(
//                processTestString,
//                System.err::println,
//                "gsutil", "ls", rootDirectory + "/*/*/*/*"); // TODO set depth
//
//        return results;
//    }

    //    // TODO make these configurable
    private static final String GS_URL_PREFIX = "gs://swirlds-circleci-jrs-results/";
    private static final String GS_URL_REPLACEMENT = "http://35.247.76.217:8095/";

    /**
     * The test url stored in this test result is a gs:// url. This method generates a url that can be visited in a web
     * browser.
     *
     * @return a url that can be visited in a web browser
     */
    public static String generateWebBrowserUrl(@NonNull final String testDirectory) {
        return testDirectory.replace(GS_URL_PREFIX, GS_URL_REPLACEMENT);
    }

    public static void generateHyperlink(
            @NonNull final StringBuilder sb,
            @NonNull final String text,
            @NonNull final String url) {

        sb.append("<a href=\"").append(url).append("\">").append(text).append("</a>");
    }

    public static void generateColoredHyperlink(
            @NonNull final StringBuilder sb,
            @NonNull final String text,
            @NonNull final String url,
            @NonNull final String color) {

        sb.append("<a style=\"color: ").append(color).append("\", href=\"")
                .append(url).append("\">").append(text).append("</a>");
    }

    private static void generateHistory(
            @NonNull final StringBuilder sb,
            @NonNull final List<JrsTestResult> historicalResults) {

        // Always ignore the first result since it is already reported
        for (int index = 1; index < historicalResults.size(); index++) {

            final JrsTestResult result = historicalResults.get(index);

            final String testUrl = generateWebBrowserUrl(result.testDirectory());
            final String resultString;
            final String color;
            if (result.status() == PASS) {
                resultString = "P";
                color = "mediumSeaGreen";
            } else if (result.status() == FAIL) {
                resultString = "F";
                color = "tomato";
            } else {
                resultString = "?";
                color = "slateBlue";
            }
            generateColoredHyperlink(sb, resultString, testUrl, color);
        }
    }

    public static void generateTestReport(
            @NonNull final VirtualTerminal terminal,
            @NonNull final ExecutorService executor,
            @NonNull final String rootDirectory,
            @NonNull final Duration maximumAge,
            @NonNull final Path outputFile) {

        final Instant now = Instant.now();

        final List<JrsTestResult> results = findTestResults(terminal, executor, rootDirectory, now, maximumAge);

        // Sort tests by unique type.
        final Map<JrsTestIdentifier, List<JrsTestResult>> resultsByTestType = new HashMap<>();
        for (final JrsTestResult result : results) {
            final JrsTestIdentifier id = result.id();
            final List<JrsTestResult> resultsForType = resultsByTestType.computeIfAbsent(id, k -> new ArrayList<>());
            resultsForType.add(result);
        }

        // Get an alphabetized list of test types.
        final List<JrsTestIdentifier> testTypes = new ArrayList<>(resultsByTestType.keySet());
        Collections.sort(testTypes);

        // Sort each test of the same type by timestamp.
        for (final List<JrsTestResult> tests : resultsByTestType.values()) {
            Collections.sort(tests);
        }

        final StringBuilder sb = new StringBuilder();

        sb.append("<!DOCTYPE html>\n");
        sb.append("<html>\n");
        sb.append("<head>\n");
        sb.append("<title>").append("JRS Test Report: ").append(now).append("</title>\n"); // TODO date formatting

        sb.append("<style>\n");
        sb.append("table { border: 5px solid black; }\n");
        sb.append("th { border: 1px solid black; background-color: #96D4D4; position: sticky; top: 0; }\n");
        sb.append("td { border: 1px solid black; padding: 10px; }\n");
        sb.append("tr:nth-child(even) { background-color: lightgray; }\n");

        sb.append("</style>\n");

        sb.append("</head>\n");
        sb.append("<body>\n");

        sb.append("<center>\n");
        sb.append("<table>\n");

        // Headers
        sb.append("<tr>\n");
        sb.append("<th>Panel</th>\n");
        sb.append("<th>Test Name</th>\n");
        sb.append("<th>Age</th>\n");
        sb.append("<th>Status</th>\n");
        sb.append("<th>Summary</th>\n");
        sb.append("<th>Metrics</th>\n");
        sb.append("<th>Data</th>\n");
        sb.append("<th>History</th>\n");
        sb.append("</tr>\n");

        for (final JrsTestIdentifier testType : testTypes) {

            final List<JrsTestResult> tests = resultsByTestType.get(testType);
            final JrsTestResult mostRecentTest = tests.get(0);

            final String testUrl = generateWebBrowserUrl(mostRecentTest.testDirectory());

            sb.append("<tr>\n");
            sb.append("<td>").append(testType.panel()).append("</td>\n");
            sb.append("<td><b>").append(testType.name()).append("</b></td>\n");

            final Duration testAge = Duration.between(mostRecentTest.timestamp(), now);
            final String ageString = new UnitFormatter(testAge.toMillis(), UNIT_MILLISECONDS)
                    .setAbbreviate(false)
                    .render() + " ago";

            sb.append("<td>").append(ageString).append("</td>\n");

            sb.append("<td ");
            if (mostRecentTest.status() == PASS) {
                sb.append("bgcolor=\"mediumSeaGreen\"");
            } else if (mostRecentTest.status() == FAIL) {
                sb.append("bgcolor=\"tomato\"");
            } else {
                sb.append("bgcolor=\"slateBlue\"");
            }
            sb.append("><center>").append(mostRecentTest.status().name()).append("</center></td>\n");

            sb.append("<td>");
            generateHyperlink(sb, "summary", testUrl + "summary.txt");
            sb.append("</td>\n");
            sb.append("<td>");
            generateHyperlink(sb, "metrics", testUrl + "multipage_pdf.pdf");
            sb.append("</td>\n");
            sb.append("<td>");
            generateHyperlink(sb, "data", testUrl);
            sb.append("</td>\n");
            sb.append("<td>");
            generateHistory(sb, tests);
            sb.append("</td>\n");
            sb.append("</tr>\n");
        }

        sb.append("</table>\n");
        sb.append("</center>\n");
        sb.append("</body>\n");
        sb.append("</html>\n");

        final String reportString = sb.toString();
        try {
            Files.write(outputFile, reportString.getBytes());
        } catch (final IOException e) {
            throw new UncheckedIOException("unable to generate test report", e);
        }
    }
}
