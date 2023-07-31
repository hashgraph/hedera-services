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

import com.swirlds.platform.util.CommandResult;
import com.swirlds.platform.util.VirtualTerminal;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
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
     * Test directories contain a timestamp in the format ".../20230630-053633-ignoredArbitraryString/...". Attempt to
     * parse the timestamp from the directory name, and return it if found.
     *
     * @return the parsed timestamp, or null if no timestamp could be parsed.
     */
    @Nullable
    public static Instant parseTimestampFromDirectory(@NonNull final String remoteDirectory) {
        final String[] parts = remoteDirectory.split("/");
        for (final String part : parts) {
            final String[] subParts = part.split("-");

            if (subParts.length < 2 || subParts[0].length() != 8 || subParts[1].length() != 6) {
                continue;
            }

            try {
                final int year = Integer.parseInt(subParts[0].substring(0, 4));
                final int month = Integer.parseInt(subParts[0].substring(4, 6));
                final int day = Integer.parseInt(subParts[0].substring(6, 8));

                final int hour = Integer.parseInt(subParts[1].substring(0, 2));
                final int minute = Integer.parseInt(subParts[1].substring(2, 4));
                final int second = Integer.parseInt(subParts[1].substring(4, 6));

                // A bit of a hack, but there isn't a clean API to convert date info to an instant.

                final String instantString = year + "-" + ALIGNED_RIGHT.pad(Integer.toString(month), '0', 2)
                        + "-" + ALIGNED_RIGHT.pad(Integer.toString(day), '0', 2)
                        + "T" + ALIGNED_RIGHT.pad(Integer.toString(hour), '0', 2)
                        + ":" + ALIGNED_RIGHT.pad(Integer.toString(minute), '0', 2)
                        + ":" + ALIGNED_RIGHT.pad(Integer.toString(second), '0', 2)
                        + "Z";

                return Instant.parse(instantString);
            } catch (final NumberFormatException | DateTimeParseException e) {
                continue;
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
     * @param terminal         the virtual terminal
     * @param executorService  the executor service to use
     * @param rootDirectory    the root of the directory tree to explore
     * @param minimumTimestamp any timestamp directory with a timestamp less than this will be ignored
     * @return a list of directories that contain a timestamp, excluding directories with timestamps less than
     * {@code minimumTimestamp}
     */
    public static List<String> findTestPanelDirectories(
            @NonNull final VirtualTerminal terminal,
            @NonNull final ExecutorService executorService,
            @NonNull final String rootDirectory,
            @NonNull final Instant minimumTimestamp) {

        System.out.println("Searching for panel directories.");
        final ProgressIndicator progressIndicator = new ProgressIndicator();

        final Queue<String> directoriesToExplore = new LinkedBlockingQueue<>();
        directoriesToExplore.add(rootDirectory);

        final Queue<String> directoriesWithTimestamps = new LinkedBlockingQueue<>();

        while (!directoriesToExplore.isEmpty()) {

            final CountDownLatch latch = new CountDownLatch(directoriesToExplore.size());

            // Handle the next bach of work in parallel
            while (!directoriesToExplore.isEmpty()) {
                final String next = directoriesToExplore.remove();
                executorService.submit(() -> {
                    final Instant timestamp = parseTimestampFromDirectory(next);
                    if (timestamp == null) {
                        final List<String> subDirectories = lsRemoteDir(terminal, next);
                        directoriesToExplore.addAll(subDirectories);
                    } else if (!timestamp.isBefore(minimumTimestamp)) {
                        directoriesWithTimestamps.add(next);
                    }
                    progressIndicator.increment();
                    latch.countDown();
                });
            }

            abortAndThrowIfInterrupted(latch::await, "interrupted while waiting for directory search to complete");
        }

        System.out.println("\nFound " + directoriesWithTimestamps.size() + " panel directories.");

        final List<String> dirList = new ArrayList<>(directoriesWithTimestamps.size());
        dirList.addAll(directoriesWithTimestamps);
        return dirList;
    }

    /**
     * Descend into a directory tree and return all nested test directories.
     *
     * @param terminal         the virtual terminal
     * @param executorService  the executor service to use
     * @param rootDirectory    the root of the directory tree to explore
     * @param minimumTimestamp any test with a timestamp less than this will be ignored
     * @return a list of directories that contain a timestamp, excluding directories with timestamps less than
     * {@code minimumTimestamp}
     */
    public static List<String> findTestDirectories(
            @NonNull final VirtualTerminal terminal,
            @NonNull final ExecutorService executorService,
            @NonNull final String rootDirectory,
            @NonNull final Instant minimumTimestamp) {

        final List<String> panelDirectories =
                findTestPanelDirectories(terminal, executorService, rootDirectory, minimumTimestamp);

        System.out.println("Searching for test directories.");

        final ProgressIndicator progressIndicator = new ProgressIndicator();

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
                progressIndicator.increment();
                latch.countDown();
            });
        }

        abortAndThrowIfInterrupted(latch::await, "interrupted while waiting for directory search to complete");

        System.out.println("\nFound " + testDirectories.size() + " test directories.");

        final List<String> dirList = new ArrayList<>(testDirectories.size());
        dirList.addAll(testDirectories);

        return dirList;
    }

    /**
     * Get a list of test results.
     *
     * @param terminal         the virtual terminal
     * @param executorService  the executor service to use
     * @param rootDirectory    the root of the directory tree to explore
     * @param minimumTimestamp any test with a timestamp less than this will be ignored
     * @return a list of test results
     */
    @NonNull
    public static List<JrsTestResult> findTestResults(
            @NonNull final VirtualTerminal terminal,
            @NonNull final ExecutorService executorService,
            @NonNull final String rootDirectory,
            @NonNull final Instant minimumTimestamp) {

        final List<String> testDirectories =
                findTestDirectories(terminal, executorService, rootDirectory, minimumTimestamp);

        final List<JrsTestResult> testResults = new ArrayList<>(testDirectories.size());

        for (final String testDirectory : testDirectories) {

            final List<String> testFiles = lsRemoteDir(terminal, testDirectory);

            Boolean passed = null;

            for (final String testFile : testFiles) {
                if (testFile.endsWith("test-passed")) {
                    passed = true;
                    break;
                } else if (testFile.endsWith("test-failed")) {
                    passed = false;
                    break;
                }
            }

            if (passed == null) {
                System.out.println("unable to determine test result for " + testDirectory);
                continue;
            }

            final String[] parts = testDirectory.split("/");
            final String testName = parts[parts.length - 1];

            final JrsTestResult result = new JrsTestResult(
                    testName,
                    passed,
                    testDirectory);

            testResults.add(result);
        }

        return testResults;
    }

    /**
     * Generate a test report.
     *
     * @param terminal         the virtual terminal
     * @param executorService  the executor service to use
     * @param rootDirectory    the root of the directory tree to explore
     * @param minimumTimestamp any test with a timestamp less than this will be ignored
     * @param reportPath       the path to write the report to
     */
    public static void generateTestReport(
            @NonNull final VirtualTerminal terminal,
            @NonNull final ExecutorService executorService,
            @NonNull final String rootDirectory,
            @NonNull final Instant minimumTimestamp,
            @NonNull final Path reportPath) {
        final List<JrsTestResult> results =
                findTestResults(terminal, executorService, rootDirectory, minimumTimestamp);
        Collections.sort(results);

        final StringBuilder sb = new StringBuilder();
        JrsTestResult.renderCsvHeader(sb);
        for (final JrsTestResult result : results) {
            result.renderCsvLine(sb);
        }

        final String report = sb.toString();
        System.out.println(report);

        try {
            Files.write(reportPath, report.getBytes());
        } catch (final IOException e) {
            throw new UncheckedIOException("unable to generate test report", e);
        }
    }
}
