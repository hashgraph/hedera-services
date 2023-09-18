package com.swirlds.platform.cli;

import com.swirlds.cli.commands.EventStreamCommand;
import com.swirlds.cli.utility.AbstractCommand;
import com.swirlds.cli.utility.SubcommandOf;
import com.swirlds.platform.event.validation.EventStreamValidation;
import java.nio.file.Path;
import picocli.CommandLine;

/**
 * A utility for validating the data integrity of event stream files (both CES and PCES).
 */
@CommandLine.Command(name = "validate", mixinStandardHelpOptions = true, description = "Validate event stream files")
@SubcommandOf(EventStreamCommand.class)
public class EventStreamValidator extends AbstractCommand {

    private Path stateDirectory;
    @CommandLine.Parameters(
            index = "0",
            description = "The root of the directory tree where state files are saved. Typically 'data/saved'.")
    private void setStateDirectory(final Path stateDirectory) {
        this.stateDirectory = this.pathMustExist(stateDirectory);
    }

    private Path consensusEventStreamDirectory;
    @CommandLine.Parameters(
            index = "1",
            description = "The directory where CES files can be found.")
    private void setConsensusEventStreamDirectory(final Path consensusEventStreamDirectory) {
        this.consensusEventStreamDirectory = this.pathMustExist(consensusEventStreamDirectory);
    }


    private Path preconsensusEventStreamDirectory;
    @CommandLine.Parameters(
            index = "2",
            description = "The root of the directory tree where PCES files can be found.")
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

        // TODO what belongs here?

        final boolean valid = EventStreamValidation.validateStreams(
                stateDirectory,
                consensusEventStreamDirectory,
                preconsensusEventStreamDirectory,
                permittedFileBreaks);

        return valid ? 0 : 1;
    }
}
