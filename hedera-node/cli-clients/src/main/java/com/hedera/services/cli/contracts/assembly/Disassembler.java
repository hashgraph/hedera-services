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

import static com.hedera.services.cli.contracts.assembly.Constants.CODE_OFFSET_COLUMN_WIDTH;
import static com.hedera.services.cli.contracts.assembly.Constants.OPCODE_COLUMN_WIDTH;
import static com.hedera.services.cli.contracts.assembly.Constants.UPPERCASE_HEX_FORMATTER;

import com.hedera.services.cli.contracts.DisassemblyOption;
import com.hedera.services.cli.contracts.assembly.DataPseudoOpLine.Kind;
import com.hedera.services.cli.utils.Range;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.IntFunction;
import java.util.regex.Pattern;

/**
 * Generates disassembly of an EVM contract
 *
 * <p>Generates the disassembly of an EVM contract. Defines the _lines_ of a disassembly as falling
 * into two classes: `Line`s - every line in the disassembly is one of these records, and `Code` -
 * all bytes of the bytecode go into one of these records.
 */
public class Disassembler {

    public Disassembler(@NonNull final Map</*@NonNull*/ String, /*@NonNull*/ Object> metrics) {

        this.metrics = metrics;

        if (DisassemblyOption.isOn(DisassemblyOption.DISPLAY_CODE_OFFSET))
            Columns.adjustFieldColumns(Columns.CODE_OFFSET, CODE_OFFSET_COLUMN_WIDTH);
        if (DisassemblyOption.isOn(DisassemblyOption.DISPLAY_OPCODE_HEX))
            Columns.adjustFieldColumns(Columns.OPCODE, OPCODE_COLUMN_WIDTH);
    }

    /**
     * Map for accumulating interesting metrics of disassembled code.
     *
     * <p>RFU.
     */
    private final @NonNull Map</*@NonNull*/ String, /*@NonNull*/ Object> metrics;

    /** The next instruction decoded from the bytestream */
    public record NextInstruction(@NonNull Line line, int nextOffset) {}

    /**
     * Get next instruction from bytecode stream
     *
     * <p>Get the next instruction from the bytecode stream, returning both the next instruction
     * decoded (as a Code) and the _next_ offset for the _next_ next instruction. At end of bytecode
     * it returns the "END" directive and then the _next_ time it is called it finally returns
     * `null`, signalling the caller to stop iterating.
     */
    public NextInstruction getNextInstruction(@NonNull final byte[] bytecode, int codeOffset) {

        // Check end conditions
        if (codeOffset < 0 || codeOffset >= bytecode.length) return null;
        ;

        // Get the next opcode and its syntax.
        final byte opcode = bytecode[codeOffset];
        final Opcodes.Descr descr = Opcodes.getOpcode(opcode);
        final boolean haveExtraBytes = descr.extraBytes() > 0;
        final int instructionLength = 1 /*opcode itself*/ + descr.extraBytes();

        // If we're decoding junk (e.g., we're decoding inside a block of code data) we might
        // find ourselves looking at what seems to be a `PUSHn` that runs off the end of the
        // bytecode.  That can't be right.
        if (haveExtraBytes && codeOffset + instructionLength > bytecode.length)
            throw new IndexOutOfBoundsException("opcode %s at offset %d overruns bytecode (of length %d)"
                    .formatted(descr.mnemonic(), codeOffset, bytecode.length));

        // Pull out the operand bytes, if any
        final var operandBytes = haveExtraBytes
                ? Arrays.copyOfRange(bytecode, codeOffset + 1, codeOffset + instructionLength)
                : new byte[0];

        // The comment field will be used to flag uses of unassigned opcodes.  (We're most
        // likely decoding "instructions" in a code data block.)
        final var comment = descr.isAssigned() ? "" : "**UNASSIGNED OPCODE**";

        // Assemble the instruction and give it to the caller
        final var line = new CodeLine(codeOffset, opcode, operandBytes, comment);
        return new NextInstruction(line, codeOffset + instructionLength);
    }

    // Disassemble the entire bytecode

