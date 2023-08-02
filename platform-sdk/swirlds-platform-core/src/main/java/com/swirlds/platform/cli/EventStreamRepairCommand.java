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

import static com.swirlds.platform.recovery.events.EventStreamSingleFileRepairer.DAMAGED_SUFFIX;
import static com.swirlds.platform.util.BootstrapUtils.setupConstructableRegistry;

import com.swirlds.cli.commands.EventStreamCommand;
import com.swirlds.cli.utility.AbstractCommand;
import com.swirlds.cli.utility.SubcommandOf;
import com.swirlds.platform.recovery.events.EventStreamSingleFileRepairer;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.NoSuchElementException;
import java.util.stream.Stream;
import picocli.CommandLine;

@CommandLine.Command(
        name = "repair",
        mixinStandardHelpOptions = true,
        description = "Repair an event stream if it has been truncated.")
@SubcommandOf(EventStreamCommand.class)
public class EventStreamRepairCommand extends AbstractCommand {
    private File eventStreamFile;

    @CommandLine.Parameters(
            description = "The path to the event stream file or directory of event files needing repair.")
    private void setEventStreamFileOrDirectory(final Path eventStreamFileOrDirectory) {
        if (Files.isDirectory(eventStreamFileOrDirectory)) {
            try (final Stream<Path> pathStream = Files.walk(eventStreamFileOrDirectory)) {
                final Path pathToLastFile = pathStream
                        .filter(path -> path.toString().endsWith(".evts"))
                        .reduce(((path, path2) -> path2.compareTo(path) < 0 ? path : path2))
                        .orElseThrow();
                this.eventStreamFile = fileMustExist(pathToLastFile);
            } catch (final IOException | NoSuchElementException e) {
                System.err.println("Failed to find event stream from Path: " + eventStreamFileOrDirectory);
                e.printStackTrace(System.err);
            }
        } else {
            this.eventStreamFile = fileMustExist(eventStreamFileOrDirectory);
        }
    }

    @Override
    public Integer call() throws Exception {
        setupConstructableRegistry();
        final EventStreamSingleFileRepairer repairer = new EventStreamSingleFileRepairer(eventStreamFile);
        if (repairer.repair()) {
            System.out.println("Event Stream Repaired.");
            System.out.println("Damaged file: " + eventStreamFile.getAbsolutePath() + DAMAGED_SUFFIX);
            System.out.println("Repaired file: " + eventStreamFile.getAbsolutePath());
        } else {
            System.out.println("Event Stream Did Not Need Repair.");
            System.out.println("Evaluated file: " + eventStreamFile.getAbsolutePath() + DAMAGED_SUFFIX);
        }
        System.out.println("File event count: " + repairer.getEventCount());
        return 0;
    }
}
