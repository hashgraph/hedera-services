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

import com.hedera.hapi.node.state.common.EntityIDPair;
import com.hedera.hapi.node.state.token.TokenRelation;
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

public class TokenAssociationsDumpUtils {
    public static void dumpModTokenRelations(
            @NonNull final Path path,
            @NonNull final VirtualMap<OnDiskKey<EntityIDPair>, OnDiskValue<TokenRelation>> associations,
            @NonNull final DumpCheckpoint checkpoint, final JsonWriter jsonWriter) {
        try (@NonNull final var writer = new Writer(path)) {
            final var dumpableTokenRelations = gatherTokenRelations(associations);
            System.out.printf(
                    "=== mod token associations report is %d bytes at checkpoint %s%n",
                    writer.getSize(), checkpoint.name());
        }
    }

    @NonNull
    private static Map<EntityIDPair, TokenRelation> gatherTokenRelations(
            VirtualMap<OnDiskKey<EntityIDPair>, OnDiskValue<TokenRelation>> source) {
        final var r = new HashMap<EntityIDPair, TokenRelation>();
        final var threadCount = 8;
        final var TokenRelations = new ConcurrentLinkedQueue<Pair<EntityIDPair, TokenRelation>>();
        try {
            VirtualMapMigration.extractVirtualMapData(
                    getStaticThreadManager(),
                    source,
                    p -> TokenRelations.add(Pair.of(p.left().getKey(), p.right().getValue())),
                    threadCount);
        } catch (final InterruptedException ex) {
            System.err.println("*** Traversal of token associations virtual map interrupted!");
            Thread.currentThread().interrupt();
        }
        TokenRelations.forEach(pair -> r.put(pair.key(), pair.value()));
        return r;
    }
}