    /** Disassemble the entire contract bytecode into its Lines of Code. */
    public List</*@NonNull*/ Line> getInstructions(
            @NonNull final List</*@NonNull*/ Line> prefixLines, @NonNull final byte[] bytecode) {
        List<Line> lines = new ArrayList<>(prefixLines);

        // First thing: find the metadata at the end of the contract - see where it starts, and
        // how long it is
        final var metadataByteRange = locateMetadata(bytecode);

        // This comparator returns 0 if _at_ the metadata start, or >0 if _past_ the metadata start
        final IntFunction<Integer> atMetadataOffset = metadataByteRange.isEmpty()
                ? ofs -> -1 /* never reach the end */
                : ofs -> Integer.compare(ofs, metadataByteRange.from());

        // Main instruction decode loop, working from bytecode
        NextInstruction nextInstruction;
        int currentOffset = 0;
        try {
            while (null != (nextInstruction = getNextInstruction(bytecode, currentOffset))) {

                // Check to see if we've overrun the start of the metadata
                if (atMetadataOffset.apply(nextInstruction.nextOffset()) > 0) {
                    // This instruction, evidently some kind of `PUSHn`, is taking us right past
                    // where the metadata is supposed to start.  That means we're already decoding
                    // the code data section and it is coming out as junk.  So we just replace
                    // this current junk instruction with a DATA that sucks up everything from here
                    // to where we know the metadata starts.  (If the junk started before this
                    // point that's ok too, it'll get eaten up when we search backward for `INVALID`
                    // or `STOP`.  (For an example of this see contract 1358908.))
                    boolean sanityCheck = nextInstruction.line() instanceof Code code && code.getCodeSize() > 0;
                    if (!sanityCheck)
                        throw new IllegalStateException(
                                "Have somehow run past metadata from with a single-byte" + " instruction");
                    final var data = getBytecodeRangeAsData(
                            bytecode,
                            new Range<Byte>(currentOffset, metadataByteRange.from() - 1),
                            "Backtracking data",
                            Kind.DATA);
                    nextInstruction = new NextInstruction(data, metadataByteRange.from());
                }

                // Ok, have an instruction to add the growing disassembly ...
                lines.add(nextInstruction.line());
                currentOffset = nextInstruction.nextOffset();

                // If now we've reached the metadata time to suck it all up and emit it as a
                // `METADATA` pseudo-op.
                if (0 == atMetadataOffset.apply(currentOffset)) {
                    final var comment = "(metadata %d (%04X) bytes)"
                            .formatted(metadataByteRange.length(), metadataByteRange.length());
                    final var data = getBytecodeRangeAsData(
                            bytecode, metadataByteRange, comment, DataPseudoOpLine.Kind.METADATA);
                    lines.add(data);
                    break;
                }
            }

            // At this point we've decoded everything to the end - now look for interesting cases
            // where you can simplify/correct/improve-for-readability the assembly source.

            // First interesting case: see if there's a code data block at the end that should
            // have disassembled as DATA and not code - that would be the stuff between a final
            // `INVALID` or `STOP` and the start of the metadata.

            lines = elideCodeData(lines, bytecode);

            // (At this time that turns out to be the _only_ interesting case we're looking for.)

        } catch (IndexOutOfBoundsException ex) {
            // If we've overrun the end then obviously something went wrong - is this an invalid
            // contract?  Have we messed up by not finding the code block properly?  Whatever,
            // we've got to emit the last bunch of bytes as a `DATA_OVERRUN` pseudo-op.
            // (Currently happens for contract 44928, dunno why yet.)
            if (ex.getMessage().contains("overruns bytecode")) {
                final var data = getBytecodeRangeAsData(
                        bytecode,
                        new Range<Byte>(currentOffset, bytecode.length),
                        "(*** PUSHn opcode here overruns end of bytecode)",
                        DataPseudoOpLine.Kind.DATA_OVERRUN);
                lines.add(data);
            }
        }

        ensureEndIsAtEnd(lines);
        return lines;
    }

    void ensureEndIsAtEnd(@NonNull final List<Line> lines) {
        if (!lines.isEmpty()
                && lines.get(lines.size() - 1) instanceof DirectiveLine directive
                && directive.directive().equals(DirectiveLine.Kind.END.name())) return;
        final var endDirective = new DirectiveLine(DirectiveLine.Kind.END);
        lines.add(endDirective);
    }

