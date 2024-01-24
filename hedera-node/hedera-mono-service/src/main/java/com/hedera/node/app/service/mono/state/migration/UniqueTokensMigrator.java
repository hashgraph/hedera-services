/*
 * Copyright (C) 2022-2024 Hedera Hashgraph, LLC
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
import static com.swirlds.common.threading.manager.AdHocThreadManager.getStaticThreadManager;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.NftID;
import com.hedera.hapi.node.base.Timestamp;
import com.hedera.hapi.node.base.TokenID;
import com.hedera.hapi.node.state.token.Nft;
import com.hedera.node.app.service.mono.ServicesState;
import com.hedera.node.app.service.mono.state.adapters.MerkleMapLike;
import com.hedera.node.app.service.mono.state.adapters.VirtualMapLike;
import com.hedera.node.app.service.mono.state.merkle.MerkleUniqueToken;
import com.hedera.node.app.service.mono.state.virtual.UniqueTokenKey;
import com.hedera.node.app.service.mono.state.virtual.UniqueTokenValue;
import com.hedera.node.app.service.mono.state.virtual.VirtualMapFactory;
import com.hedera.node.app.service.mono.utils.EntityNumPair;
import com.hedera.node.app.spi.state.WritableKVState;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.swirlds.merkle.map.MerkleMap;
import com.swirlds.virtualmap.VirtualMap;
import edu.umd.cs.findbugs.annotations.NonNull;
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

    public static void migrateFromUniqueTokenVirtualMap(
            @NonNull VirtualMapLike<UniqueTokenKey, UniqueTokenValue> fromState,
            @NonNull WritableKVState<NftID, Nft> toState) {

        // TODO: parallelize? Thread-safe?
        try {
            fromState.extractVirtualMapData(
                    getStaticThreadManager(),
                    entry -> {
                        final var monoId = entry.left();
                        final var monoNft = entry.right();
                        final var id = NftID.newBuilder()
                                .tokenId(TokenID.newBuilder().tokenNum(monoId.getNum()))
                                .serialNumber(monoId.getTokenSerial())
                                .build();
                        final var nft = Nft.newBuilder()
                                .nftId(id)
                                .metadata(Bytes.wrap(monoNft.getMetadata()))
                                .mintTime(Timestamp.newBuilder()
                                        .seconds(monoNft.getCreationTime().getSeconds())
                                        .nanos(monoNft.getCreationTime().getNanos()))
                                .ownerId(AccountID.newBuilder()
                                        .shardNum(monoNft.getOwner().shard())
                                        .realmNum(monoNft.getOwner().realm())
                                        .accountNum(monoNft.getOwnerAccountNum()))
                                .ownerNextNftId(NftID.newBuilder()
                                        .tokenId(TokenID.newBuilder()
                                                .shardNum(monoNft.getNext()
                                                        .nftId()
                                                        .shard())
                                                .realmNum(monoNft.getNext()
                                                        .nftId()
                                                        .realm())
                                                .tokenNum(monoNft.getNext()
                                                        .nftId()
                                                        .num()))
                                        .serialNumber(monoNft.getNext().serialNum()))
                                .ownerPreviousNftId(NftID.newBuilder()
                                        .tokenId(TokenID.newBuilder()
                                                .shardNum(monoNft.getPrev()
                                                        .nftId()
                                                        .shard())
                                                .realmNum(monoNft.getPrev()
                                                        .nftId()
                                                        .realm())
                                                .tokenNum(monoNft.getPrev()
                                                        .nftId()
                                                        .num()))
                                        .serialNumber(monoNft.getPrev().serialNum()))
                                .spenderId(AccountID.newBuilder()
                                        .shardNum(monoNft.getSpender().shard())
                                        .realmNum(monoNft.getSpender().realm())
                                        .accountNum(monoNft.getSpender().num()))
                                .build();
                        toState.put(id, nft);
                    },
                    8); // TODO: don't hardcode
        } catch (final InterruptedException ex) {
            System.out.println("Exception");
        }
    }

    private UniqueTokensMigrator() {
        /* disallow construction */
    }
}
