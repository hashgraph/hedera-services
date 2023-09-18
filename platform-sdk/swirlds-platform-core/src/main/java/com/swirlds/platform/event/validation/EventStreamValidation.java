package com.swirlds.platform.event.validation;

import edu.umd.cs.findbugs.annotations.NonNull;
import java.nio.file.Path;
import java.util.Objects;

/**
 * Utilities for validating CES and PCES files.
 */
public final class EventStreamValidation {

    private EventStreamValidation() {

    }


    /**
     * Validate the CES and PCES files in the given directories.
     *
     * @param stateDirectory                   the root of the directory tree where state files are saved.
     * @param consensusEventStreamDirectory    the directory where CES files can be found.
     * @param preconsensusEventStreamDirectory the root of the directory tree where PCES files can be found.
     * @param permittedFileBreaks              the permitted number of breaks in the file (usually just the number of
     *                                         reconnects).
     * @return true if the files are valid, false otherwise.
     */
    public static boolean validateStreams(
            @NonNull final Path stateDirectory,
            @NonNull final Path consensusEventStreamDirectory,
            @NonNull final Path preconsensusEventStreamDirectory,
            final int permittedFileBreaks) {

        Objects.requireNonNull(stateDirectory);
        Objects.requireNonNull(consensusEventStreamDirectory);
        Objects.requireNonNull(preconsensusEventStreamDirectory);

        return false;
    }


    /**
     * Validate a single CES file for internal consistency.
     *
     * @param file the file to validate
     * @return true if the file is valid, false otherwise.
     */
    public static boolean validateConsensusEventStreamFile(@NonNull final ConsensusEventStreamFileContents file) {
        // TODO recompute running hash
        // TODO verify signature
        // TODO verify topological ordering
        // TODO verify consensus order
        // TODO verify consensus timestamps (between events and against file timestamp)
        // TODO verify event signatures
        return false;
    }

    /**
     * Validate that two CES files are linked together correctly.
     *
     * @param firstFile  a CES file
     * @param secondFile the following CES file
     * @return true if the files are linked correctly, false otherwise.
     */
    public static boolean verifyConsensusEventFileLinkage(
            @NonNull final ConsensusEventStreamFileContents firstFile,
            @NonNull final ConsensusEventStreamFileContents secondFile) {

        // TODO verify hash
        // TODO verify consensus order between first and last event
        // TODO verify consensus timestamps between first and last event
        // TODO verify that a new file should have been started

        return false;
    }

}
