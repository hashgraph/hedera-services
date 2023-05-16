/*
 * Copyright (C) 2016-2023 Hedera Hashgraph, LLC
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

package com.swirlds.platform.cli;

import static com.swirlds.platform.util.BootstrapUtils.setupConstructableRegistry;

import com.swirlds.cli.commands.EventStreamCommand;
import com.swirlds.cli.utility.AbstractCommand;
import com.swirlds.cli.utility.SubcommandOf;
import com.swirlds.platform.event.report.EventStreamMultiNodeReport;
import com.swirlds.platform.event.report.EventStreamReport;
import com.swirlds.platform.event.report.EventStreamScanner;
import com.swirlds.platform.recovery.internal.EventStreamLowerBound;
import com.swirlds.platform.recovery.internal.EventStreamRoundLowerBound;
import com.swirlds.platform.recovery.internal.EventStreamTimestampLowerBound;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Objects;
import picocli.CommandLine;

@CommandLine.Command(
        name = "info",
        mixinStandardHelpOptions = true,
        description = "Read event stream files and print an informational report.")
@SubcommandOf(EventStreamCommand.class)
public final class EventStreamInfoCommand extends AbstractCommand {

    /** a format for timestamps */
    private static final String TIMESTAMP_FORMAT = "yyyy-MM-dd HH:mm:ss";

    /** a formatter for timestamps */
    private static final DateTimeFormatter formatter = DateTimeFormatter.ofPattern(TIMESTAMP_FORMAT);

    /** the directory containing the event stream files */
    private Path eventStreamDirectory;

    /** the timestamp of the lower bound */
    private Instant timestampBound = Instant.MIN;

    /** the round of the lower bound */
    private long roundBound = -1;

    /** the default temporal granularity of the data report */
    private Duration granularity = Duration.ofSeconds(10);

    /**
     * If true, a separate report will be generated for each child directory contained in the specified parent.
     * <p>
     * Each individual report will be written to a file in the respective child directory
     * <p>
     * In addition to individual reports, a summary report will be generated and printed to stdout.
     */
    private boolean multiNodeReport = false;

    @CommandLine.Parameters(description = "The path to a directory tree containing event stream files.")
    private void setEventStreamDirectory(final Path eventStreamDirectory) {
        this.eventStreamDirectory = pathMustExist(eventStreamDirectory.toAbsolutePath());
    }

    @CommandLine.Option(
            names = {"-f", "--first-round"},
            description = "The first round to be considered in the event stream.")
    private void setFirstRound(final long firstRound) {
        roundBound = firstRound;
    }

    @CommandLine.Option(
            names = {"-t", "--timestamp"},
            description = "The minimum timestamp to be considered in the event stream. The format is \""
                    + TIMESTAMP_FORMAT + "\".")
    private void setTimestamp(@NonNull final String timestamp) {
        Objects.requireNonNull(timestamp, "timestamp must not be null");
        try {
            // the format used by log4j2
            timestampBound = formatter.parse(timestamp, Instant::from);
        } catch (final DateTimeParseException e) {
            // the format used by Instant.toString()
            timestampBound = Instant.parse(timestamp);
        }
    }

    @CommandLine.Option(
            names = {"-g", "--granularity"},
            description = "The temporal granularity of the data report, in seconds.")
    private void setGranularityInSeconds(final long granularity) {
        if (granularity < 1) {
            throw buildParameterException("Granularity must be strictly greater than 1");
        }
        this.granularity = Duration.ofSeconds(granularity);
    }

    @CommandLine.Option(
            names = {"-m", "--multi-node-report"},
            description =
                    "Generate a separate report for each direct child of the specified directory, as well as a summary report.")
    private void requestMultiNodeReport(final boolean multiNodeReport) {
        this.multiNodeReport = multiNodeReport;
    }

    private EventStreamInfoCommand() {}

    private EventStreamReport getReport(@NonNull final Path directory, @NonNull final EventStreamLowerBound bound) {
        Objects.requireNonNull(directory);
        Objects.requireNonNull(bound);

        try {
            return new EventStreamScanner(directory, bound, granularity, true).createReport();
        } catch (final IOException e) {
            throw new UncheckedIOException("Failed to generate event stream report", e);
        }
    }

    private void writeNodeReportToFile(@NonNull final Path nodeDirectory, @NonNull final EventStreamReport nodeReport) {
        final Path reportFile = nodeDirectory.resolve("event-stream-report.txt");

        try {
            Files.writeString(reportFile, nodeReport.toString());
        } catch (final IOException e) {
            throw new UncheckedIOException("Failed to write report to file", e);
        }
    }

    private void generateMultiNodeReport(@NonNull final EventStreamLowerBound bound) throws IOException {
        Objects.requireNonNull(bound);

        final EventStreamMultiNodeReport multiReport = new EventStreamMultiNodeReport(granularity, bound);

        try (final DirectoryStream<Path> stream = Files.newDirectoryStream(eventStreamDirectory)) {
            stream.forEach(streamElement -> {
                // child elements that aren't directories are ignored
                if (!Files.isDirectory(streamElement)) {
                    return;
                }

                final EventStreamReport individualReport = getReport(streamElement, bound);

                multiReport.addIndividualReport(streamElement, individualReport);
                writeNodeReportToFile(streamElement, individualReport);
            });
        }

        System.out.println(multiReport);
    }

    @Override
    public Integer call() throws Exception {
        setupConstructableRegistry();
        if (roundBound > 0 && !Instant.MIN.equals(timestampBound)) {
            throw buildParameterException("Cannot set both round and timestamp");
        }

        final EventStreamLowerBound bound;
        if (roundBound > 0) {
            bound = new EventStreamRoundLowerBound(roundBound);
        } else if (!Instant.MIN.equals(timestampBound)) {
            bound = new EventStreamTimestampLowerBound(timestampBound);
        } else {
            bound = EventStreamLowerBound.UNBOUNDED;
        }

        if (multiNodeReport) {
            generateMultiNodeReport(bound);
        } else {
            // an individual report has been requested. Simply print the report to stdout.
            System.out.println(getReport(eventStreamDirectory, bound));
        }
        return 0;
    }
}
