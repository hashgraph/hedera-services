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

package com.hedera.node.app.bbm.nfts;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.NftID;
import com.hedera.hapi.node.state.token.Nft;
import com.hedera.node.app.service.mono.state.submerkle.EntityId;
import com.hedera.node.app.service.mono.state.submerkle.RichInstant;
import com.hedera.node.app.service.mono.utils.NftNumPair;
import com.hedera.node.app.state.merkle.disk.OnDiskValue;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;

record UniqueToken(
        EntityId owner,
        EntityId spender,
        @NonNull RichInstant creationTime,
        @NonNull byte[] metadata,
        @NonNull NftNumPair previous,
        @NonNull NftNumPair next) {

    static UniqueToken fromMod(@NonNull final OnDiskValue<Nft> wrapper) {
        final var value = wrapper.getValue();
        return new UniqueToken(
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
}
