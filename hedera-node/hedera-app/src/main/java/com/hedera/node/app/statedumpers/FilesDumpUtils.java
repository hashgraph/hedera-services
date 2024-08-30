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

package com.hedera.node.app.statedumpers;

import static com.swirlds.common.threading.manager.AdHocThreadManager.getStaticThreadManager;

import com.hedera.hapi.node.base.FileID;
import com.hedera.hapi.node.state.file.File;
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
            @NonNull final DumpCheckpoint checkpoint, final JsonWriter jsonWriter) {
        try (@NonNull final var writer = new Writer(path)) {
            final var dumpableFiles = gatherModFiles(files);
            System.out.printf(
                    "=== mod files report is %d bytes at checkpoint %s%n", writer.getSize(), checkpoint.name());
        }
    }

    @NonNull
    public static Map<FileID, File> gatherModFiles(
            VirtualMap<OnDiskKey<FileID>, OnDiskValue<File>> source) {
        final var r = new HashMap<FileID, File>();
        final var threadCount = 8;
        final var files = new ConcurrentLinkedQueue<Pair<FileID, File>>();
        try {
            VirtualMapMigration.extractVirtualMapData(
                    getStaticThreadManager(),
                    source,
                    p -> files.add(Pair.of(p.left().getKey(), p.right().getValue())),
                    threadCount);
        } catch (final InterruptedException ex) {
            System.err.println("*** Traversal of files virtual map interrupted!");
            Thread.currentThread().interrupt();
        }
        files.forEach(filePair -> r.put(filePair.key(), filePair.value()));
        return r;
    }
}
