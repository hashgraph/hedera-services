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

package com.hedera.services.cli.signedstate;

import static com.hedera.node.app.hapi.utils.CommonPbjConverters.fromPbj;
import static com.hedera.services.cli.utils.ThingsToStrings.toStructureSummaryOfKey;

import com.hedera.hapi.node.base.Key;
import com.hedera.hapi.node.state.file.File;
import com.hedera.services.cli.signedstate.DumpStateCommand.EmitSummary;
import com.hedera.services.cli.signedstate.DumpStateCommand.KeyDetails;
import com.hedera.services.cli.signedstate.DumpStateCommand.OmitContents;
import com.hedera.services.cli.signedstate.SignedStateCommand.Verbosity;
import com.hedera.services.cli.utils.Writer;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.function.BinaryOperator;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Dump all the data files in a signed state file in textual format.  Output is deterministic for a signed
 * state - sorted by file entity number, etc. - so that
 * comparisons can be made between two signed states that are similar (e.g., mono-service vs modularized service when
 * the same events are run through them).
 * <p>
 * N.B.: Does _not_ include _special files_ which are stored in a different merkle tree.
 */
@SuppressWarnings("java:S106") // "use of system.out/system.err instead of logger" - not needed/desirable for CLI tool
public class DumpFilesSubcommand {

    static void doit(
            @NonNull final SignedStateHolder state,
            @NonNull final Path filesPath,
            @NonNull final EnumSet<KeyDetails> keyDetails,
            @NonNull final EmitSummary emitSummary,
            @NonNull final OmitContents omitContents,
            @NonNull final Verbosity verbosity) {
        new DumpFilesSubcommand(state, filesPath, keyDetails, emitSummary, omitContents, verbosity).doit();
    }

    @NonNull
    final SignedStateHolder state;

    @NonNull
    final Path filesPath;

    @NonNull
    final EnumSet<KeyDetails> keyDetails;

    @NonNull
    final EmitSummary emitSummary;

    @NonNull
    final OmitContents omitContents;

    @NonNull
    final Verbosity verbosity;

    DumpFilesSubcommand(
            @NonNull final SignedStateHolder state,
            @NonNull final Path filesPath,
            @NonNull final EnumSet<KeyDetails> keyDetails,
            @NonNull final EmitSummary emitSummary,
            @NonNull final OmitContents omitContents,
            @NonNull final Verbosity verbosity) {
        this.state = state;
        this.filesPath = filesPath;
        this.keyDetails = keyDetails;
        this.emitSummary = emitSummary;
        this.omitContents = omitContents;
        this.verbosity = verbosity;
    }

    void doit() {
        if (verbosity == Verbosity.VERBOSE)
            System.out.printf("=== %d data files (%d from special files store) ===%n", 0, 0);

        int reportSize;
        try (@NonNull final var writer = new Writer(filesPath)) {
            reportSize = writer.getSize();
        } catch (final RuntimeException ex) {
            System.err.printf("*** Exception when writing to '%s':%n", filesPath);
            throw ex;
        }

        if (verbosity == Verbosity.VERBOSE)
            System.out.printf(
                    "=== files report is %d bytes%s%n",
                    reportSize, omitContents == OmitContents.YES ? " (file contents omitted from report)" : "");
    }

    /**
     * Holds summaries of how many files of each type there are in the store, also how many are missing content.
     */
    enum FileStore {
        ORDINARY,
        SPECIAL
    }

    enum SystemFileType {
        ADDRESS_BOOK(101),
        NODE_DETAILS(102),
        FEE_SCHEDULES(111),
        EXCHANGE_RATES(112),
        NETWORK_PROPERTIES(121),
        HAPI_PERMISSIONS(122),
        THROTTLE_DEFINITIONS(123),
        SOFTWARE_UPDATE0(150),
        SOFTWARE_UPDATE1(151),
        SOFTWARE_UPDATE2(152),
        SOFTWARE_UPDATE3(153),
        SOFTWARE_UPDATE4(154),
        SOFTWARE_UPDATE5(155),
        SOFTWARE_UPDATE6(156),
        SOFTWARE_UPDATE7(157),
        SOFTWARE_UPDATE8(158),
        SOFTWARE_UPDATE9(159),
        UNKNOWN(-1);

        public final int id;

        static final Map<Integer, SystemFileType> byId = new HashMap<>();

        SystemFileType(final int id) {
            this.id = id;
        }

        static {
            EnumSet.allOf(SystemFileType.class).forEach(e -> byId.put(e.id, e));
        }
    }

