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

package com.swirlds.platform.cli;

import static com.swirlds.logging.LogMarker.EXCEPTION;

import com.swirlds.cli.commands.EventStreamCommand;
import com.swirlds.cli.utility.AbstractCommand;
import com.swirlds.cli.utility.SubcommandOf;
import com.swirlds.common.crypto.CryptographyHolder;
import com.swirlds.platform.event.stream.validation.EventStreamValidation;
import com.swirlds.platform.util.BootstrapUtils;
import java.nio.file.Path;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import picocli.CommandLine;

/**
 * A utility for validating the data integrity of event stream files (both CES and PCES).
 */
@CommandLine.Command(name = "validate", mixinStandardHelpOptions = true, description = "Validate event stream files")
@SubcommandOf(EventStreamCommand.class)
public class EventStreamValidatorCommand extends AbstractCommand {

    private static final Logger logger = LogManager.getLogger(EventStreamValidatorCommand.class);

    private Path stateDirectory;

    @CommandLine.Parameters(
            index = "0",
            description = "The root of the directory tree where state files are saved. Typically 'data/saved'.")
    private void setStateDirectory(final Path stateDirectory) {
        this.stateDirectory = this.pathMustExist(stateDirectory);
    }

    private Path consensusEventStreamDirectory;

    @CommandLine.Parameters(index = "1", description = "The directory where CES files can be found.")
    private void setConsensusEventStreamDirectory(final Path consensusEventStreamDirectory) {
        this.consensusEventStreamDirectory = this.pathMustExist(consensusEventStreamDirectory);
    }

    private Path preconsensusEventStreamDirectory;

    @CommandLine.Parameters(index = "2", description = "The root of the directory tree where PCES files can be found.")
    private void setPreconsensusEventStreamDirectory(final Path preconsensusEventStreamDirectory) {
        this.preconsensusEventStreamDirectory = this.pathMustExist(preconsensusEventStreamDirectory);
    }

    private int permittedFileBreaks = 0;

    @CommandLine.Option(
            names = {"-b", "--breaks"},
            description = "the permitted number of breaks in the file (usually just the number of reconnects).")
    private void setPermittedFileBreaks(final int permittedFileBreaks) {
        if (permittedFileBreaks < 0) {
            throw buildParameterException("permittedFileBreaks must be positive or zero");
        }
        this.permittedFileBreaks = permittedFileBreaks;
    }

    /**
     * Run the command.
     */
    @Override
    public Integer call() throws Exception {
        try {
            BootstrapUtils.setupConstructableRegistry();
            EventStreamValidation.validateStreams(
                    CryptographyHolder.get(),
                    stateDirectory,
                    consensusEventStreamDirectory,
                    preconsensusEventStreamDirectory,
                    permittedFileBreaks);
        } catch (final Throwable t) {
            logger.error(EXCEPTION.getMarker(), "event streams failed validation", t);
            return 1;
        }

        return 0;
    }
}
