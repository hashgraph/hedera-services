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

package com.hedera.node.app.statedumpers.associations;

import com.hedera.node.app.statedumpers.legacy.EntityId;
import com.hedera.node.app.statedumpers.legacy.EntityNumPair;
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
