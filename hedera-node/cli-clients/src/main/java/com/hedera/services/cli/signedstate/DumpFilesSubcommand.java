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

import static com.hedera.services.cli.utils.ThingsToStrings.quoteForCsv;
import static com.hedera.services.cli.utils.ThingsToStrings.squashLinesToEscapes;
import static com.hedera.services.cli.utils.ThingsToStrings.toStringOfByteArray;
import static com.hedera.services.cli.utils.ThingsToStrings.toStructureSummaryOfJKey;
import static com.swirlds.common.threading.manager.AdHocThreadManager.getStaticThreadManager;
import static java.util.Objects.nonNull;

import com.hedera.node.app.service.mono.files.HFileMeta;
import com.hedera.node.app.service.mono.files.MetadataMapFactory;
import com.hedera.node.app.service.mono.state.adapters.VirtualMapLike;
import com.hedera.node.app.service.mono.state.merkle.MerkleSpecialFiles;
import com.hedera.node.app.service.mono.state.virtual.VirtualBlobKey;
import com.hedera.node.app.service.mono.state.virtual.VirtualBlobValue;
import com.hedera.node.app.service.mono.utils.MiscUtils;
import com.hedera.services.cli.signedstate.DumpStateCommand.EmitSummary;
import com.hedera.services.cli.signedstate.DumpStateCommand.KeyDetails;
import com.hedera.services.cli.signedstate.DumpStateCommand.OmitContents;
import com.hedera.services.cli.signedstate.SignedStateCommand.Verbosity;
import com.hedera.services.cli.utils.Writer;
import com.hederahashgraph.api.proto.java.FileID;
import com.swirlds.base.utility.Pair;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BinaryOperator;
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
        final var fileStore = state.getFileStore();
        final var specialFileStore = state.getSpecialFileStore();

        final var collectedFiles = collectFiles(fileStore);
        final var collectedSpecialFiles = collectSpecialFiles(specialFileStore);
        final var fileSummary = collectedFiles.left().mergeWith(collectedSpecialFiles.left());
        final var allFiles = merge(this::arb, collectedFiles.right(), collectedSpecialFiles.right());

        if (verbosity == Verbosity.VERBOSE)
            System.out.printf(
                    "=== %d data files (%d from special files store) ===%n",
                    allFiles.size(), collectedSpecialFiles.right().size());

        int reportSize;
        try (@NonNull final var writer = new Writer(filesPath)) {
            if (EmitSummary.YES == emitSummary) reportSummary(writer, fileSummary, allFiles);
            if (EmitSummary.YES == emitSummary) reportFileSizes(writer, allFiles);
            reportFileContentsHeader(writer);
            reportFileContents(writer, allFiles, omitContents);
            if (keyDetails.contains(KeyDetails.STRUCTURE)) reportOnKeyStructure(writer, allFiles);
            if (keyDetails.contains(KeyDetails.STRUCTURE_WITH_IDS)) reportOnKeyIds(writer, allFiles);
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

    /** Holds summaries of how many files of each type there are in the store, also how many are missing content. */
    record FileSummary(
            @NonNull Map<VirtualBlobKey.Type, Integer> typeOccurrences,
            @NonNull Map<VirtualBlobKey.Type, Integer> nullValueOccurrences,
            @NonNull Integer nNullMetadataValues) {

        public FileSummary mergeWith(@NonNull final FileSummary other) {
            final var typeOccurrences = merge(Integer::sum, this.typeOccurrences, other.typeOccurrences);
            final var nullValueOccurrences = merge(Integer::sum, this.nullValueOccurrences, other.nullValueOccurrences);
            final var nNullMetadataValues = this.nNullMetadataValues + other.nNullMetadataValues;
            return new FileSummary(typeOccurrences, nullValueOccurrences, nNullMetadataValues);
        }
    }

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

    /** Holds the content and the metadata for a single data file in the store */
    @SuppressWarnings("java:S6218") // "Equals/hashcode methods should be overridden in records containing array fields"
    // not using this with equals
    record HederaFile(
            @NonNull FileStore fileStore,
            @NonNull Integer fileId,
            @NonNull byte[] contents,
            @Nullable HFileMeta metadata,
            @Nullable SystemFileType systemFileType) {

        @NonNull
        static HederaFile of(final int fileId, @NonNull final byte[] contents) {
            return new HederaFile(FileStore.ORDINARY, fileId, contents, null, SystemFileType.byId.get(fileId));
        }

        @NonNull
        static HederaFile of(final int fileId, @NonNull final byte[] contents, @NonNull final HFileMeta metadata) {
            return new HederaFile(FileStore.ORDINARY, fileId, contents, metadata, SystemFileType.byId.get(fileId));
        }

        @NonNull
        static HederaFile of(@NonNull final FileStore fileStore, final int fileId, @NonNull final byte[] contents) {
            return new HederaFile(fileStore, fileId, contents, null, SystemFileType.byId.get(fileId));
        }

        boolean isActive() {
            if (null != systemFileType) return true;
            if (null != metadata) return !metadata.isDeleted();
            return false;
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

        final var nDataFiles = fileSummary.typeOccurrences().getOrDefault(VirtualBlobKey.Type.FILE_DATA, 0);
        final var nDeletedDataFiles =
                allFiles.values().stream().filter(HederaFile::isDeleted).count();

        final var nMetadataFiles = fileSummary.typeOccurrences().getOrDefault(VirtualBlobKey.Type.FILE_METADATA, 0);
        final var nActiveDataFiles =
                allFiles.values().stream().filter(HederaFile::isActive).count();
        final var nActiveDataFilesWithMetadata = allFiles.values().stream()
                .filter(HederaFile::isActive)
                .filter(hf -> nonNull(hf.metadata()))
                .count();
        final var nActiveDataFilesMissingMetadata = nActiveDataFiles - nActiveDataFilesWithMetadata;

        final var nSpecialFiles = allFiles.values().stream()
                .filter(hf -> hf.fileStore() == FileStore.SPECIAL)
                .count();
        final var nSystemFiles = allFiles.values().stream()
                .filter(hf -> nonNull(hf.systemFileType()))
                .count();

        Function<VirtualBlobKey.Type, String> wereNull = t -> {
            final var n = fileSummary.nullValueOccurrences().getOrDefault(t, 0);
            return 0 != n ? " (%d had missing content)".formatted(n) : "";
        };

        final var sb = new StringBuilder(1000);

        sb.append("#   === Summary ===%n".formatted());
        sb.append("#   %7d total files%n".formatted(nTotalFiles));
        sb.append("#   %7d were contract bytecodes%s%n"
                .formatted(
                        fileSummary.typeOccurrences().getOrDefault(VirtualBlobKey.Type.CONTRACT_BYTECODE, 0),
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
                        fileSummary.typeOccurrences().getOrDefault(VirtualBlobKey.Type.SYSTEM_DELETED_ENTITY_EXPIRY, 0),
                        wereNull.apply(VirtualBlobKey.Type.SYSTEM_DELETED_ENTITY_EXPIRY)));
        sb.append("#   %7d were stored in the special files store%n".formatted(nSpecialFiles));
        sb.append("#   %7d were system files%n".formatted(nSystemFiles));

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

        sb.append("#   === Content Size Histogram (size in bytes) ===%n".formatted());
        sb.append("#          =0: %6d%n".formatted(histogram.getOrDefault(0, 0L)));
        for (int i = 1; i <= maxDigits; i++) {
            sb.append("# %11s: %6d%n".formatted("<=" + (int) Math.pow(10, i), histogram.getOrDefault(i, 0L)));
        }

        writer.write(sb);
    }

    /** Emits the CSV header line for the file contents - **KEEP IN SYNC WITH reportFileContents!!!** */
    void reportFileContentsHeader(@NonNull final Writer writer) {
        final var header = "fileId,PRESENT/DELETED,SPECIAL file,SYSTEM file,length(bytes),expiry,memo,content,key";
        writer.write("%s%n", header);
    }

    /** Emits the actual content (hexified) for each file, and it's full key */
    void reportFileContents(
            @NonNull final Writer writer,
            @NonNull final Map<Integer, HederaFile> allFiles,
            @NonNull final OmitContents omitContents) {
        for (@NonNull
        final var file :
                allFiles.entrySet().stream().sorted(Map.Entry.comparingByKey()).toList()) {

            final var contractId = file.getKey();
            final var hf = file.getValue();
            if (hf.isActive()) {
                final var sb = new StringBuilder();
                if (omitContents == OmitContents.NO) toStringOfByteArray(sb, hf.contents());
                writer.write(
                        "%d,PRESENT,%s,%s,%d,%s,%s,%s,%s%n",
                        contractId,
                        hf.fileStore() == FileStore.SPECIAL ? "SPECIAL" : "",
                        hf.systemFileType() != null ? hf.systemFileType().name() : "",
                        hf.contents().length,
                        hf.metadata() != null ? Long.toString(hf.metadata().getExpiry()) : "",
                        hf.metadata() != null ? quoteForCsv(",", hf.metadata().getMemo()) : "",
                        sb,
                        hf.metadata() != null
                                ? quoteForCsv(
                                        ",",
                                        squashLinesToEscapes(
                                                MiscUtils.describe(hf.metadata().getWacl())))
                                : "");
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
            if (hf.metadata() == null) continue;
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
    @SuppressWarnings("java:S135") // "Reduce total # of break+continue statements to at most one" - disagree
    // it would improve this code
    void reportOnKeyIds(@NonNull final Writer writer, @NonNull final Map<Integer, HederaFile> allFiles) {
        final var keySummary = new HashMap<Integer, String>();
        for (@NonNull final var hf : allFiles.values()) {
            if (hf.isDeleted()) continue;
            if (hf.metadata() == null) continue;
            final var jkey = hf.metadata().getWacl();
            if (null == jkey) continue;
            final var key = jkey.getKeyList();
            if (key.isEmpty() || !key.isValid() || !key.hasKeyList()) continue;
            if (key.getKeysList().size() <= 1) continue;
            // Have a "complex" key (more than one key in key list)
            final var sb = new StringBuilder();
            final var b = toStructureSummaryOfJKey(sb, jkey);
            if (b) keySummary.put(hf.fileId(), sb.toString());
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
        final var nNullMetadataValues = new AtomicInteger();

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
                                        nNullMetadataValues.incrementAndGet();

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

        final var fileSummary = new FileSummary(Map.copyOf(nType), Map.copyOf(nNullValues), nNullMetadataValues.get());
        return Pair.of(fileSummary, r);
    }

    /** Collects the information for each special file in the special file store */
    Pair<FileSummary, Map<Integer, HederaFile>> collectSpecialFiles(
            @NonNull final MerkleSpecialFiles specialFileStore) {
        // The MerkleSpecialFiles does _not_ support iteration.  So we do it by brute force.

        final var r = new HashMap<Integer, HederaFile>();
        final int HIGHEST_NON_USER_ACCOUNT = 1000;
        for (int id = 0; id <= HIGHEST_NON_USER_ACCOUNT; id++) {
            final var fileId = FileID.newBuilder().setFileNum(id).build();
            if (specialFileStore.contains(fileId)) {
                final var contents = specialFileStore.get(fileId);
                final var hfile = HederaFile.of(FileStore.SPECIAL, id, contents);
                r.put(id, hfile);
            }
        }

        final var typeMap = Map.of(VirtualBlobKey.Type.FILE_DATA, r.size());
        final var fileSummary = new FileSummary(typeMap, Map.of(), 0);
        return Pair.of(fileSummary, r);
    }

    /** Merge two (or more) maps.
     *
     * (Seems like it should be provided by the `Map` class, but maybe not.)
     */
    @SafeVarargs
    @NonNull
    @SuppressWarnings("varargs")
    static <K, V> Map<K, V> merge(@NonNull final BinaryOperator<V> mergeFunction, @NonNull final Map<K, V>... maps) {
        return Stream.of(maps)
                .flatMap(m -> m.entrySet().stream())
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, mergeFunction));
    }

    /** Pick `a` or `b` arbitrarily.
     *
     *  Not usually a good idea, but in this case it is really never called, as it a merge function for disjoint sets
     */
    <T> T arb(@NonNull final T a, @NonNull final T b) {
        final var choice = new Random().nextBoolean();
        return choice ? a : b;
    }
}
