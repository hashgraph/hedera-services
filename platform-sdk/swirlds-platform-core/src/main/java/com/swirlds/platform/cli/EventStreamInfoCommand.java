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
import com.swirlds.platform.event.report.EventStreamScanner;
import com.swirlds.platform.recovery.internal.EventStreamBound;
import com.swirlds.platform.recovery.internal.EventStreamBound.BoundBuilder;
import edu.umd.cs.findbugs.annotations.NonNull;
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

    /** the builder for the event stream bound */
    private final BoundBuilder boundBuilder = EventStreamBound.create();

    /** the default temporal granularity of the data report, in seconds */
    private long granularityInSeconds = 10;

    @CommandLine.Parameters(description = "The path to a directory tree containing event stream files.")
    private void setEventStreamDirectory(final Path eventStreamDirectory) {
        this.eventStreamDirectory = pathMustExist(eventStreamDirectory.toAbsolutePath());
    }

    @CommandLine.Option(
            names = {"-f", "--first-round"},
            description = "The first round to be considered in the event stream.")
    private void setFirstRound(final long firstRound) {
        boundBuilder.setRound(firstRound);
    }

    @CommandLine.Option(
            names = {"-t", "--timestamp"},
            description = "The minimum timestamp to be considered in the event stream. The format is \""
                    + TIMESTAMP_FORMAT + "\".")
    private void setTimestamp(@NonNull final String timestamp) {
        Objects.requireNonNull(timestamp, "timestamp must not be null");
        try {
            // the format used by log4j2
            boundBuilder.setTimestamp(formatter.parse(timestamp, Instant::from));
        } catch (final DateTimeParseException e) {
            // the format used by Instant.toString()
            boundBuilder.setTimestamp(Instant.parse(timestamp));
        }
    }

    @CommandLine.Option(
            names = {"-g", "--granularity"},
            description = "The temporal granularity of the data report, in seconds.")
    private void setGranularityInSeconds(final long granularityInSeconds) {
        if (granularityInSeconds < 1) {
            throw buildParameterException("Granularity must be strictly greater than 1");
        }
        this.granularityInSeconds = granularityInSeconds;
    }

    private EventStreamInfoCommand() {}

    @Override
    public Integer call() throws Exception {
        setupConstructableRegistry();
        final EventStreamBound bound = boundBuilder.build();
        if (bound.hasRound() && bound.hasTimestamp()) {
            throw buildParameterException("Cannot set both round and timestamp");
        }
        System.out.println(
                new EventStreamScanner(eventStreamDirectory, bound, Duration.ofSeconds(granularityInSeconds), true)
                        .createReport());
        return 0;
    }
}
