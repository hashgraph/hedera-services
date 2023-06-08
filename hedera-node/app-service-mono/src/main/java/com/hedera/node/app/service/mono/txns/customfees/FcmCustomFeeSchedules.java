/*
 * Copyright (C) 2021-2023 Hedera Hashgraph, LLC
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

package com.hedera.node.app.service.mono.txns.customfees;

import com.hedera.node.app.service.mono.grpc.marshalling.CustomFeeMeta;
import com.hedera.node.app.service.mono.state.adapters.MerkleMapLike;
import com.hedera.node.app.service.mono.state.merkle.MerkleToken;
import com.hedera.node.app.service.mono.store.models.Id;
import com.hedera.node.app.service.mono.utils.EntityNum;
import java.util.function.Supplier;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;

/** Active CustomFeeSchedules for an entity in the tokens FCMap */
@Singleton
public class FcmCustomFeeSchedules implements CustomFeeSchedules {
    private final Supplier<MerkleMapLike<EntityNum, MerkleToken>> tokens;

    @Inject
    public FcmCustomFeeSchedules(Supplier<MerkleMapLike<EntityNum, MerkleToken>> tokens) {
        this.tokens = tokens;
    }

    @Override
    public CustomFeeMeta lookupMetaFor(final Id tokenId) {
        final var currentTokens = tokens.get();
        final var key = EntityNum.fromModel(tokenId);
        if (!currentTokens.containsKey(key)) {
            return CustomFeeMeta.forMissingLookupOf(tokenId);
        }
        final var merkleToken = currentTokens.get(key);
        return new CustomFeeMeta(tokenId, merkleToken.treasury().asId(), merkleToken.customFeeSchedule());
    }

    public Supplier<MerkleMapLike<EntityNum, MerkleToken>> getTokens() {
        return tokens;
    }

    @Override
    public boolean equals(Object obj) {
        return EqualsBuilder.reflectionEquals(this, obj);
    }

    @Override
    public int hashCode() {
        return HashCodeBuilder.reflectionHashCode(this);
    }
}
