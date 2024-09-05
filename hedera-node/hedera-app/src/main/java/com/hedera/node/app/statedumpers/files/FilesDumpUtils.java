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

import static com.swirlds.common.threading.manager.AdHocThreadManager.getStaticThreadManager;

import com.hedera.hapi.node.base.FileID;
import com.hedera.hapi.node.base.Key;
import com.hedera.hapi.node.state.file.File;
import com.hedera.node.app.statedumpers.DumpCheckpoint;
import com.hedera.node.app.statedumpers.legacy.HFileMeta;
import com.hedera.node.app.statedumpers.legacy.JKey;
import com.hedera.node.app.statedumpers.utils.ThingsToStrings;
import com.hedera.node.app.statedumpers.utils.Writer;
import com.swirlds.base.utility.Pair;
import com.swirlds.state.merkle.disk.OnDiskKey;
import com.swirlds.state.merkle.disk.OnDiskValue;
import com.swirlds.virtualmap.VirtualMap;
import com.swirlds.virtualmap.VirtualMapMigration;
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
    public static Map<BBMFileId, BBMHederaFile> gatherModFiles(
            VirtualMap<OnDiskKey<FileID>, OnDiskValue<File>> source) {
        final var r = new HashMap<BBMFileId, BBMHederaFile>();
        final var threadCount = 8;
        final var files = new ConcurrentLinkedQueue<Pair<BBMFileId, BBMHederaFile>>();
        try {
            VirtualMapMigration.extractVirtualMapData(
                    getStaticThreadManager(),
                    source,
                    p -> files.add(Pair.of(BBMFileId.fromMod(p.left().getKey()), fromMod(p.right()))),
                    threadCount);
        } catch (final InterruptedException ex) {
            System.err.println("*** Traversal of files virtual map interrupted!");
            Thread.currentThread().interrupt();
        }
        files.forEach(filePair -> r.put(filePair.key(), filePair.value()));
        return r;
    }

    static BBMHederaFile fromMod(@NonNull final OnDiskValue<File> wrapper) {
        final var value = wrapper.getValue();
        JKey key;
        try {
            key = JKey.mapKey(Key.newBuilder().keyList(value.keys()).build());
        } catch (Exception e) {
            key = null;
        }
        final var meta = new HFileMeta(value.deleted(), key, value.expirationSecond(), value.memo());
        return new BBMHederaFile(
                FileStore.ORDINARY,
                (int) value.fileId().fileNum(),
                value.contents().toByteArray(),
                meta,
                SystemFileType.byId.get((int) value.fileId().fileNum()));
    }

    private static void reportOnFiles(
            @NonNull final Writer writer, @NonNull final Map<BBMFileId, BBMHederaFile> files) {
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
            @NonNull final Writer writer, @NonNull final Map<BBMFileId, BBMHederaFile> allFiles) {
        for (@NonNull
        final var file :
                allFiles.entrySet().stream().sorted(Map.Entry.comparingByKey()).toList()) {
            final var fileNum = file.getKey().fileNum();
            final var hf = file.getValue();
            if (hf.isActive()) {
                final var sb = new StringBuilder();
                ThingsToStrings.toStringOfByteArray(sb, hf.contents());
                writer.write(
                        "%d,PRESENT,%s,%s,%d,%s,%s,%s,%s%n",
                        fileNum,
                        hf.fileStore() == FileStore.SPECIAL ? "SPECIAL" : "",
                        hf.systemFileType() != null ? hf.systemFileType().name() : "",
                        hf.contents() != null ? hf.contents().length : 0,
                        Long.toString(hf.metadata().getExpiry()),
                        ThingsToStrings.quoteForCsv(",", hf.metadata().getMemo()),
                        sb,
                        ThingsToStrings.quoteForCsv(
                                ",",
                                ThingsToStrings.squashLinesToEscapes(
                                        ThingsToStrings.describe(hf.metadata().getWacl()))));
            } else {
                writer.write("%d,DELETED%n", fileNum);
            }
        }
    }
}
