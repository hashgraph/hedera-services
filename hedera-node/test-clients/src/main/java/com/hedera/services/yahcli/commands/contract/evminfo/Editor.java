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

package com.hedera.services.yahcli.commands.contract.evminfo;

import static java.util.Comparator.comparing;
import static java.util.Comparator.reverseOrder;
import static java.util.stream.Collectors.groupingBy;

import com.hedera.services.yahcli.commands.contract.evminfo.Assembly.Code;
import com.hedera.services.yahcli.commands.contract.evminfo.Assembly.Line;
import com.hedera.services.yahcli.commands.contract.evminfo.Editor.Edit.Kind;
import com.hedera.services.yahcli.commands.contract.utils.Range;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.TreeMap;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * Represents "edits" to an Assembly of Lines.
 *
 * <p>Lines can be inserted, replaced (by a different number of lines), deleted. Kept as a separate
 * structure of Edits that can be "applied" to a list of Lines to produce a final listing.
 */
public class Editor {

    @NonNull
    private final List<Line> baseLines;

    @NonNull
    private List<Edit> edits;

    public Editor(@NonNull List<Line> baseLines) {
        this.baseLines = baseLines;
        this.edits = new ArrayList<>(100);
    }

    public void add(
            @NonNull final Range<Line> lineRange,
            @NonNull final List<Line> newLines,
            @NonNull final Range<Byte> byteRange) {
        if (lineRange.isEmpty() && newLines.isEmpty() && byteRange.isEmpty()) return;
        edits.add(new Edit(this, lineRange, newLines, byteRange));
    }

    public void add(@NonNull final Range<Line> lineRange, @NonNull final List<Line> newLines) {
        edits.add(new Edit(this, lineRange, newLines));
    }

    public void add(@NonNull final Code replacementCode) {
        edits.add(new Edit(this, getLineRangeFromCode(replacementCode), List.of(replacementCode)));
    }

    protected List<Edit> getEdits() {
        return edits;
    }

    protected record Edit(
            @NonNull Range<Line> lineRange, @NonNull List<Line> newLines, @NonNull Range<Byte> byteRange) {

        enum Kind {
            INSERT,
            DELETE,
            REPLACE
        }

        public Edit(
                @NonNull final Editor editor,
                @NonNull final Range<Line> lineRange,
                @NonNull final List<Line> newLines,
                @Nullable final Range<Byte> byteRange) {
            this(editor.validateLineRange(lineRange), newLines, editor.validateByteRange(lineRange, byteRange));
        }

        public Edit(
                @NonNull final Editor editor,
                @NonNull final Range<Line> lineRange,
                @NonNull final List<Line> newLines) {
            this(editor, lineRange, newLines, editor.getByteRangeFromLines(editor.validateLineRange(lineRange)));
        }

        @NonNull
        public Kind getKind() {
            if (!lineRange.isEmpty() && !newLines.isEmpty()) return Kind.REPLACE;
            if (lineRange.isEmpty()) return Kind.INSERT;
            return Kind.DELETE;
        }

        @Override
        public String toString() {
            return "Edit{lineRange=%s, byteRange=%s, newLines=%s}".formatted(lineRange, byteRange, newLines);
        }
    }

    /** Merge a List<Line> with a bunch of Edits, producing a new assembly List<Line> */
    @NonNull
    public List<Line> merge() {
        final var merged = new ArrayList<Line>(baseLines);
        final Consumer<Edit> inserter = edit -> merged.addAll(edit.lineRange().from(), edit.newLines());
        final Consumer<Edit> deleter = edit ->
                merged.subList(edit.lineRange().from(), edit.lineRange().to()).clear();

        // Edits are specified in terms of the line ranges they affect ... so a little thought
        // reveals that for best (easiest!) results you process them in _descending_ order from the
        // end of the listing to the beginning.
        arrangeEditsForMerge(edits);
        for (final var edit : edits) {
            switch (edit.getKind()) {
                case INSERT -> inserter.accept(edit);
                case DELETE -> deleter.accept(edit);
                case REPLACE -> {
                    deleter.accept(edit);
                    inserter.accept(edit);
                }
            }
        }

        return merged;
    }

