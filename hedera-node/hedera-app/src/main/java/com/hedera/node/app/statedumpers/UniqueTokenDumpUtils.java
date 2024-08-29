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

import com.hedera.hapi.node.base.NftID;
import com.hedera.hapi.node.state.token.Nft;
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

public class UniqueTokenDumpUtils {
    public static void dumpModUniqueTokens(
            @NonNull final Path path,
            @NonNull final VirtualMap<OnDiskKey<NftID>, OnDiskValue<Nft>> uniques,
            @NonNull final DumpCheckpoint checkpoint,
            final JsonWriter jsonWriter) {
        final var dumpableUniques = gatherUniques(uniques);
        jsonWriter.write(dumpableUniques, path.toString());
        System.out.printf("Nfts of size %d at checkpoint %s%n", dumpableUniques.size(), checkpoint.name());
    }

    @NonNull
    private static Map<NftID, Nft> gatherUniques(
            @NonNull final VirtualMap<OnDiskKey<NftID>, OnDiskValue<Nft>> source) {
        final var r = new HashMap<NftID, Nft>();
        final var threadCount = 5;
        final var mappings = new ConcurrentLinkedQueue<Pair<NftID, Nft>>();
        try {
            VirtualMapMigration.extractVirtualMapDataC(
                    getStaticThreadManager(),
                    source,
                    p -> mappings.add(Pair.of(p.left().getKey(), p.right().getValue())),
                    threadCount);
        } catch (final InterruptedException ex) {
            System.err.println("*** Traversal of uniques virtual map interrupted!");
            Thread.currentThread().interrupt();
        }
        // Consider in the future: Use another thread to pull things off the queue as they're put on by the
        // virtual map traversal
        while (!mappings.isEmpty()) {
            final var mapping = mappings.poll();
            r.put(mapping.key(), mapping.value());
        }
        return r;
    }
}
