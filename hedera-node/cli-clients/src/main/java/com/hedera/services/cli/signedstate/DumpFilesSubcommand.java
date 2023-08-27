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

package com.hedera.services.cli.signedstate;

import static com.hedera.services.cli.utils.ThingsToStrings.quoteForCsv;
import static com.hedera.services.cli.utils.ThingsToStrings.toStringOfByteArray;
import static com.hedera.services.cli.utils.ThingsToStrings.toStructureSummaryOfJKey;
import static com.swirlds.common.threading.manager.AdHocThreadManager.getStaticThreadManager;

import com.hedera.node.app.service.mono.files.HFileMeta;
import com.hedera.node.app.service.mono.files.MetadataMapFactory;
import com.hedera.node.app.service.mono.state.adapters.VirtualMapLike;
import com.hedera.node.app.service.mono.state.virtual.VirtualBlobKey;
import com.hedera.node.app.service.mono.state.virtual.VirtualBlobValue;
import com.hedera.node.app.service.mono.utils.MiscUtils;
import com.hedera.services.cli.signedstate.DumpStateCommand.EmitSummary;
import com.hedera.services.cli.signedstate.DumpStateCommand.KeyDetails;
import com.hedera.services.cli.signedstate.SignedStateCommand.Verbosity;
import com.hedera.services.cli.utils.Writer;
import com.swirlds.base.utility.Pair;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/** Dump all the data files in a signed state file in textual format.  Output is deterministic for a signed
 * state - sorted by file entity number, etc. - so that
 * comparisons can be made between two signed states that are similar (e.g., mono-service vs modularized service when
 * the same events are run through them).
 *
 * N.B.: Does _not_ include _special files_ which are stored in a different merkle tree.
 */
@SuppressWarnings("java:S106") // "use of system.out/system.err instead of logger" - not needed/desirable for CLI tool
public class DumpFilesSubcommand {

