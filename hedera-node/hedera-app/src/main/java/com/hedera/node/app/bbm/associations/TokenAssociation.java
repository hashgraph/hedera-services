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

package com.hedera.node.app.bbm.associations;

import com.hedera.hapi.node.state.token.TokenRelation;
import com.hedera.node.app.service.mono.state.submerkle.EntityId;
import com.hedera.node.app.service.mono.utils.EntityNumPair;
import com.hedera.node.app.state.merkle.disk.OnDiskValue;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.TokenID;
import com.swirlds.base.utility.Pair;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;

record TokenAssociation(
        EntityId accountId,
        EntityId tokenId,
        long balance,
        boolean isFrozen,
        boolean isKycGranted,
        boolean isAutomaticAssociation,
        EntityId prev,
        EntityId next) {
    static TokenAssociation fromMod(@NonNull final OnDiskValue<TokenRelation> wrapper) {
        final var value = wrapper.getValue();
        return new TokenAssociation(
                accountIdFromMod(value.accountId()),
                tokenIdFromMod(value.tokenId()),
                value.balance(),
                value.frozen(),
                value.kycGranted(),
                value.automaticAssociation(),
                tokenIdFromMod(value.previousToken()),
                tokenIdFromMod(value.nextToken()));
    }

    @NonNull
    static Pair<AccountID, TokenID> toPair(@NonNull final EntityNumPair enp) {
        final var at = enp.asAccountTokenRel();
        return Pair.of(at.getLeft(), at.getRight());
    }

    @NonNull
    static Pair<Long, Long> toLongsPair(@NonNull final Pair<AccountID, TokenID> pat) {
        return Pair.of(pat.left().getAccountNum(), pat.right().getTokenNum());
    }

    static EntityId accountIdFromMod(@Nullable final com.hedera.hapi.node.base.AccountID accountId) {
        return null == accountId ? EntityId.MISSING_ENTITY_ID : new EntityId(0L, 0L, accountId.accountNumOrThrow());
    }

    static EntityId tokenIdFromMod(@Nullable final com.hedera.hapi.node.base.TokenID tokenId) {
        return null == tokenId ? EntityId.MISSING_ENTITY_ID : new EntityId(0L, 0L, tokenId.tokenNum());
    }
}
