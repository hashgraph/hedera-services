/*
 * Copyright (C) 2024 Hedera Hashgraph, LLC
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

package com.hedera.node.app.bbm.files;

import static com.hedera.node.app.bbm.utils.ThingsToStrings.quoteForCsv;
import static com.hedera.node.app.bbm.utils.ThingsToStrings.squashLinesToEscapes;
import static com.hedera.node.app.bbm.utils.ThingsToStrings.toStringOfByteArray;
import static com.swirlds.common.threading.manager.AdHocThreadManager.getStaticThreadManager;

import com.hedera.hapi.node.base.FileID;
import com.hedera.hapi.node.state.file.File;
import com.hedera.node.app.bbm.DumpCheckpoint;
import com.hedera.node.app.bbm.utils.Writer;
import com.hedera.node.app.service.mono.files.HFileMeta;
import com.hedera.node.app.service.mono.files.MetadataMapFactory;
import com.hedera.node.app.service.mono.state.adapters.VirtualMapLike;
import com.hedera.node.app.service.mono.state.virtual.VirtualBlobKey;
import com.hedera.node.app.service.mono.state.virtual.VirtualBlobValue;
import com.hedera.node.app.service.mono.utils.MiscUtils;
import com.hedera.node.app.state.merkle.disk.OnDiskKey;
import com.hedera.node.app.state.merkle.disk.OnDiskValue;
import com.swirlds.base.utility.Pair;
import com.swirlds.virtualmap.VirtualMap;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.nio.file.Path;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

public class FilesDumpUtils {

    private FilesDumpUtils() {
        // Utility class
    }

    public static void dumpModFiles(
            @NonNull final Path path,
            @NonNull final VirtualMap<OnDiskKey<FileID>, OnDiskValue<File>> files,
            @NonNull final DumpCheckpoint checkpoint) {
        try (@NonNull final var writer = new Writer(path)) {
            final var dumpableFiles = gatherModFiles(files);
            reportOnFiles(writer, dumpableFiles);
            System.out.printf(
                    "=== mod files report is %d bytes at checkpoint %s%n", writer.getSize(), checkpoint.name());
        }
    }

    public static void dumpMonoFiles(
            @NonNull final Path path,
            @NonNull final VirtualMap<VirtualBlobKey, VirtualBlobValue> files,
            @NonNull final DumpCheckpoint checkpoint) {
        try (@NonNull final var writer = new Writer(path)) {
            final var dumpableFiles = gatherMonoFiles(files);
            reportOnFiles(writer, dumpableFiles);
            System.out.printf(
                    "=== mono files report is %d bytes at checkpoint %s%n", writer.getSize(), checkpoint.name());
        }
    }

    @NonNull
    public static Map<FileId, HederaFile> gatherModFiles(VirtualMap<OnDiskKey<FileID>, OnDiskValue<File>> source) {
        final var r = new HashMap<FileId, HederaFile>();
        final var threadCount = 8;
        final var files = new ConcurrentLinkedQueue<Pair<FileId, HederaFile>>();
        try {
            VirtualMapLike.from(source)
                    .extractVirtualMapData(
                            getStaticThreadManager(),
                            p -> files.add(Pair.of(FileId.fromMod(p.left().getKey()), HederaFile.fromMod(p.right()))),
                            threadCount);
        } catch (final InterruptedException ex) {
            System.err.println("*** Traversal of files virtual map interrupted!");
            Thread.currentThread().interrupt();
        }
        files.forEach(filePair -> r.put(filePair.key(), filePair.value()));
        return r;
    }

    /** Collects the information for each data file in the file store, also the summaries of all files of all types. */
    @SuppressWarnings("java:S108") // "Nested blocks of code should not be left empty" - not for switches on an enum
    @NonNull
    private static Map<FileId, HederaFile> gatherMonoFiles(
            @NonNull final VirtualMap<VirtualBlobKey, VirtualBlobValue> source) {
        final var foundFiles = new ConcurrentHashMap<Integer, byte[]>();
        final var foundMetadata = new ConcurrentHashMap<Integer, HFileMeta>();

        final var nType = new ConcurrentHashMap<VirtualBlobKey.Type, Integer>();
        final var nNullValues = new ConcurrentHashMap<VirtualBlobKey.Type, Integer>();
        final var nNullMetadataValues = new AtomicInteger();

        Stream.of(nType, nNullValues)
                .forEach(m -> EnumSet.allOf(VirtualBlobKey.Type.class).forEach(t -> m.put(t, 0)));

        final int THREAD_COUNT = 8;
        boolean didRunToCompletion = true;
        try {
            VirtualMapLike.from(source)
                    .extractVirtualMapDataC(
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

        final var r = new HashMap<FileId, HederaFile>();
        for (@NonNull final var e : foundFiles.entrySet()) {
            final var contractId = e.getKey();
            final var contents = e.getValue();
            final var metadata = foundMetadata.getOrDefault(contractId, null);
            r.put(
                    FileId.fromMono(contractId),
                    null != metadata
                            ? HederaFile.of(contractId, contents, metadata)
                            : HederaFile.of(contractId, contents));
        }

        return r;
    }

    private static void reportOnFiles(@NonNull final Writer writer, @NonNull final Map<FileId, HederaFile> files) {
        reportFileContentsHeader(writer);
        reportFileContents(writer, files);
        writer.writeln("");
    }

    /** Emits the CSV header line for the file contents - **KEEP IN SYNC WITH reportFileContents!!!** */
    private static void reportFileContentsHeader(@NonNull final Writer writer) {
        final var header = "fileId,PRESENT/DELETED,SPECIAL file,SYSTEM file,length(bytes),expiry,memo,content,key";
        writer.write("%s%n", header);
    }

    /** Emits the actual content (hexified) for each file, and it's full key */
    private static void reportFileContents(
            @NonNull final Writer writer, @NonNull final Map<FileId, HederaFile> allFiles) {
        for (@NonNull
        final var file :
                allFiles.entrySet().stream().sorted(Map.Entry.comparingByKey()).toList()) {
            final var fileId = file.getKey().fileNum();
            final var hf = file.getValue();
            if (hf.isActive()) {
                final var sb = new StringBuilder();
                toStringOfByteArray(sb, hf.contents());
                writer.write(
                        "%d,PRESENT,%s,%s,%d,%s,%s,%s,%s%n",
                        fileId,
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
                writer.write("%d,DELETED%n", fileId);
            }
        }
    }
}