    static void doit(
            @NonNull final SignedStateHolder state,
            @NonNull final Path filesPath,
            @NonNull final EnumSet<KeyDetails> keyDetails,
            @NonNull final EmitSummary emitSummary,
            @NonNull final Verbosity verbosity) {
        new DumpFilesSubcommand(state, filesPath, keyDetails, emitSummary, verbosity).doit();
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
    final Verbosity verbosity;

    DumpFilesSubcommand(
            @NonNull final SignedStateHolder state,
            @NonNull final Path filesPath,
            @NonNull final EnumSet<KeyDetails> keyDetails,
            @NonNull final EmitSummary emitSummary,
            @NonNull final Verbosity verbosity) {
        this.state = state;
        this.filesPath = filesPath;
        this.keyDetails = keyDetails;
        this.emitSummary = emitSummary;
        this.verbosity = verbosity;
    }

    void doit() {
        final var fileStore = state.getFileStore();

        final var collectedFiles = collectFiles(fileStore);
        final var fileSummary = collectedFiles.left();
        final var allFiles = collectedFiles.right();

        if (verbosity == Verbosity.VERBOSE) System.out.printf("=== %d data files ===%n", allFiles.size());

        int reportSize;
        try (@NonNull final var writer = new Writer(filesPath)) {
            if (EmitSummary.YES == emitSummary) reportSummary(writer, fileSummary, allFiles);
            if (EmitSummary.YES == emitSummary) reportFileSizes(writer, allFiles);
            reportFileContents(writer, allFiles);
            if (keyDetails.contains(KeyDetails.STRUCTURE)) reportOnKeyStructure(writer, allFiles);
            if (keyDetails.contains(KeyDetails.STRUCTURE_WITH_IDS)) reportOnKeyIds(writer, allFiles);
            reportSize = writer.getSize();
        } catch (final RuntimeException ex) {
            System.err.printf("*** Exception when writing to '%s':%n", filesPath);
            throw ex;
        }

        if (verbosity == Verbosity.VERBOSE) System.out.printf("=== files report is %d bytes%n", reportSize);
    }

    /** Holds summaries of how many files of each type there are in the store, also how many are missing content. */
    record FileSummary(
            @NonNull Map<VirtualBlobKey.Type, Integer> typeOccurrences,
            @NonNull Map<VirtualBlobKey.Type, Integer> nullValueOccurrences,
            @NonNull Integer nNullMetadataValues) {}

    /** Holds the content and the metadata for a single data file in the store */
    @SuppressWarnings("java:S6218") // "Equals/hashcode methods should be overridden in records containing array fields"
    // not using this with equals
    record HederaFile(@NonNull Integer contractId, @NonNull byte[] contents, @Nullable HFileMeta metadata) {

        @NonNull
        static HederaFile of(@NonNull Integer contractId, @NonNull byte[] contents) {
            return new HederaFile(contractId, contents, null);
        }

        @NonNull
        static HederaFile of(@NonNull Integer contractId, @NonNull byte[] contents, @NonNull HFileMeta metadata) {
            return new HederaFile(contractId, contents, metadata);
        }

        boolean isActive() {
            return null != metadata && !metadata.isDeleted();
        }

        boolean isDeleted() {
            return !isActive();
        }
    }

    /** Emits the summary of the files in the store */
    void reportSummary(
            @NonNull final Writer writer,
            @NonNull final FileSummary fileSummary,
            @NonNull final Map<Integer, HederaFile> allFiles) {
        final var nTotalFiles = fileSummary.typeOccurrences().values().stream().reduce(0, Integer::sum);

        final var nDataFiles = fileSummary.typeOccurrences().get(VirtualBlobKey.Type.FILE_DATA);
        final var nDeletedDataFiles =
                allFiles.values().stream().filter(HederaFile::isDeleted).count();

        final var nMetadataFiles = fileSummary.typeOccurrences().get(VirtualBlobKey.Type.FILE_METADATA);
        final var nActiveDataFiles =
                allFiles.values().stream().filter(HederaFile::isActive).count();
        final var nActiveDataFilesWithMetadata =
                allFiles.values().stream().filter(HederaFile::isActive).count();
        final var nActiveDataFilesMissingMetadata = nActiveDataFiles - nActiveDataFilesWithMetadata;

        Function<VirtualBlobKey.Type, String> wereNull = t -> {
            final var n = fileSummary.nullValueOccurrences().get(t);
            return 0 != n ? " (%d had missing content)".formatted(n) : "";
        };

        final var sb = new StringBuilder(1000);

        sb.append("#   === Summary ===%n".formatted());
        sb.append("#   %7d total files%n".formatted(nTotalFiles));
        sb.append("#   %7d were contract bytecodes%s%n"
                .formatted(
                        fileSummary.typeOccurrences().get(VirtualBlobKey.Type.CONTRACT_BYTECODE),
                        wereNull.apply(VirtualBlobKey.Type.CONTRACT_BYTECODE)));
        sb.append("#   %7d were data, of which %d were deleted, leaving %d live data files%s%s%n"
                .formatted(
                        nDataFiles,
                        nDeletedDataFiles,
                        nDataFiles - nDeletedDataFiles,
                        wereNull.apply(VirtualBlobKey.Type.FILE_DATA),
                        nActiveDataFilesMissingMetadata != 0
                                ? " (%d of those missing metadata)".formatted(nActiveDataFilesMissingMetadata)
                                : ""));
        sb.append("#   %7d were metadata%s%s%n"
                .formatted(
                        nMetadataFiles,
                        wereNull.apply(VirtualBlobKey.Type.FILE_METADATA),
                        0 != fileSummary.nNullMetadataValues
                                ? " (%d, not counted here, had null metadata)"
                                        .formatted(fileSummary.nNullMetadataValues)
                                : ""));
        sb.append("#   %7d were system deleted/entity expiry%s%n"
                .formatted(
                        fileSummary.typeOccurrences().get(VirtualBlobKey.Type.SYSTEM_DELETED_ENTITY_EXPIRY),
                        wereNull.apply(VirtualBlobKey.Type.SYSTEM_DELETED_ENTITY_EXPIRY)));

        writer.write(sb);
    }

    /** Emits a histogram of file content sizes */
    void reportFileSizes(@NonNull final Writer writer, @NonNull final Map<Integer, HederaFile> allFiles) {
        final var histogram = allFiles.values().stream()
                .filter(HederaFile::isActive)
                .map(hf -> hf.contents().length)
                .collect(Collectors.groupingBy(n -> 0 == n ? 0 : (int) Math.log10(n), Collectors.counting()));
        final var maxDigits =
                histogram.keySet().stream().max(Comparator.naturalOrder()).orElse(0);

        final var sb = new StringBuilder(1000);

        sb.append("#   === Content Size Histogram ===%n".formatted());
        sb.append("#        =0: %6d%n".formatted(histogram.getOrDefault(0, 0L)));
        for (int i = 1; i <= maxDigits; i++) {
            sb.append("# %9s: %6d%n".formatted("<=" + (int) Math.pow(10, i), histogram.getOrDefault(i, 0L)));
        }

        writer.write(sb);
    }

    /** Emits the actual content (hexified) for each file), and it's full key */
    void reportFileContents(@NonNull final Writer writer, @NonNull final Map<Integer, HederaFile> allFiles) {
        for (@NonNull
        final var file :
                allFiles.entrySet().stream().sorted(Map.Entry.comparingByKey()).toList()) {
            // Is it correct to _skip_ files without metadata?  Or print them (none currently exist)

            final var contractId = file.getKey();
            final var hf = file.getValue();
            if (hf.isActive()) {
                assert hf.metadata() != null;
                final var sb = new StringBuilder();
                toStringOfByteArray(sb, hf.contents());
                writer.write(
                        "%d,PRESENT,%d,%s,%d,%s,%s%n",
                        contractId,
                        hf.metadata().getExpiry(),
                        quoteForCsv(",", hf.metadata().getMemo()),
                        hf.contents().length,
                        sb,
                        quoteForCsv(",", MiscUtils.describe(hf.metadata().getWacl())));
            } else {
                writer.write("%d,DELETED%n", contractId);
            }
        }
    }

    /** Emits a summary of the _structures_ of the keys securing the data files. */
    void reportOnKeyStructure(@NonNull final Writer writer, @NonNull final Map<Integer, HederaFile> allFiles) {
        final var keySummary = new HashMap<String, Integer>();
        for (@NonNull final var hf : allFiles.values()) {
            if (hf.isDeleted()) continue;
            final var jkey = hf.metadata().getWacl();
            final var sb = new StringBuilder();
            final var b = toStructureSummaryOfJKey(sb, jkey);
            if (!b) {
                sb.setLength(0);
                sb.append("NULL-KEY");
            }
            keySummary.merge(sb.toString(), 1, Integer::sum);
        }

        writer.writeln("=== Key Summary ===");
        keySummary.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEachOrdered(e -> writer.write("%7d: %s%n", e.getValue(), e.getKey()));
    }

    /** Emits a summary of key structure for each file that has a non-trivial key */
    @SuppressWarnings(
            "java:S135") // Reduce total # of break+continue statements to at most one" - disagree it would improve this
    // code
    void reportOnKeyIds(@NonNull final Writer writer, @NonNull final Map<Integer, HederaFile> allFiles) {
        final var keySummary = new HashMap<Integer, String>();
        for (@NonNull final var hf : allFiles.values()) {
            if (hf.isDeleted()) continue;
            final var jkey = hf.metadata().getWacl();
            if (null == jkey) continue;
            final var key = jkey.getKeyList();
            if (key.isEmpty() || !key.isValid() || !key.hasKeyList()) continue;
            if (key.getKeysList().size() <= 1) continue;
            // Have a "complex" key (more than one key in key list)
            final var sb = new StringBuilder();
            final var b = toStructureSummaryOfJKey(sb, jkey);
            if (b) keySummary.put(hf.contractId(), sb.toString());
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

    /** Collects the information for each data file in the file store, also the summaries of all files of all types. */
    @SuppressWarnings("java:S108") // "Nested blocks of code should not be left empty" - not for switches on an enum
    @NonNull
    Pair<FileSummary, Map<Integer, HederaFile>> collectFiles(
            @NonNull final VirtualMapLike<VirtualBlobKey, VirtualBlobValue> fileStore) {
        final var foundFiles = new ConcurrentHashMap<Integer, byte[]>();
        final var foundMetadata = new ConcurrentHashMap<Integer, HFileMeta>();

        final var nType = new ConcurrentHashMap<VirtualBlobKey.Type, Integer>();
        final var nNullValues = new ConcurrentHashMap<VirtualBlobKey.Type, Integer>();
        final var nNulLMetadataValues = new AtomicInteger();

        Stream.of(nType, nNullValues)
                .forEach(m -> EnumSet.allOf(VirtualBlobKey.Type.class).forEach(t -> m.put(t, 0)));

        final int THREAD_COUNT = 8; // size it for a laptop, why not?
        boolean didRunToCompletion = true;
        try {
            fileStore.extractVirtualMapDataC(
                    getStaticThreadManager(),
                    entry -> {
                        final var contractId = entry.key().getEntityNumCode();

                        final var type = entry.key().getType();
                        nType.merge(type, 1, Integer::sum);

                        final var value = entry.value().getData();
                        if (null != value) {
                            switch (type) {
                                case FILE_DATA -> foundFiles.put(contractId, value);

                                case FILE_METADATA -> {
                                    final var metadata = MetadataMapFactory.toAttr(value);
                                    if (null != metadata) {
                                        foundMetadata.put(contractId, metadata);
                                    } else {
                                        nNulLMetadataValues.incrementAndGet();

                                        System.err.printf(
                                                "*** collectFiles file metadata (HFileMeta) null for contract id %d, type %s%n",
                                                contractId, type);
                                    }
                                }
                                case CONTRACT_BYTECODE, SYSTEM_DELETED_ENTITY_EXPIRY -> {}
                            }
                        } else {
                            nNullValues.merge(type, 1, Integer::sum);

                            System.err.printf(
                                    "*** collectFiles file value (bytes) null for contract id %d, type %s%n",
                                    contractId, type);
                        }
                    },
                    THREAD_COUNT);
        } catch (final InterruptedException ex) {
            Thread.currentThread().interrupt();
            didRunToCompletion = false;
        }

        if (!didRunToCompletion) {
            System.err.printf("*** collectFiles interrupted (did not run to completion)%n");
        }

        final var r = new HashMap<Integer, HederaFile>();
        for (@NonNull final var e : foundFiles.entrySet()) {
            final var contractId = e.getKey();
            final var contents = e.getValue();
            final var metadata = foundMetadata.getOrDefault(contractId, null);
            r.put(
                    contractId,
                    null != metadata
                            ? HederaFile.of(contractId, contents, metadata)
                            : HederaFile.of(contractId, contents));
        }

        final var fileSummary = new FileSummary(Map.copyOf(nType), Map.copyOf(nNullValues), nNulLMetadataValues.get());
        return Pair.of(fileSummary, r);
    }
}
