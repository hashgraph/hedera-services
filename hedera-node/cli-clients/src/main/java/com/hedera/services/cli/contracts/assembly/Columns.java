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

import static com.hedera.services.cli.contracts.assembly.Constants.DEFAULT_CODE_OFFSET_COLUMN;
import static com.hedera.services.cli.contracts.assembly.Constants.DEFAULT_COMMENT_COLUMN;
import static com.hedera.services.cli.contracts.assembly.Constants.DEFAULT_EOL_COMMENT_COLUMN;
import static com.hedera.services.cli.contracts.assembly.Constants.DEFAULT_LABEL_COLUMN;
import static com.hedera.services.cli.contracts.assembly.Constants.DEFAULT_MNEMONIC_COLUMN;
import static com.hedera.services.cli.contracts.assembly.Constants.DEFAULT_OPCODE_COLUMN;
import static com.hedera.services.cli.contracts.assembly.Constants.DEFAULT_OPERAND_COLUMN;

import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Objects;

/**
 * Named enums for each "column" in the disassembly output, holding their column location
 *
 * <p>Each "column" in the resulting disassembly has its own enum value, which holds its column
 * position. But the column locations depend on which variants are chosen. E.g., you may or may
 * not be formatting with the code offset present. Thus there is a mechanism where the columns
 * are given default values for the vanilla disassembly, and as options are selected which _add_
 * a column to the disassembly the other columns to its right are moved over. For this purpose
 * columns are "grouped".
 *
 * YUCK: This use of an enum, as singletons, to hold globals is probably not as justifiable as
 * `DisassemblyOption`.  Do something better.
 */
public enum Columns {
    CODE_OFFSET(0, DEFAULT_CODE_OFFSET_COLUMN),
    OPCODE(1, DEFAULT_OPCODE_COLUMN),
    COMMENT(2, DEFAULT_COMMENT_COLUMN),
    LABEL(2, DEFAULT_LABEL_COLUMN),
    MNEMONIC(2, DEFAULT_MNEMONIC_COLUMN),
    OPERAND(2, DEFAULT_OPERAND_COLUMN),
    EOL_COMMENT(2, DEFAULT_EOL_COMMENT_COLUMN);

    public final int columnModificationGroup;
    private int column;

    public int getColumn() {
        return column;
    }

    Columns(int columnModificationGroup, int column) {
        this.columnModificationGroup = columnModificationGroup;
        this.column = column;
    }

    /** When an option adds a column to the disassembly adjust the location of other columns */
    static void adjustFieldColumns(@NonNull final Columns after, int byWidth) {
        Objects.requireNonNull(after);
        for (var e : Columns.values())
            if (after.columnModificationGroup < e.columnModificationGroup) e.column += byWidth;
    }
}
