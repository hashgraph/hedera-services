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

package com.hedera.node.app.statedumpers.nfts;

import static com.hedera.node.app.service.mono.statedumpers.nfts.UniqueTokenDumpUtils.reportOnUniques;
import static com.swirlds.common.threading.manager.AdHocThreadManager.getStaticThreadManager;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.NftID;
import com.hedera.hapi.node.state.token.Nft;
import com.hedera.node.app.service.mono.state.adapters.VirtualMapLike;
import com.hedera.node.app.service.mono.state.submerkle.EntityId;
import com.hedera.node.app.service.mono.state.submerkle.RichInstant;
import com.hedera.node.app.service.mono.statedumpers.DumpCheckpoint;
import com.hedera.node.app.service.mono.statedumpers.nfts.BBMUniqueToken;
import com.hedera.node.app.service.mono.statedumpers.nfts.BBMUniqueTokenId;
import com.hedera.node.app.service.mono.statedumpers.utils.Writer;
import com.hedera.node.app.service.mono.utils.NftNumPair;
import com.hedera.node.app.state.merkle.disk.OnDiskKey;
import com.hedera.node.app.state.merkle.disk.OnDiskValue;
import com.swirlds.base.utility.Pair;
import com.swirlds.virtualmap.VirtualMap;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;

public class UniqueTokenDumpUtils {
    public static void dumpModUniqueTokens(
            @NonNull final Path path,
            @NonNull final VirtualMap<OnDiskKey<NftID>, OnDiskValue<Nft>> uniques,
            @NonNull final DumpCheckpoint checkpoint) {
        try (@NonNull final var writer = new Writer(path)) {
            final var dumpableUniques = gatherUniques(uniques);
            reportOnUniques(writer, dumpableUniques);
            System.out.printf(
                    "=== mod  uniques report is %d bytes at checkpoint %s%n", writer.getSize(), checkpoint.name());
        }
    }

    @NonNull
    private static Map<BBMUniqueTokenId, BBMUniqueToken> gatherUniques(
            @NonNull final VirtualMap<OnDiskKey<NftID>, OnDiskValue<Nft>> source) {
        final var r = new HashMap<BBMUniqueTokenId, BBMUniqueToken>();
        final var threadCount = 5;
        final var mappings = new ConcurrentLinkedQueue<Pair<BBMUniqueTokenId, BBMUniqueToken>>();
        try {
            VirtualMapLike.from(source)
                    .extractVirtualMapDataC(
                            getStaticThreadManager(),
                            p -> mappings.add(Pair.of(fromMod(p.left().getKey()), fromMod(p.right()))),
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

    static BBMUniqueToken fromMod(@NonNull final OnDiskValue<Nft> wrapper) {
        final var value = wrapper.getValue();
        return new BBMUniqueToken(
                idFromMod(value.ownerId()),
                idFromMod(value.spenderId()),
                new RichInstant(value.mintTime().seconds(), value.mintTime().nanos()),
                value.metadata().toByteArray(),
                idPairFromMod(value.ownerPreviousNftId()),
                idPairFromMod(value.ownerNextNftId()));
    }

    private static EntityId idFromMod(@Nullable final AccountID accountId) {
        return null == accountId ? EntityId.MISSING_ENTITY_ID : new EntityId(0L, 0L, accountId.accountNumOrThrow());
    }

    private static NftNumPair idPairFromMod(@Nullable final NftID nftId) {
        return null == nftId
                ? NftNumPair.MISSING_NFT_NUM_PAIR
                : NftNumPair.fromLongs(nftId.tokenIdOrThrow().tokenNum(), nftId.serialNumber());
    }

    static BBMUniqueTokenId fromMod(@NonNull final NftID nftID) {
        return new BBMUniqueTokenId(nftID.tokenIdOrThrow().tokenNum(), nftID.serialNumber());
    }
}