    /**
     * Edits must be non-overlapping and in _reverse_ line range order to be merged with an assembly
     * list
     */
    protected static void arrangeEditsForMerge(@NonNull final List<Edit> edits) {
        if (!hasNoOverlappingEdits(edits)) throw new IndexOutOfBoundsException("Edits overlap");
        edits.sort(comparing(rng -> rng.lineRange().from(), reverseOrder()));
    }

    /** Given a range into a list of lines, get the bytecode range they encompass */
    @SuppressWarnings("java:S2293") // Replace type specification...with diamond operator (for `Range<...>`)
    @NonNull
    protected Range<Byte> getByteRangeFromLines(@NonNull final Range<Line> lineRange) {
        if (lineRange.isEmpty()) return Range.<Byte>empty();

        /* Scan the range looking for the first and last Code line.  From that compute the
         * byte range.  If there are no Code lines in the range, rescan from the beginning of
         * the baseLines.
         */

        Code[] firstLast = getCodeRangeFromLines(lineRange);
        if (null == firstLast[0]) firstLast = getCodeRangeFromLines(new Range<Line>(0, lineRange.to()));
        if (null == firstLast[0]) return Range.<Byte>empty();
        return new Range<Byte>(
                firstLast[0].getCodeOffset(),
                firstLast[1].getCodeOffset() + firstLast[1].getCodeSize() /*exclusive at this end*/);
    }

    /** Given a range into a list of lines, get the first and last Code line */
    @NonNull
    protected Code[] getCodeRangeFromLines(@NonNull final Range<Line> lineRange) {
        Code firstCode = null;
        Code lastCode = null;

        for (int i = lineRange.from(); i < lineRange.to(); i++) {
            final var line = baseLines.get(i);
            if (line instanceof Code code) {
                if (null == firstCode) firstCode = code;
                lastCode = code;
            }
        }
        return new Code[] {firstCode, lastCode};
    }

    /**
     * Given a Code line (probably a macro) get (from its offset and size) the range of lines that
     * it covers
     */
    @NonNull
    protected Range<Line> getLineRangeFromCode(@NonNull final Code codeLine) {
        final var atOffset = codeLine.getCodeOffset();
        final var toOffset = atOffset + codeLine.getCodeSize();

        // Look for the line of code at the given code offset
        int firstLine = -1;
        int i = 0;
        while (i < baseLines.size()) {
            if (baseLines.get(i) instanceof Code code) {
                if (code.getCodeOffset() == atOffset) {
                    firstLine = i;
                    break;
                }
            }
            i++;
        }
        if (firstLine < 0)
            throw new IndexOutOfBoundsException("can't find code line at code offset 0x%04X".formatted(atOffset));

        if (atOffset == toOffset) {
            return new Range<>(firstLine, firstLine);
        }

        // Now look for the line of code that extends to the end of the given code line
        int lastLine = -1;
        while (i < baseLines.size()) {
            if (baseLines.get(i) instanceof Code code) {
                if (code.getCodeOffset() + code.getCodeSize() == toOffset) {
                    lastLine = i + 1;
                    break;
                }
            }
            i++;
        }
        if (lastLine < 0)
            throw new IndexOutOfBoundsException(
                    "can't find code line with last offset at 0x%04X, code line %s".formatted(toOffset, codeLine));

        return new Range<>(firstLine, lastLine);
    }

    /**
     * Check that the range of lines is valid in general and also in particular
     *
     * <p>Check that the range of lines is valid in general and also with respect to the particular
     * list of lines we're dealing with.
     */
    protected boolean isValidLineRange(@NonNull final Range<Line> lineRange) {

        if (lineRange.from() < 0 || lineRange.from() > baseLines.size()) return false;
        return lineRange.to() >= lineRange.from() && lineRange.to() <= baseLines.size();
    }

