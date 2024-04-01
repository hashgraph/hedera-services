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

import static com.hedera.node.app.service.mono.pbj.PbjConverter.fromPbjKey;
import static com.hedera.node.app.service.mono.statedumpers.files.FilesDumpUtils.reportFileContents;
import static com.hedera.node.app.service.mono.statedumpers.files.FilesDumpUtils.reportFileContentsHeader;
import static com.swirlds.common.threading.manager.AdHocThreadManager.getStaticThreadManager;

import com.hedera.hapi.node.base.FileID;
import com.hedera.hapi.node.base.Key;
import com.hedera.hapi.node.state.file.File;
import com.hedera.node.app.service.mono.files.HFileMeta;
import com.hedera.node.app.service.mono.legacy.core.jproto.JKey;
import com.hedera.node.app.service.mono.state.adapters.VirtualMapLike;
import com.hedera.node.app.service.mono.statedumpers.DumpCheckpoint;
import com.hedera.node.app.service.mono.statedumpers.files.BBMFileId;
import com.hedera.node.app.service.mono.statedumpers.files.BBMHederaFile;
import com.hedera.node.app.service.mono.statedumpers.files.FileStore;
import com.hedera.node.app.service.mono.statedumpers.files.SystemFileType;
import com.hedera.node.app.service.mono.statedumpers.utils.Writer;
import com.hedera.node.app.state.merkle.disk.OnDiskKey;
import com.hedera.node.app.state.merkle.disk.OnDiskValue;
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
    public static Map<BBMFileId, BBMHederaFile> gatherModFiles(
            VirtualMap<OnDiskKey<FileID>, OnDiskValue<File>> source) {
        final var r = new HashMap<BBMFileId, BBMHederaFile>();
        final var threadCount = 8;
        final var files = new ConcurrentLinkedQueue<Pair<BBMFileId, BBMHederaFile>>();
        try {
            VirtualMapLike.from(source)
                    .extractVirtualMapData(
                            getStaticThreadManager(),
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
        if (value.fileId().fileNum() == 13704L) {
            System.out.println(value);
        }
        final var key = Key.newBuilder().keyList(value.keys()).build();
        final var meta = new HFileMeta(
                value.deleted(),
                value.keys() != null ? (JKey) fromPbjKey(key).get() : null,
                value.expirationSecond(),
                value.memo());
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
}
