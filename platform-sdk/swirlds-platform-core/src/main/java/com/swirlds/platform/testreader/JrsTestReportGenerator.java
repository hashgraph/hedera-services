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

import static com.swirlds.common.units.TimeUnit.UNIT_MILLISECONDS;
import static com.swirlds.platform.testreader.TestStatus.FAIL;
import static com.swirlds.platform.testreader.TestStatus.PASS;

import com.swirlds.common.formatting.UnitFormatter;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.Month;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Generates HTML JRS Test Reports.
 */
public final class JrsTestReportGenerator {

    private JrsTestReportGenerator() {}

    /**
     * The test url stored in this test result is a gs:// url. This method generates a url that can be visited in a web
     * browser.
     *
     * @return a url that can be visited in a web browser
     */
    private static String generateWebBrowserUrl(
            @NonNull final String testDirectory,
            @NonNull final String bucketPrefix,
            @NonNull final String bucketPrefixReplacement) {
        return testDirectory.replace(bucketPrefix, bucketPrefixReplacement);
    }

    private static void generateHyperlink(
            @NonNull final StringBuilder sb, @NonNull final String text, @NonNull final String url) {

        sb.append("<a target=\"_blank\" href=\"%s\">%s</a>".formatted(url, text));
    }

    private static void generateColoredHyperlink(
            @NonNull final StringBuilder sb,
            @NonNull final String text,
            @NonNull final String url,
            @NonNull final String color) {

        sb.append("<a target=\"_blank\" style=\"color: %s\" href=\"%s\">%s</a>".formatted(color, url, text));
    }

    private static void generateTitle(@NonNull final StringBuilder sb, @NonNull final Instant now) {
        sb.append("<title>").append("JRS Test Report: ").append(now).append("</title>\n"); // TODO date formatting
    }

    private static void generatePageStyle(@NonNull final StringBuilder sb) {
        final LocalDate currentDate = LocalDate.now();
        final boolean april2 = currentDate.getMonth().equals(Month.APRIL) && currentDate.getDayOfMonth() == 2;
        final String april2Style; // TODO test with today's date
        if (april2) {
            april2Style =
                    """
                            /* you have activated my trap card */
                            html * {
                                font-family: Snell Roundhand, cursive;
                            }""";
        } else {
            april2Style = "";
        }

        sb.append(
                """
                        <style>
                            .testDataTable > tbody > tr > th {
                                border: 1px solid black;
                                background-color: #96D4D4;
                                position: sticky;
                                top: 0;
                                padding: 5px;
                            }
                            .testDataTable > tbody > tr > td {
                                border: 1px solid black;
                                padding: 10px;
                            }
                            .testDataTable > tbody > tr:nth-child(even) {
                                background-color: lightgray;
                            }
                            .sidePanel {
                                position: sticky;
                                top: 12px;
                            }
                            .topLevelTable > tbody > tr > td {
                                border: none;
                            }
                            .summaryTable > tbody > tr > td {
                                    border: none;
                                    padding-left: 5px;
                                    padding-right: 5px;
                                    vertical-align: top;
                                }
                            %s
                        </style>
                        """
                        .formatted(april2Style));
    }

    /**
     * Generate javascript functions to sort the table. In reality there exist a bunch of pre-sorted tables, and these
     * methods just change which table is visible.
     */
    private static void generateSortingFunctions(@NonNull final StringBuilder sb) {
        sb.append(
                """
                        <script>
                            function sortByName() {
                                document.getElementById('table_sortedByName').style.display = "block";
                                document.getElementById('table_sortedByAge').style.display = "none";
                                document.getElementById('table_sortedByStatus').style.display = "none";
                            }
                            function sortByAge() {
                                document.getElementById('table_sortedByName').style.display = "none";
                                document.getElementById('table_sortedByAge').style.display = "block";
                                document.getElementById('table_sortedByStatus').style.display = "none";
                            }
                            function sortByStatus() {
                                document.getElementById('table_sortedByName').style.display = "none";
                                document.getElementById('table_sortedByAge').style.display = "none";
                                document.getElementById('table_sortedByStatus').style.display = "block";
                            }
                            function showInTableIfOwnedBy(owner, tableId) {
                                var table = document.getElementById(tableId);
                                for (var i = 1, row; row = table.rows[i]; i++) {
                                    // owner is in column 2
                                    var rowOwner = row.cells[2].innerHTML;
                                    if (rowOwner == owner) {
                                        row.style.display = "table-row";
                                    } else {
                                        row.style.display = "none";
                                    }
                                }
                            }
                            function showIfOwnedBy(owner) {
                                showInTableIfOwnedBy(owner, 'table_sortedByName');
                                showInTableIfOwnedBy(owner, 'table_sortedByAge');
                                showInTableIfOwnedBy(owner, 'table_sortedByStatus')
                            }
                            function showAllRowsInTable(tableId) {
                                var table = document.getElementById(tableId);
                                for (var i = 1, row; row = table.rows[i]; i++) {
                                    row.style.display = "table-row";
                                }
                            }
                            function showAllRows() {
                                showAllRowsInTable('table_sortedByName');
                                showAllRowsInTable('table_sortedByAge');
                                showAllRowsInTable('table_sortedByStatus');
                            }
                        </script>
                        """);
    }

