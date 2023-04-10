package com.swirlds.demo.consistency;

import static com.swirlds.common.utility.ByteUtils.byteArrayToLong;

import com.swirlds.common.system.Round;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Path;

/**
 * Utility class for writing to the state log file
 * <p>
 * The format of the log file is as follows:
 * <ul>
 *     <li>Each line of the file represents a round which came to consensus</li>
 *     <li>Each line is of the form: "Round: {round number}; Transactions: [{list of transaction long contents}]"</li>
 * </ul>
 */
public final class StateLogWriter {
    /**
     * Hidden constructor
     */
    private StateLogWriter() {
    }

    /**
     * Creates a string representation of the given round
     *
     * @param round the round to convert to a string
     * @return a string representation of the given round
     */
    private static String roundToString(final @NonNull Round round) {
        final StringBuilder builder = new StringBuilder();

        builder.append("Round: ");
        builder.append(round.getRoundNum());
        builder.append("; Transactions: [");

        round.forEachTransaction(transaction -> {
            builder.append(byteArrayToLong(transaction.getContents(), 0));
            builder.append(", ");
        });

        builder.append("]\n");

        return builder.toString();
    }

    /**
     * Writes the given round to the log file
     *
     * @param round the round to write to the log file
     */
    public static synchronized void writeRoundStateToLog(final @NonNull Round round) {
        final Path path = Path.of(ConsistencyTestingToolUtils.getLogFileName());

        try (BufferedWriter file = new BufferedWriter(new FileWriter(path.toFile(), true))) {
            file.write(roundToString(round));
        } catch (final IOException e) {
            e.printStackTrace();
        }
    }

}