    /** Throws if the line range given is not valid, otherwise returns it */
    @NonNull
    protected Range<Line> validateLineRange(@NonNull final Range<Line> lineRange) {
        if (!isValidLineRange(lineRange)) throw new IllegalArgumentException("EDIT: invalid lineRange");
        return lineRange;
    }

    /**
     * Check that the range of bytes is valid in general and also in particular
     *
     * <p>Check that the range of bytes is valid in general and also with respect to the particular
     * list of lines we're dealing with.
     *
     * <p>Precondition: `validLineRange(baseLines, lineRange)` => `true`
     */
    protected boolean isValidByteRange(@NonNull final Range<Line> lineRange, @NonNull final Range<Byte> byteRange) {

        final var actualBaseLinesByteRange = getByteRangeFromLines(lineRange);
        if (byteRange.from() < actualBaseLinesByteRange.from() || byteRange.from() > actualBaseLinesByteRange.to())
            return false;
        return byteRange.to() >= byteRange.from() && byteRange.to() <= actualBaseLinesByteRange.to();
    }

    /** Throws if the byte range given is not valid, otherwise returns it */
    @NonNull
    protected Range<Byte> validateByteRange(
            @NonNull final Range<Line> lineRange, @Nullable final Range<Byte> byteRange) {
        if (null == byteRange || !isValidByteRange(lineRange, byteRange))
            throw new IllegalArgumentException("EDIT: invalid byteRange");
        return byteRange;
    }

    /** Predicate testing whether any of the Edits in a collection of them overlap in Lines */
    protected static boolean hasNoOverlappingEdits(@NonNull final List<Edit> edits) {

        // Although this method takes Edits it only deals with the line ranges of those edits.

        // Empty line ranges signal an INSERT.  These are _not_ allowed to happen:
        //    a) Properly _within_ another range (but it's ok at either _end_ of a range), and
        //    b) _at the same location_ as another INSERT.
        // Because of these special rules empty ranges are treated separately from non-empty ranges.

        // Partition on "emptiness" of range: `true` is empty
        final var emptyAndProperRanges =
                edits.stream().map(Edit::lineRange).collect(Collectors.partitioningBy(Range<Line>::isEmpty));

        {
            // Handle the empty ranges (INSERTS)

            // For the empty ranges we need only the line they're pointing to.
            final var emptyLines =
                    emptyAndProperRanges.get(true).stream().map(Range::from).toList();
            final var properRanges = emptyAndProperRanges.get(false);

            // a) all must be distinct
            final var distinctLines = new HashSet<Integer>();
            for (final var line : emptyLines) {
                if (!distinctLines.add(line)) return false;
            }
            // b) none must be properly within a properRange (this is 𝑂(𝑚𝑛), but there aren't
            // going to be many edits)
            for (final var line : emptyLines) {
                if (properRanges.stream().anyMatch(l -> l.properlyWithin(line))) return false;
            }
        }

        {
            // Handle the proper ranges: none can _overlap_
            if (Range.hasOverlappingRanges(emptyAndProperRanges.get(false))) return false;
        }

        return true;
    }

    enum Verbose {
        NO,
        YES
    }

    @NonNull
    String toString(@NonNull final Verbose verbose) {

        final var kinds = edits.stream()
                .map(Edit::getKind)
                .collect(groupingBy(
                        k -> k, () -> new TreeMap<Kind, Long>(comparing(Kind::name)), Collectors.counting()));
        for (final var k : Kind.class.getEnumConstants()) kinds.putIfAbsent(k, 0L);

        final var basicInfo = "%d baselines, %d edits %s".formatted(baseLines.size(), edits.size(), kinds.toString());
        return switch (verbose) {
            case NO -> "Editor[%s]".formatted(basicInfo);
            case YES -> "Editor[%s baselines: %s edits: %s]".formatted(basicInfo, baseLines, edits);
        };
    }
}
