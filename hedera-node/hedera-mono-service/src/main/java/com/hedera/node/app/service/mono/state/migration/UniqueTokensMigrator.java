/*
 * Copyright (C) 2022-2023 Hedera Hashgraph, LLC
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

package com.hedera.node.app.service.mono.state.migration;

import static com.hedera.node.app.service.mono.utils.MiscUtils.forEach;

import com.hedera.node.app.service.mono.ServicesState;
import com.hedera.node.app.service.mono.state.adapters.MerkleMapLike;
import com.hedera.node.app.service.mono.state.merkle.MerkleUniqueToken;
import com.hedera.node.app.service.mono.state.virtual.UniqueTokenKey;
import com.hedera.node.app.service.mono.state.virtual.UniqueTokenValue;
import com.hedera.node.app.service.mono.state.virtual.VirtualMapFactory;
import com.hedera.node.app.service.mono.utils.EntityNumPair;
import com.swirlds.merkle.map.MerkleMap;
import com.swirlds.virtualmap.VirtualMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class UniqueTokensMigrator {
    private static final Logger LOG = LogManager.getLogger(UniqueTokensMigrator.class);

    /**
     * Migrate tokens from MerkleMap data structure to VirtualMap data structure.
     *
     * @param initializingState the ServicesState containing the MerkleMap to migrate.
     */
    public static void migrateFromUniqueTokenMerkleMap(final ServicesState initializingState) {
        final var virtualMapFactory = new VirtualMapFactory();
        final var currentData = initializingState.uniqueTokens();
        if (currentData.isVirtual()) {
            // Already done here
            LOG.info("UniqueTokens is already virtualized. Skipping migration.");
            return;
        }

        final MerkleMap<EntityNumPair, MerkleUniqueToken> legacyUniqueTokens = currentData.merkleMap();
        final AtomicReference<VirtualMap<UniqueTokenKey, UniqueTokenValue>> virtualMapRef =
                new AtomicReference<>(virtualMapFactory.newVirtualizedUniqueTokenStorage());
        final AtomicInteger count = new AtomicInteger();

        forEach(MerkleMapLike.from(legacyUniqueTokens), (entityNumPair, legacyToken) -> {
            final var numSerialPair = entityNumPair.asTokenNumAndSerialPair();
            final var newTokenKey = new UniqueTokenKey(numSerialPair.getLeft(), numSerialPair.getRight());
            final var newTokenValue = UniqueTokenValue.from(legacyToken);
            virtualMapRef.get().put(newTokenKey, newTokenValue);
            final int currentCount = count.incrementAndGet();
            // Create a new virtual map copy every few tokens to make sure they can be flushed to disk
            if (currentCount % 10000 == 0) {
                final VirtualMap<UniqueTokenKey, UniqueTokenValue> currentCopy = virtualMapRef.get();
                virtualMapRef.set(currentCopy.copy());
                currentCopy.release();
                // Future work: may need to wait until currentCopy is actually flushed to disk
            }
        });

        initializingState.setChild(StateChildIndices.UNIQUE_TOKENS, virtualMapRef.get());
        LOG.info("Migrated {} unique tokens", count.get());
    }

    private UniqueTokensMigrator() {
        /* disallow construction */
    }
}