    /**
     * Elide the code block with a replacement `DATA` pseudo-op.
     *
     * <p>Given the lines (last one of which is probably a `METADATA` pseudo-op) find the code data
     * block between the "last" `INVALID` or `STOP` instruction and the start of the metadata, take
     * that bunch of data and change it into an (uninterpreted) `DATA` pseudo-op, and use it to
     * _replace_ all the (mis-)interpreted lines of Code.
     */
    List<Line> elideCodeData(@NonNull final List<Line> lines, @NonNull final byte[] bytecode) {
        if (!DisassemblyOption.isOn(DisassemblyOption.DO_NOT_DECODE_BEFORE_METADATA)) {

            // This function maps a `Range<Line>` of disassembled instructions to a `Range<Byte>`
            // of those bytecodes in the contract.  It does this by looking in the Lines to see what
            // offsets they are at in the bytecode.  Ranges here are _exclusive_ at the "to" end.
            final Function<Range<Line>, Range<Byte>> bytecodeRangeFromLineRange = lr -> {
                if (lines.size() == lr.from()) return Range.empty();

                final var fromLine = lines.get(lr.from());
                final var toLine = lines.get(lr.to());
                if (fromLine instanceof Code fromCode && toLine instanceof Code toCode) {
                    return new Range<Byte>(fromCode.getCodeOffset(), toCode.getCodeOffset());
                } else
                    throw new IllegalStateException("Expected Code at line index %d or %d (%s, %s)"
                            .formatted(lr.from(), lr.to(), fromLine, toLine));
            };

            // Search backwards from the metadata to an `INVALID` or `STOP` (if present).
            final var lineRange = lookBackwardsForDelimiterInstruction(lines, bytecode);

            if (!lineRange.isEmpty()) {
                final var codeRange = bytecodeRangeFromLineRange.apply(lineRange);

                // Eliminate junk "instructions" we've already disassembled that were, in fact,
                // part of this code data block
                lines.subList(lineRange.from(), lineRange.to()).clear();
                // FYI (very) arguable Java design decision: See "Why is Java's `AbstractList`'s
                // `removeRange()` method protected?"
                // See
                // https://stackoverflow.com/questions/2289183/why-is-javas-abstractlists-removerange-method-protected

                // And now turn that bunch of bytecodes into a `DATA` pseudo-op.
                final var dataLine = getBytecodeRangeAsData(bytecode, codeRange, "", DataPseudoOpLine.Kind.DATA);
                lines.add(lineRange.from(), dataLine);
            }
        }

        return lines;
    }

    /**
     * Look for a code data block between an `INVALID` or `STOP` and the metadata.
     *
     * <p>Look for a code data block between an `INVALID` or `STOP` and the metadata. We want the
     * _last_ `INVALID` or `STOP`. (There might be more than one.) How do we know which? We search
     * _backwards_ from the metadata line.
     */
    @NonNull
    public Range<Line> lookBackwardsForDelimiterInstruction(
            @NonNull final List</*@NonNull*/ Line> lines, @NonNull final byte[] bytecode) {
        // Only tricky thing here is keeping track of both Line locations and bytecode offsets.

        // start with empty ranges
        int lineRangeFrom = lines.size();
        int lineRangeTo = lineRangeFrom;

        if (lines.isEmpty() || 0 == bytecode.length) return Range.empty();

        // We either have metadata at the end, or we don't
        if (lines.get(lines.size() - 1) instanceof DataPseudoOpLine data
                && data.kind() == DataPseudoOpLine.Kind.METADATA) {
            lineRangeFrom--;
            lineRangeTo--;
        }

        // Scan now for first prior `INVALID` or `STOP`
        while (lineRangeFrom >= 0) {
            if (lines.get(lineRangeFrom) instanceof CodeLine code) {
                if (code.opcode() == INVALID_OPCODE || code.opcode() == STOP_OPCODE) {
                    lineRangeFrom++;
                    if (lineRangeFrom == lineRangeTo)
                        return Range.empty(); // This would be if there was no data block between
                    // INVALID/STOP and the metadata/end
                    return new Range<>(lineRangeFrom, lineRangeTo);
                }
            }
            lineRangeFrom--;
        }
        // Here, no INVALID or STOP found all the way to the beginning of the contract
        return Range.empty();
    }

