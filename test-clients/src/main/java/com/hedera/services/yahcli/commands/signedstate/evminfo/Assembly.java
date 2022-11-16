/*
 * Copyright (C) 2022 Hedera Hashgraph, LLC
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
package com.hedera.services.yahcli.commands.signedstate.evminfo;

import com.google.common.collect.ImmutableList;
import java.util.Arrays;
import java.util.List;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

// represents a line of disassembled assembly code
public class Assembly {

    // A line of the disassembly
    // (dev note: here is an _interface_ with _default_ methods so that "subclasses" can be
    // immutable records - which is what I really want)
    public static interface Line {

        void formatLine(StringBuilder sb);

        default String formatLine() {
            var sb = new StringBuilder(MAX_EXPECTED_LINE);
            formatLine(sb);
            return sb.toString();
        }

        default void extendWithBlanksTo(StringBuilder sb, int goalColumn) {
            while (sb.length() < goalColumn) {
                sb.append("                ");
            }
            sb.setLength(goalColumn);
        }

        default boolean thisLineIsA(int opcode) {
            return this instanceof CodeLine codeLine && codeLine.opcode() == opcode;
        }
    }

    // A line of _code_ in the disassembly (could be an opcode, could be a macro)
    public static interface Code {

        public abstract int getCodeOffset();

        public abstract int getCodeSize();
    }

    public enum Options {
        DISPLAY_CODE_OFFSET,
        DISPLAY_OPCODE_HEX;

        private boolean value = false;

        public boolean getValue() {
            return value;
        }

        void setOptionOn() {
            this.value = true;
        }
    }

    public Assembly(@NotNull Options... options) {
        for (var o : options) {
            switch (o) {
                case DISPLAY_CODE_OFFSET -> {
                    Options.DISPLAY_CODE_OFFSET.setOptionOn();
                    Columns.adjustFieldColumns(Columns.CODE_OFFSET, CODE_OFFSET_COLUMN_WIDTH);
                }
                case DISPLAY_OPCODE_HEX -> {
                    Options.DISPLAY_OPCODE_HEX.setOptionOn();
                    Columns.adjustFieldColumns(Columns.OPCODE, OPCODE_COLUMN_WIDTH);
                }
            }
        }
    }

    enum Columns {
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

        static void adjustFieldColumns(@NotNull Columns after, int byWidth) {
            for (var e : Columns.values())
                if (after.columnModificationGroup < e.columnModificationGroup) e.column += byWidth;
        }
    }

    // Get next instruction from the bytecode, returning a pair:
    // - _left_ (first element): `Line` for that next instruction
    // - _right_ (second element): code offset for _next_ instruction
    // At end of bytecode returns the "END" directive, and then the _next_ time it is called
    // it finally returns `null`.
    @Contract(pure = true)
    public ImmutablePair<@NotNull Line, Integer> getNextInstruction(
            int @NotNull [] bytecode, int codeOffset) {
        if (codeOffset < 0) return null;
        if (codeOffset >= bytecode.length) return ImmutablePair.of(new DirectiveLine("END"), -1);

        final int opcode = bytecode[codeOffset];
        final Opcodes.Descr descr = Opcodes.byOpcode.get(opcode);
        final boolean haveExtraBytes = descr.extraBytes() > 0;
        final int instructionLength = 1 /*opcode itself*/ + descr.extraBytes();

        if (haveExtraBytes && codeOffset + instructionLength > bytecode.length)
            throw new IndexOutOfBoundsException(
                    String.format(
                            "opcode %s at offset %d overruns bytecode (of length %d)",
                            descr.mnemonic(), codeOffset, bytecode.length));

        final var operandBytes =
                haveExtraBytes
                        ? Arrays.copyOfRange(
                                bytecode, codeOffset + 1, codeOffset + instructionLength)
                        : new int[0];

        final var comment = descr.assigned() ? "" : "**UNASSIGNED OPCODE**";

        final var line = new CodeLine(codeOffset, opcode, operandBytes, comment);
        return ImmutablePair.of(line, codeOffset + instructionLength);
    }

    @Contract(pure = true)
    // Disassemble the entire bytecode
    public ImmutableList<Line> getInstructions(
            @NotNull List<Line> prefixLines,
            int @NotNull [] bytecode,
            boolean solidityDataIsAfterInvalidOp,
            boolean dumpPartial) {
        var lines = new ImmutableList.Builder<Line>().addAll(prefixLines);

        int currentOffset = 0;
        ImmutablePair<Line, Integer> p;
        try {
            while (null != (p = getNextInstruction(bytecode, currentOffset))) {
                lines.add(p.left);
                currentOffset = p.right;

                if (solidityDataIsAfterInvalidOp && p.left.thisLineIsA(INVALID_OPCODE)) {
                    final var data = getRemainingBytecodesAsData(bytecode, currentOffset);
                    lines.add(data);
                    break;
                }
            }
        } catch (IndexOutOfBoundsException ex) {
            if (dumpPartial && ex.getMessage().contains("overruns bytecode")) {
                System.out.printf("***** %s\n", ex.getMessage());
                return lines.build();
            }
            throw ex;
        }

        return lines.build();
    }

    @Contract(pure = true)
    private @NotNull Line getRemainingBytecodesAsData(int @NotNull [] bytecode, int currentOffset) {
        final var dataBytes = Arrays.copyOfRange(bytecode, currentOffset, bytecode.length);
        return new DataPseudoOpLine(currentOffset, dataBytes, "");
    }

    public static final int INVALID_OPCODE = Opcodes.getDescrFor("INVALID").opcode();

    public static final int DEFAULT_CODE_OFFSET_COLUMN = 0;
    public static final int DEFAULT_OPCODE_COLUMN = 0;
    public static final int DEFAULT_COMMENT_COLUMN = 6;
    public static final int DEFAULT_LABEL_COLUMN = 6;
    public static final int DEFAULT_MNEMONIC_COLUMN = 10;
    public static final int DEFAULT_OPERAND_COLUMN = 18;
    public static final int DEFAULT_EOL_COMMENT_COLUMN = 22;
    public static final int MAX_EXPECTED_LINE = 80;

    public static final int CODE_OFFSET_COLUMN_WIDTH = 8;
    public static final int OPCODE_COLUMN_WIDTH = 2;

    public static final String FULL_LINE_COMMENT_PREFIX = "# ";
    public static final String EOL_COMMENT_PREFIX = "# ";
}
