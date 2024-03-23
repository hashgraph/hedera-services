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

package com.hedera.node.app.statedumpers.files;

import static com.hedera.node.app.service.mono.statedumpers.utils.ThingsToStrings.quoteForCsv;
import static com.hedera.node.app.service.mono.statedumpers.utils.ThingsToStrings.squashLinesToEscapes;
import static com.hedera.node.app.service.mono.statedumpers.utils.ThingsToStrings.toStringOfByteArray;
import static com.swirlds.common.threading.manager.AdHocThreadManager.getStaticThreadManager;

import com.hedera.hapi.node.base.FileID;
import com.hedera.hapi.node.state.file.File;
import com.hedera.node.app.service.mono.state.adapters.VirtualMapLike;
import com.hedera.node.app.service.mono.statedumpers.DumpCheckpoint;
import com.hedera.node.app.service.mono.utils.MiscUtils;
import com.hedera.node.app.state.merkle.disk.OnDiskKey;
import com.hedera.node.app.state.merkle.disk.OnDiskValue;
import com.hedera.node.app.statedumpers.utils.Writer;
import com.swirlds.base.utility.Pair;
import com.swirlds.virtualmap.VirtualMap;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;

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
