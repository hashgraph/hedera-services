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

package com.hedera.node.app.service.mono.statedumpers.associations;

import com.hedera.node.app.service.mono.state.submerkle.EntityId;
import com.hedera.node.app.service.mono.state.virtual.entities.OnDiskTokenRel;
import com.hedera.node.app.service.mono.utils.EntityNumPair;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.TokenID;
import com.swirlds.base.utility.Pair;
import edu.umd.cs.findbugs.annotations.NonNull;

public record BBMTokenAssociation(
        EntityId accountId,
        EntityId tokenId,
        long balance,
        boolean isFrozen,
        boolean isKycGranted,
        boolean isAutomaticAssociation,
        EntityId prev,
        EntityId next) {

    @NonNull
    static BBMTokenAssociation fromMono(@NonNull final OnDiskTokenRel tokenRel) {
        final var at = toLongsPair(toPair(tokenRel.getKey()));

        return new BBMTokenAssociation(
                entityIdFrom(at.left()),
                entityIdFrom(at.right()),
                tokenRel.getBalance(),
                tokenRel.isFrozen(),
                tokenRel.isKycGranted(),
                tokenRel.isAutomaticAssociation(),
                entityIdFrom(tokenRel.getPrev()),
                entityIdFrom(tokenRel.getNext()));
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

    public static EntityId entityIdFrom(long num) {
        return new EntityId(0L, 0L, num);
    }
}