    private static void generateHeader(@NonNull final StringBuilder sb, @NonNull final Instant now) {
        sb.append("<head>\n");
        generateTitle(sb, now);
        generatePageStyle(sb);
        generateSortingFunctions(sb);
        sb.append("</head>\n");
    }

    private static void generatePanelCell(@NonNull final StringBuilder sb, @NonNull final String panelName) {
        sb.append("<td>").append(panelName).append("</td>\n");
    }

    private static void generateNameCell(@NonNull final StringBuilder sb, @NonNull final String testName) {
        sb.append("<td><b>").append(testName).append("</b></td>\n");
    }

    private static void generateOwnerCell(@NonNull final StringBuilder sb, @NonNull final String owner) {
        sb.append("<td>").append(owner).append("</td>\n");
    }

    private static void generateAgeCell(
            @NonNull final StringBuilder sb, @NonNull final Instant now, @NonNull final Instant testTime) {

        final Duration testAge = Duration.between(testTime, now);
        final String ageString = new UnitFormatter(testAge.toMillis(), UNIT_MILLISECONDS)
                .setAbbreviate(false)
                .render();

        sb.append("<td>").append(ageString).append(" ago</td>\n");
    }

    private static void generateStatusCell(@NonNull final StringBuilder sb, @NonNull final TestStatus status) {
        final String statusColor;
        if (status == TestStatus.PASS) {
            statusColor = "mediumSeaGreen";
        } else if (status == TestStatus.FAIL) {
            statusColor = "tomato";
        } else {
            statusColor = "slateBlue";
        }

        sb.append("<td bgcolor=\"")
                .append(statusColor)
                .append("\"><center>")
                .append(status.name())
                .append("</center></td>\n");
    }

