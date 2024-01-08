/*
 * Copyright (C) 2023-2024 Hedera Hashgraph, LLC
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

package com.hedera.services.cli.contracts.assembly;

import static com.hedera.services.cli.contracts.assembly.Constants.MAX_EXPECTED_LINE;
import static com.hedera.services.cli.contracts.assembly.Constants.UPPERCASE_HEX_FORMATTER;

import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.HexFormat;
import java.util.Objects;

public interface Line {

    /** Formats the line into a string suitable for directly putting in the disassembly */
    void formatLine(@NonNull final StringBuilder sb);

    /**
     * Formats the line into a string suitable for directly putting in the disassembly
     *
     * <p>Convenience method so that holders of Lines don't have to create their own
     * StringBuilder
     */
    default @NonNull String formatLine() {
        var sb = new StringBuilder(MAX_EXPECTED_LINE);
        formatLine(sb);
        return sb.toString();
    }

    /**
     * Extend a string until it reaches the goal column
     *
     * <p>Utility function useful for lining up fields (opcode, mnemonic, comment, etc.) into
     * columns.
     */
    default void extendWithBlanksTo(@NonNull final StringBuilder sb, int goalColumn) {
        Objects.requireNonNull(sb);
        if (goalColumn <= sb.length()) {
            // Already past the goal column ... just pad with a couple of blanks
            sb.append("  ");
            return;
        }

        // Need to pad to goal column - keep sticking blanks on to the end until past
        // the goal, then truncate to correct size
        while (sb.length() < goalColumn) {
            sb.append("                ");
        }
        sb.setLength(goalColumn);
    }

    /**
     * Check if this Line is a specific instruction opcodes
     *
     * <p>Convenience method for check to see if this line is an instruction with the given
     * opcode.
     */
    default boolean thisLineIsA(int opcode) {
        return this instanceof CodeLine codeLine && codeLine.opcode() == opcode;
    }

    default boolean thisLineIsA(@NonNull final String mnemonic) {
        Objects.requireNonNull(mnemonic);
        return this instanceof CodeLine codeLine && codeLine.isA(mnemonic);
    }

    /** Make a HexFormatter available to all Lines */
    default @NonNull HexFormat hexer() {
        return UPPERCASE_HEX_FORMATTER;
    }
}
