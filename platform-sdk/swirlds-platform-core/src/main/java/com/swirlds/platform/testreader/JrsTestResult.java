package com.swirlds.platform.testreader;

import edu.umd.cs.findbugs.annotations.NonNull;
import java.time.Instant;
import java.util.Objects;

/**
 * The result of a JRS test run.
 *
 * @param name          The name of the test
 * @param passed        Whether the test passed
 * @param testDirectory The directory where the test was run
 */
public record JrsTestResult(
        @NonNull String name,
        boolean passed,
        @NonNull String testDirectory) implements Comparable<JrsTestResult> {

    /**
     * Failing tests are always ordered before passing tests. If both tests are passing or both tests are failing, then
     * the tests are ordered by alphabetical order of the test name.
     *
     * @param that the object to be compared.
     * @return a negative integer, zero, or a positive integer as this object is less than, equal to, or greater than
     */
    @Override
    public int compareTo(@NonNull final JrsTestResult that) {
        if (this.passed && !that.passed) {
            return 1;
        } else if (!this.passed && that.passed) {
            return -1;
        } else {
            return this.name.compareTo(that.name);
        }
    }

    // TODO move the CSV stuff out of this class

    /**
     * Generate a CSV header for the test results.
     * @param sb the string builder to append to
     */
    public static void renderCsvHeader(@NonNull final StringBuilder sb) {
        sb.append("Name, Timestamp, Status, Summary, Data\n");
    }

    /**
     * Generate a CSV line for this test result.
     * @param sb the string builder to append to
     * @return a CSV line
     */

    public void renderCsvLine(@NonNull final StringBuilder sb) {
        final String browserUrl = generateWebBrowserUrl();

        sb.append(name).append(", ")
                .append(getTestTimestamp()).append(", ")
                .append(passed ? "PASS" : "FAIL").append(", ")
                .append(browserUrl).append("/summary.txt, ")
                .append(browserUrl).append("\n")
        ;
    }

    // TODO make these configurable
    private static final String GS_URL_PREFIX = "gs://swirlds-circleci-jrs-results/";
    private static final String GS_URL_REPLACEMENT = "http://35.247.76.217:8095/";

    /**
     * Get the timestamp of the test run.
     *
     * @return the timestamp of the test run
     */
    @NonNull
    public Instant getTestTimestamp() {
        // We should never get a null timestamp here because we only create JrsTestResults
        // from directories that contain a timestamp.
        return Objects.requireNonNull(JrsTestReader.parseTimestampFromDirectory(testDirectory));
    }

    /**
     * The test url stored in this test result is a gs:// url. This method generates a url that can be visited in a web
     * browser.
     *
     * @return a url that can be visited in a web browser
     */
    public String generateWebBrowserUrl() {
        return testDirectory.replace(GS_URL_PREFIX, GS_URL_REPLACEMENT);
    }
}