    /**
     * Emits a histogram of file content sizes
     */
    void reportFileSizes(@NonNull final Writer writer, @NonNull final Map<Integer, File> allFiles) {
        final var histogram = allFiles.values().stream()
                .map(hf -> hf.contents().length())
                .collect(Collectors.groupingBy(n -> 0 == n ? 0 : (int) Math.log10(n), Collectors.counting()));
        final var maxDigits =
                histogram.keySet().stream().max(Comparator.naturalOrder()).orElse(0);

        final var sb = new StringBuilder(1000);

        sb.append("#   === Content Size Histogram (size in bytes) ===%n".formatted());
        sb.append("#          =0: %6d%n".formatted(histogram.getOrDefault(0, 0L)));
        for (int i = 1; i <= maxDigits; i++) {
            sb.append("# %11s: %6d%n".formatted("<=" + (int) Math.pow(10, i), histogram.getOrDefault(i, 0L)));
        }

        writer.write(sb);
    }

    /**
     * Emits the CSV header line for the file contents - **KEEP IN SYNC WITH reportFileContents!!!**
     */
    void reportFileContentsHeader(@NonNull final Writer writer) {
        final var header = "fileId,PRESENT/DELETED,SPECIAL file,SYSTEM file,length(bytes),expiry,memo,content,key";
        writer.write("%s%n", header);
    }

    /**
     * Emits the actual content (hexified) for each file, and it's full key
     */
    void reportFileContents(
            @NonNull final Writer writer,
            @NonNull final Map<Integer, File> allFiles,
            @NonNull final OmitContents omitContents) {
        for (@NonNull
        final var file :
                allFiles.entrySet().stream().sorted(Map.Entry.comparingByKey()).toList()) {
            // (FUTURE) Format a CSV line for the file
        }
    }

    /**
     * Emits a summary of the _structures_ of the keys securing the data files.
     */
    void reportOnKeyStructure(@NonNull final Writer writer, @NonNull final Map<Integer, File> allFiles) {
        final var keySummary = new HashMap<String, Integer>();
        for (@NonNull final var hf : allFiles.values()) {
            if (hf.deleted()) continue;
            final var jkey = hf.keysOrThrow();
            final var sb = new StringBuilder();
            toStructureSummaryOfKey(sb, fromPbj(Key.newBuilder().keyList(jkey).build()));
            keySummary.merge(sb.toString(), 1, Integer::sum);
        }

        writer.writeln("=== Key Summary ===");
        keySummary.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEachOrdered(e -> writer.write("%7d: %s%n", e.getValue(), e.getKey()));
    }

    /**
     * Emits a summary of key structure for each file that has a non-trivial key
     */
    @SuppressWarnings("java:S135")
    // "Reduce total # of break+continue statements to at most one" - disagree
    // it would improve this code
    void reportOnKeyIds(@NonNull final Writer writer, @NonNull final Map<Integer, File> allFiles) {
        final var keySummary = new HashMap<Integer, String>();
        for (@NonNull final var hf : allFiles.values()) {
            if (hf.deleted()) continue;
            final var jkey = hf.keys();
            if (null == jkey) continue;
            final var key = jkey.keys();
            if (key.isEmpty()) continue;
            // Have a "complex" key (more than one key in key list)
            final var sb = new StringBuilder();
            toStructureSummaryOfKey(sb, fromPbj(Key.newBuilder().keyList(jkey).build()));
        }

        writer.writeln("=== Files with complex keys ===");
        if (!keySummary.isEmpty()) {
            final var NEW_LINE = System.lineSeparator();
            final var sb = new StringBuilder();
            sb.append("[");
            sb.append(NEW_LINE);
            keySummary.entrySet().stream()
                    .sorted(Map.Entry.comparingByKey())
                    .forEachOrdered(e -> sb.append("  { \"fileid\" : \"0.0.%d\", \"key\" : \"%s\" }, %s"
                            .formatted(e.getKey(), e.getValue(), NEW_LINE)));
            sb.setLength(sb.length() - (1 + NEW_LINE.length()));
            sb.append(NEW_LINE);
            sb.append("]");
            sb.append(NEW_LINE);
            writer.write(sb);
        }
    }

    /**
     * Merge two (or more) maps.
     * <p>
     * (Seems like it should be provided by the `Map` class, but maybe not.)
     */
    @SafeVarargs
    @SuppressWarnings("varargs")
    @NonNull
    static <K, V> Map<K, V> merge(@NonNull final BinaryOperator<V> mergeFunction, @NonNull final Map<K, V>... maps) {
        return Stream.of(maps)
                .flatMap(m -> m.entrySet().stream())
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, mergeFunction));
    }

    /**
     * Pick `a` or `b` arbitrarily.
     * <p>
     * Not usually a good idea, but in this case it is really never called, as it a merge function for disjoint sets
     */
    <T> T arb(@NonNull final T a, @NonNull final T b) {
        final var choice = new Random().nextBoolean();
        return choice ? a : b;
    }
}
