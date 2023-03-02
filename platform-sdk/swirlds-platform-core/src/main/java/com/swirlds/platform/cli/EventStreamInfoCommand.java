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
import java.nio.file.Path;
import java.time.Duration;
import picocli.CommandLine;

@CommandLine.Command(
        name = "info",
        mixinStandardHelpOptions = true,
        description = "Read event stream files and print an informational report.")
@SubcommandOf(EventStreamCommand.class)
public final class EventStreamInfoCommand extends AbstractCommand {

    private Path eventStreamDirectory;

    private long firstRound = -1;

    private long granularityInSeconds = 10;

    @CommandLine.Parameters(description = "The path to a directory tree containing event stream files.")
    private void setEventStreamDirectory(final Path eventStreamDirectory) {
        this.eventStreamDirectory = pathMustExist(eventStreamDirectory.toAbsolutePath());
    }

    @CommandLine.Option(
            names = {"-f", "--first-round"},
            description = "The first to be considered.")
    private void setFirstRound(final long firstRound) {
        this.firstRound = firstRound;
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
        System.out.println(
                new EventStreamScanner(eventStreamDirectory, firstRound, Duration.ofSeconds(granularityInSeconds), true)
                        .createReport());
        return 0;
    }
}