    record SolidityMetadata(int metadataLength, String approxVersion, Pattern metadataPattern) {
        SolidityMetadata(int metadataLength, String approxVersion, String metadataPattern) {
            this(metadataLength, approxVersion, Pattern.compile(metadataPattern));
        }
    }

    /**
     * Signatures of "known" Solidity-generated metadata.
     *
     * <p>Signatures of "known" Solidity-generated metadata. Probably missing some, and will need to
     * update this in the future as the Solidity compiler evolves. Possibly this is too strict and
     * we shouldn't care. Maybe _other_ contract language compilers put metadata at the end in the
     * same CBOR format but containing different data. We'll see.
     *
     * <p>- ref: <a href="https://www.badykov.com/ethereum/solidity-bytecode-metadata/">...</a> -
     * ref: <a
     * href="https://docs.soliditylang.org/en/v0.8.17/metadata.html#encoding-of-the-metadata-hash-in-the-bytecode">...</a>
     */
    static final SolidityMetadata[] solidityMetadataEvolution = {
        new SolidityMetadata(0x0029, "~0.4.17", "A165627A7A72305820(..){32}0029"),
        new SolidityMetadata(0x0032, "0.5.10", "A265627A7A72305820(..){32}64736F6C6343(..){3}0032"),
        new SolidityMetadata(0x0032, "0.5.10", "A265627A7A72315820(..){32}64736F6C6343(..){3}0032"),
        new SolidityMetadata(0x0033, "~0.8.17", "A264697066735822(..){34}64736F6C6343(..){3}0033")
    };

    static final int MAX_METADATA_LENGTH = 0x60; // just a guess

    /**
     * Given contract bytecode find the Solidity metadata blob at the very end of it, if present.
     *
     * <p>Solitity puts metadata at the end of a compiled contract. It consists of a CBOR-encoded
     * blob followed by its length, the latter in two bytes (and the length here is that of the CBOR
     * blob only, not the entire metadata blob including the length bytes).
     *
     * <p>Look for the metadata at the end - performing some sanity checks on it. We only recognize
     * certain "known" patterns (there maybe some we're missing!) And the most obvious rigorous
     * sanity check isn't performed: we don't decode the metadata to see if it is a valid CBOR-
     * encoding thing. (Maybe someday.)
     */
    public @NonNull Range<Byte> locateMetadata(@NonNull final byte[] bytecode) {
        if (bytecode.length < 2) return Range.empty();
        final int metadataLength = bytecode[bytecode.length - 2] * 256 + bytecode[bytecode.length - 1];

        if (MAX_METADATA_LENGTH < metadataLength) return Range.empty();
        if (bytecode.length < metadataLength + 2) return Range.empty();

        final var metadataFrom = bytecode.length - 2 - metadataLength;
        final var tailAsHex = UPPERCASE_HEX_FORMATTER.formatHex(bytecode, metadataFrom, bytecode.length);
        for (var sm : solidityMetadataEvolution) {
            if (sm.metadataLength != metadataLength) continue;
            if (sm.metadataPattern.matcher(tailAsHex).matches()) return new Range<>(metadataFrom, bytecode.length);
        }

        // Don't get persnickety about metadata matching the known signatures.  So far we
        // have 2 that look like metadata at the end but don't match the signature.  We also
        // have ~50 that look like data of some kind at the end but don't have metadata.
        return new Range<>(metadataFrom, bytecode.length);
    }

    /** Given a range, return, as a `DATA` pseudo-op, the contract bytecodes in that range */
    public @NonNull DataPseudoOpLine getBytecodeRangeAsData(
            @NonNull final byte[] bytecode,
            @NonNull final Range<Byte> range,
            @NonNull final String comment,
            @NonNull final DataPseudoOpLine.Kind kind) {
        final var dataBytes = Arrays.copyOfRange(bytecode, range.from(), range.to());
        return new DataPseudoOpLine(range.from(), kind, dataBytes, comment);
    }

    public static final int INVALID_OPCODE = Opcodes.getDescrFor("INVALID").opcode();
    public static final int STOP_OPCODE = Opcodes.getDescrFor("STOP").opcode();
}
