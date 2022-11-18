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

import com.hedera.services.yahcli.commands.signedstate.evminfo.DataPseudoOpLine.Kind;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.IntPredicate;
import java.util.function.Supplier;
import java.util.regex.Pattern;
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

    public Assembly(
            @NotNull Map<@NotNull String, @NotNull Object> metrics, @NotNull Options... options) {
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
        this.metrics = metrics;
    }

    private final @NotNull Map<@NotNull String, @NotNull Object> metrics;

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
    public List<@NotNull Line> getInstructions(
            @NotNull List<@NotNull Line> prefixLines, int @NotNull [] bytecode) {
        var lines = new ArrayList<>(prefixLines);

        final var metadataPresence = locateMetadata(bytecode);
        final int metadataOffset = metadataPresence.map(md -> md.offset).orElse(bytecode.length);
        final int metadataLength = metadataPresence.map(md -> md.length).orElse(0);
        final IntPredicate atMetadataOffset =
                metadataPresence.isPresent() ? ofs -> ofs == metadataOffset : ofs -> false;

        int currentOffset = 0;
        ImmutablePair<Line, Integer> p;
        try {
            while (null != (p = getNextInstruction(bytecode, currentOffset))) {
                lines.add(p.left);
                currentOffset = p.right;

                if (atMetadataOffset.test(currentOffset)) {
                    final var data =
                            getBytecodeRangeAsData(
                                    bytecode,
                                    currentOffset,
                                    currentOffset + metadataLength,
                                    String.format(
                                            "(metadata %d (%04X) bytes)",
                                            metadataLength, metadataLength),
                                    Kind.METADATA);
                    lines.add(data);
                    break;
                }
            }

            // Have everything to end - now look for interesting cases

            // First interesting case: see if there's a data block at the end that should
            // have disassembled as DATA and not code

            final var dataInstructionRange = lookBackwardsForDelimiterInstruction(lines, bytecode);
            DataRange lineRange = dataInstructionRange.left;
            DataRange bytecodeRange = dataInstructionRange.right;

            if (0 != lineRange.length) {
                // Eliminate wrongly assembled instructions
                lines.subList(lineRange.offset, lineRange.offset + lineRange.length).clear();
                // FYI (very) arguable Java design decision: See "Why is Java's `AbstractList`'s
                // `removeRange()` method protected?
                // See
                // https://stackoverflow.com/questions/2289183/why-is-javas-abstractlists-removerange-method-protected

                // DATAize the bytecode and add it
                final var dataLine =
                        getBytecodeRangeAsData(
                                bytecode,
                                bytecodeRange.offset,
                                bytecodeRange.offset + bytecodeRange.length,
                                "",
                                Kind.DATA);
                lines.add(lineRange.offset, dataLine);
            }

        } catch (IndexOutOfBoundsException ex) {
            if (ex.getMessage().contains("overruns bytecode")) {
                final var data =
                        getBytecodeRangeAsData(
                                bytecode,
                                currentOffset,
                                bytecode.length,
                                "(*** PUSHn opcode here overruns end of bytecode)",
                                Kind.DATA_OVERRUN);
                lines.add(data);
            }
        }

        return lines;
    }

    record DataRange(int offset, int length) {}

    // starting at the end (skipping the metadata pseudo-op, if present) look for previous
    // `INVALID` or `STOP` opcode, return the range of lines and bytecodes skipped over
    @NotNull
    ImmutablePair<@NotNull DataRange, @NotNull DataRange> lookBackwardsForDelimiterInstruction(
            @NotNull List<@NotNull Line> lines, int @NotNull [] bytecode) {
        // Only tricky thing here is keeping track of both Line locations and bytecode offsets.

        // start with empty ranges
        int lineRangeFrom = lines.size();
        int lineRangeTo = lineRangeFrom;

        int bytecodeTo = bytecode.length;

        Supplier<ImmutablePair<@NotNull DataRange, @NotNull DataRange>> emptyRange =
                () ->
                        new ImmutablePair<>(
                                new DataRange(lines.size(), lines.size()),
                                new DataRange(bytecode.length, bytecode.length));
        if (lines.isEmpty() || 0 == bytecode.length) return emptyRange.get();

        // We either have metadata at the end, or we don't
        if (lines.get(lines.size() - 1) instanceof DataPseudoOpLine data
                && data.kind() == Kind.METADATA) {
            lineRangeFrom = lineRangeTo = lines.size() - 1;
            bytecodeTo = data.codeOffset();
        }

        while (lineRangeFrom >= 0) {
            if (lines.get(lineRangeFrom) instanceof CodeLine code) {
                if (code.opcode() == INVALID_OPCODE || code.opcode() == STOP_OPCODE) {
                    final int bytecodeFrom =
                            code.codeOffset() + 1; // the _next_ offset after this opcode
                    lineRangeFrom++;
                    if (lineRangeFrom == lineRangeTo)
                        return emptyRange.get(); // This would be if there was no data block between
                    // INVALID/STOP and the metadate/end
                    return new ImmutablePair<>(
                            new DataRange(lineRangeFrom, lineRangeTo - lineRangeFrom),
                            new DataRange(bytecodeFrom, bytecodeTo - bytecodeFrom));
                }
            }
            lineRangeFrom--;
        }
        // Here, no INVALID or STOP found
        return emptyRange.get();
    }

    record SolidityMetadata(int metadataLength, String approxVersion, Pattern metadataPattern) {
        SolidityMetadata(int metadataLength, String approxVersion, String metadataPattern) {
            this(metadataLength, approxVersion, Pattern.compile(metadataPattern));
        }
    }

    static final SolidityMetadata[] solidityMetadataEvolution = {
        new SolidityMetadata(0x0029, "~0.4.17", "A165627A7A72305820.{64}0029"),
        new SolidityMetadata(0x0032, "0.5.10", "A265627A7A72305820.{64}64736F6C6343.{6}0032"),
        new SolidityMetadata(0x0032, "0.5.10", "A265627A7A72315820.{64}64736F6C6343.{6}0032"),
        new SolidityMetadata(0x0033, "~0.8.17", "A264697066735822.{68}64736F6C6343.{6}0033")
    };

    // Attempt to locate "known" contract metadata at end of bytecode
    // - ref: https://www.badykov.com/ethereum/solidity-bytecode-metadata/
    // - ref:
    // https://docs.soliditylang.org/en/v0.8.17/metadata.html#encoding-of-the-metadata-hash-in-the-bytecode
    // Possibly not a complete list of metadata variants ...

    @NotNull
    Optional<DataRange> locateMetadata(int @NotNull [] bytecode) {
        if (bytecode.length < 2) return Optional.empty();
        // last 2 bytes of contract bytecode is metadata length (_not_ including the length bytes
        // themselves)
        final int metadataLength =
                bytecode[bytecode.length - 2] * 256 + bytecode[bytecode.length - 1];
        if (bytecode.length < metadataLength + 2) return Optional.empty();
        final var metadataFrom = bytecode.length - 2 - metadataLength;
        final var tailAsHex = Utility.toHex(bytecode, metadataFrom);
        for (var sm : solidityMetadataEvolution) {
            if (sm.metadataLength != metadataLength) continue;
            if (sm.metadataPattern.matcher(tailAsHex).matches())
                return Optional.of(new DataRange(metadataFrom, metadataLength));
        }
        return Optional.empty();
    }

    private @NotNull Line getBytecodeRangeAsData(
            int @NotNull [] bytecode, int from, int to, String comment, @NotNull Kind kind) {
        final var dataBytes = Arrays.copyOfRange(bytecode, from, to);
        return new DataPseudoOpLine(from, kind, dataBytes, comment);
    }

    public static final int INVALID_OPCODE = Opcodes.getDescrFor("INVALID").opcode();
    public static final int STOP_OPCODE = Opcodes.getDescrFor("STOP").opcode();

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