    private static void generateHistoryCell(
            @NonNull final StringBuilder sb,
            @NonNull final List<JrsTestResult> historicalResults,
            @NonNull final String bucketPrefix,
            @NonNull final String bucketPrefixReplacement) {

        sb.append("<td>");

        // Always ignore the first result since it is already reported
        for (int index = 1; index < historicalResults.size(); index++) {

            final JrsTestResult result = historicalResults.get(index);

            final String testUrl = generateWebBrowserUrl(result.testDirectory(), bucketPrefix, bucketPrefixReplacement);
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

        sb.append("</td>\n");
    }

    private static void generateSummaryCell(@NonNull final StringBuilder sb, @NonNull final String testUrl) {
        sb.append("<td>");
        generateHyperlink(sb, "summary", testUrl + "summary.txt");
        sb.append("</td>\n");
    }

    private static void generateMetricsCell(@NonNull final StringBuilder sb, @NonNull final String testUrl) {
        sb.append("<td>");
        generateHyperlink(sb, "metrics", testUrl + "multipage_pdf.pdf");
        sb.append("</td>\n");
    }

    private static void generateDataCell(@NonNull final StringBuilder sb, @NonNull final String testUrl) {
        sb.append("<td>");
        generateHyperlink(sb, "data", testUrl);
        sb.append("</td>\n");
    }

    private static void generateNotesCell(@NonNull final StringBuilder sb, @NonNull final String notesUrl) {
        sb.append("<td>");
        if (!notesUrl.isBlank()) {
            generateHyperlink(sb, "notes", notesUrl);
        }
        sb.append("</td>\n");
    }

    private static void generateTableRow(
            @NonNull final StringBuilder sb,
            @NonNull final JrsTestReportRow row,
            @NonNull final Instant now,
            @NonNull final String bucketPrefix,
            @NonNull final String bucketPrefixReplacement) {

        final String testUrl =
                generateWebBrowserUrl(row.getMostRecentTest().testDirectory(), bucketPrefix, bucketPrefixReplacement);

        sb.append("<tr>\n");
        generatePanelCell(sb, row.getMostRecentTest().id().panel());
        generateNameCell(sb, row.getMostRecentTest().id().name());
        generateOwnerCell(sb, row.metadata() == null ? "" : row.metadata().owner());
        generateAgeCell(sb, now, row.getMostRecentTest().timestamp());
        generateStatusCell(sb, row.getMostRecentTest().status());
        generateHistoryCell(sb, row.tests(), bucketPrefix, bucketPrefixReplacement);
        generateSummaryCell(sb, testUrl);
        generateMetricsCell(sb, testUrl);
        generateDataCell(sb, testUrl);
        generateNotesCell(sb, row.metadata() == null ? "" : row.metadata().notesUrl());
        sb.append("</tr>\n");
    }

    private static void generateTable(
            @NonNull final StringBuilder sb,
            @NonNull final String tableId,
            @NonNull final List<JrsTestReportRow> rows,
            @NonNull final Instant now,
            @NonNull final String bucketPrefix,
            @NonNull final String bucketPrefixReplacement,
            @NonNull final Comparator<JrsTestReportRow> comparator,
            final boolean hidden) {

        rows.sort(comparator);

        sb.append(
                """
                        <table id="%s" style="display: %s" class="testDataTable">
                            <tr>
                                <th>Panel</th>
                                <th>Test Name</th>
                                <th>Owner</th>
                                <th>Age</th>
                                <th>Status</th>
                                <th>History</th>
                                <th>Summary</th>
                                <th>Metrics</th>
                                <th>Data</th>
                                <th>Notes</th>
                            </tr>
                         """
                        .formatted(tableId, hidden ? "none" : "block"));

        for (final JrsTestReportRow row : rows) {
            generateTableRow(sb, row, now, bucketPrefix, bucketPrefixReplacement);
        }

        sb.append("</table>\n");
    }

    /**
     * Used to sort tests by status.
     */
    private static int statusComparator(@NonNull final JrsTestReportRow a, @NonNull final JrsTestReportRow b) {

        final JrsTestResult aResult = a.getMostRecentTest();
        final JrsTestResult bResult = b.getMostRecentTest();

        if (aResult.status() != bResult.status()) {
            return Integer.compare(aResult.status().ordinal(), bResult.status().ordinal());
        }

        if (aResult.status() == PASS) {
            // For passing tests, give priority to the test with the most recent failure

            int mostRecentFailureA = Integer.MAX_VALUE;
            for (int index = 1; index < a.tests().size(); index++) {
                if (a.tests().get(index).status() == FAIL) {
                    mostRecentFailureA = index;
                    break;
                }
            }

            int mostRecentFailureB = Integer.MAX_VALUE;
            for (int index = 1; index < b.tests().size(); index++) {
                if (b.tests().get(index).status() != PASS) {
                    mostRecentFailureB = index;
                    break;
                }
            }

            if (mostRecentFailureA != mostRecentFailureB) {
                return Integer.compare(mostRecentFailureA, mostRecentFailureB);
            }
        } else {
            // For failing/unknown tests, give priority to the test with the most recent passing run

            int mostRecentPassA = Integer.MAX_VALUE;
            for (int index = 1; index < a.tests().size(); index++) {
                if (a.tests().get(index).status() == PASS) {
                    mostRecentPassA = index;
                    break;
                }
            }

            int mostRecentPassB = Integer.MAX_VALUE;
            for (int index = 1; index < b.tests().size(); index++) {
                if (b.tests().get(index).status() == PASS) {
                    mostRecentPassB = index;
                    break;
                }
            }

            if (mostRecentPassA != mostRecentPassB) {
                return Integer.compare(mostRecentPassA, mostRecentPassB);
            }
        }

        // If all else fails, sort by panel & name
        return aResult.id().compareTo(bResult.id());
    }

    /**
     * Used to sort tests by age.
     */
    private static int ageComparator(@NonNull final JrsTestReportRow a, @NonNull final JrsTestReportRow b) {
        if (!a.getMostRecentTest().timestamp().equals(b.getMostRecentTest().timestamp())) {
            return b.getMostRecentTest()
                    .timestamp()
                    .compareTo(a.getMostRecentTest().timestamp());
        }
        // If timestamps are equal, sort by panel & name.
        return a.getMostRecentTest().id().compareTo(b.getMostRecentTest().id());
    }

    private static void generateDataTable(
            @NonNull final StringBuilder sb,
            @NonNull final JrsReportData data,
            @NonNull final Map<JrsTestIdentifier, JrsTestMetadata> metadata,
            @NonNull final Instant now,
            @NonNull final String bucketPrefix,
            @NonNull final String bucketPrefixReplacement) {

        final List<JrsTestReportRow> rows = JrsTestReportGenerator.buildTableRows(data.testResults(), metadata);

        // Only one table will be visible at a time, the others will be hidden.
        // I'm too lazy to write javascript to sort these, so I just sort them in java
        // and have the page display the table with the desired sort order.
        generateTable(
                sb,
                "table_sortedByName",
                rows,
                now,
                bucketPrefix,
                bucketPrefixReplacement,
                Comparator.comparing(a -> a.getMostRecentTest().id()),
                false);
        generateTable(
                sb,
                "table_sortedByAge",
                rows,
                now,
                bucketPrefix,
                bucketPrefixReplacement,
                JrsTestReportGenerator::ageComparator,
                true);
        generateTable(
                sb,
                "table_sortedByStatus",
                rows,
                now,
                bucketPrefix,
                bucketPrefixReplacement,
                JrsTestReportGenerator::statusComparator,
                true);
    }

    private record TestCount(int uniqueTests, int passingTests, int failingTests, int unknownTests) {}

    @NonNull
    private static TestCount countTests(@NonNull final List<JrsTestResult> results) {

        // Sort tests by unique type.
        final Map<JrsTestIdentifier, List<JrsTestResult>> resultsByTestType = new HashMap<>();
        for (final JrsTestResult result : results) {
            final JrsTestIdentifier id = result.id();
            final List<JrsTestResult> resultsForType = resultsByTestType.computeIfAbsent(id, k -> new ArrayList<>());
            resultsForType.add(result);
        }

        // Sort each test of the same type by timestamp.
        for (final List<JrsTestResult> tests : resultsByTestType.values()) {
            Collections.sort(tests);
        }

        final int uniqueTests = resultsByTestType.size();
        int passingTests = 0;
        int failingTests = 0;
        int unknownTests = 0;
        for (final JrsTestIdentifier testIdentifier : resultsByTestType.keySet()) {
            final List<JrsTestResult> testResults = resultsByTestType.get(testIdentifier);
            final JrsTestResult mostRecentResult = testResults.get(0);
            if (mostRecentResult.status() == PASS) {
                passingTests++;
            } else if (mostRecentResult.status() == FAIL) {
                failingTests++;
            } else {
                unknownTests++;
            }
        }

        return new TestCount(uniqueTests, passingTests, failingTests, unknownTests);
    }

    @NonNull
    private static String generateEmotion(final int passPercentage) {
        if (passPercentage == 100) {
            return "ᕙ(⇀‸↼‶)ᕗ";
        } else if (passPercentage >= 95) {
            return "(⌐■_■)";
        } else if (passPercentage >= 90) {
            return "ಠ⌣ಠ";
        } else if (passPercentage >= 80) {
            return "ಠ_ಠ";
        } else if (passPercentage >= 70) {
            return "(ಥ_ಥ)";
        } else if (passPercentage >= 60) {
            return "(ಥ﹏ಥ)";
        } else if (passPercentage >= 50) {
            return "ლ(ಠ益ಠლ)";
        } else if (passPercentage >= 40) {
            return "(╯°□°）╯︵ ┻━┻";
        } else if (passPercentage >= 30) {
            return "(ノಠ益ಠ)ノ彡┻━┻";
        } else if (passPercentage >= 20) {
            return "┻━┻ ︵ヽ(`Д´)ﾉ︵ ┻━┻";
        } else {
            return "(︺︹︺)";
        }
    }

    private static void generateSummary(@NonNull final StringBuilder sb, @NonNull final JrsReportData data) {

        final LocalDate localDate =
                data.reportTime().atZone(ZoneId.systemDefault()).toLocalDate();
        final LocalTime localTime =
                data.reportTime().atZone(ZoneId.systemDefault()).toLocalTime();
        final ZonedDateTime zonedDateTime = ZonedDateTime.of(localDate, localTime, ZoneId.systemDefault());

        final String date =
                DateTimeFormatter.ofLocalizedDateTime(FormatStyle.FULL).format(zonedDateTime);
        final String[] dateParts = date.split(" at ");

        final TestCount testCount = countTests(data.testResults());
        final int percentPassing = (int) (100.0 * testCount.passingTests() / testCount.uniqueTests());

        sb.append(
                """
                        <table class="summaryTable">
                            <tr>
                                <td><b>Directory</b></td>
                                <td>%s</td>
                            </tr>
                            <tr>
                                <td><b>Date</b></td>
                                <td>%s</td>
                            </tr>
                            <tr>
                                <td><b>Time</b></td>
                                <td>%s</td>
                            </tr>
                            <tr>
                                <td><b>Span</b></td>
                                <td>%s days</td>
                            </tr>
                            <tr>
                                <td><b>Pass Rate</b></td>
                                <td>%s%% %s</td>
                            </tr>
                            <tr>
                                <td><b>Total</b></td>
                                <td>%s</td>
                            </tr>
                            <tr>
                                <td><b>Passing</b></td>
                                <td>%s</td>
                            </tr>
                            <tr>
                                <td><b>Failing</b></td>
                                <td>%s</td>
                            </tr>
                            <tr>
                                <td><b>Unknown</b></td>
                                <td>%s</td>
                            </tr>
                        </table>
                        """
                        .formatted(
                                data.directory(),
                                dateParts[0],
                                dateParts[1],
                                data.reportSpan(),
                                percentPassing,
                                generateEmotion(percentPassing),
                                testCount.uniqueTests(),
                                testCount.passingTests(),
                                testCount.failingTests(),
                                testCount.unknownTests()));
    }

    private static void generateOrderControls(@NonNull final StringBuilder sb) {
        sb.append(
                """
                        <center><h1><b>Ordering</b></h1></center><br>
                        <form autocomplete="off">
                            <input type="radio" name="order" onclick="sortByName()" checked> name<br>
                            <input type="radio" name="order" onclick="sortByAge()"> age<br>
                            <input type="radio" name="order" onclick="sortByStatus()"> status<br>
                        </form>
                        """);
    }

    private static void generateOwnerControls(@NonNull final StringBuilder sb) {
        sb.append("<center><h1><b>Owner</b></h1></center><br>\n");
        sb.append("<p>Note: this feature is a WIP and is not currently functional.</p>\n");
        sb.append("""
                <form autocomplete="off">
                """);
        sb.append(
                """
                <input type="radio" name="order" value="name" onclick="showAllRows()" checked> all (163)<br>
                """);
        sb.append(
                """
                <input type="radio" name="order" value="age" onclick="showIfOwnedBy('')"> unassigned (0)<br>
                """);
        sb.append(
                """
                <input type="radio" name="order" value="status" onclick="showIfOwnedBy('platform')"> platform (50)<br>
                """);
        sb.append(
                """
                <input type="radio" name="order" value="status" onclick="showIfOwnedBy('services')"> services (113)<br>
                """);
        sb.append("</form>\n");
    }

    private static void generateSidePanel(@NonNull final StringBuilder sb, @NonNull final JrsReportData data) {
        sb.append("<div class=\"sidePanel\">\n");
        generateSummary(sb, data);
        sb.append("<hr>");
        generateOrderControls(sb);
        sb.append("<hr>");
        generateOwnerControls(sb);
        sb.append("</div>\n");
    }

    private static void generateBody(
            @NonNull final StringBuilder sb,
            @NonNull final JrsReportData data,
            @NonNull final Map<JrsTestIdentifier, JrsTestMetadata> metadata,
            @NonNull final Instant now,
            @NonNull final String bucketPrefix,
            @NonNull final String bucketPrefixReplacement) {

        sb.append(
                """
                        <center>
                        <table class="topLevelTable">
                        <tr>
                        <td style="vertical-align: top;">
                        """);
        generateDataTable(sb, data, metadata, now, bucketPrefix, bucketPrefixReplacement);
        sb.append("""
                </td>
                <td style="vertical-align: top;">
                """);
        generateSidePanel(sb, data);
        sb.append(
                """
                        </td>
                        </tr>
                        </table>
                        </center>
                        """);
    }

    private static void generatePage(
            @NonNull final StringBuilder sb,
            @NonNull final JrsReportData data,
            @NonNull final Map<JrsTestIdentifier, JrsTestMetadata> metadata,
            @NonNull final Instant now,
            @NonNull final String bucketPrefix,
            @NonNull final String bucketPrefixReplacement) {
        sb.append("<!DOCTYPE html>\n");
        sb.append("<html>\n");
        generateHeader(sb, now);
        generateBody(sb, data, metadata, now, bucketPrefix, bucketPrefixReplacement);
        sb.append("</body>\n");
        sb.append("</html>\n");
    }

    /**
     * Print some warnings if we are missing metadata or if we have metadata for tests that were not discovered.
     *
     * @param tests    all tests discovered by this utility
     * @param metadata test metadata, by test
     */
    public static void validateMetadata(
            @NonNull final Set<JrsTestIdentifier> tests,
            @NonNull final Map<JrsTestIdentifier, JrsTestMetadata> metadata) {

        final Set<JrsTestIdentifier> unassignedMetadata = new HashSet<>(metadata.keySet());
        final List<JrsTestIdentifier> testsWithoutMetadata = new ArrayList<>();

        for (final JrsTestIdentifier test : tests) {
            final boolean noteFound = unassignedMetadata.remove(test);

            if (!noteFound) {
                testsWithoutMetadata.add(test);
            }
        }

        if (!testsWithoutMetadata.isEmpty()) {
            final StringBuilder sb = new StringBuilder();
            sb.append("The following test(s) do not have metadata:\n");
            for (final JrsTestIdentifier test : testsWithoutMetadata) {
                sb.append("  - ")
                        .append(test.panel())
                        .append(": ")
                        .append(test.name())
                        .append("\n");
            }
            System.out.println(sb);
        }
        if (!unassignedMetadata.isEmpty()) {
            final StringBuilder sb = new StringBuilder();
            sb.append("There is metadata defined for the following test(s), "
                    + "but these test(s) were not discovered during scan:\n");
            for (final JrsTestIdentifier test : unassignedMetadata) {
                sb.append("  - ")
                        .append(test.panel())
                        .append(": ")
                        .append(test.name())
                        .append("\n");
            }
            System.out.println(sb);
        }
    }

    @NonNull
    private static List<JrsTestReportRow> buildTableRows(
            @NonNull final List<JrsTestResult> results,
            @NonNull final Map<JrsTestIdentifier, JrsTestMetadata> metadata) {

        // Sort tests by unique type.
        final Map<JrsTestIdentifier, List<JrsTestResult>> resultsByTestType = new HashMap<>();
        for (final JrsTestResult result : results) {
            final JrsTestIdentifier id = result.id();
            final List<JrsTestResult> resultsForType = resultsByTestType.computeIfAbsent(id, k -> new ArrayList<>());
            resultsForType.add(result);
        }

        // Sort each test of the same type by timestamp.
        for (final List<JrsTestResult> tests : resultsByTestType.values()) {
            Collections.sort(tests);
        }

        final List<JrsTestReportRow> rows = new ArrayList<>();
        for (final JrsTestIdentifier testType : resultsByTestType.keySet()) {
            rows.add(new JrsTestReportRow(resultsByTestType.get(testType), metadata.getOrDefault(testType, null)));
        }

        validateMetadata(resultsByTestType.keySet(), metadata);

        return rows;
    }

    public static void generateReport(
            @NonNull final JrsReportData data,
            @NonNull final Map<JrsTestIdentifier, JrsTestMetadata> metadata,
            @NonNull final Instant now,
            @NonNull final String bucketPrefix,
            @NonNull final String bucketPrefixReplacement,
            @NonNull final Path outputFile) {

        final StringBuilder sb = new StringBuilder();
        generatePage(sb, data, metadata, now, bucketPrefix, bucketPrefixReplacement);

        final String reportString = sb.toString();
        try {
            Files.write(outputFile, reportString.getBytes());
        } catch (final IOException e) {
            throw new UncheckedIOException("unable to generate test report", e);
        }
    }
}
