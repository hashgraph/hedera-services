/*
 * Copyright (C) 2021-2022 Hedera Hashgraph, LLC
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
package com.hedera.services.txns.customfees;

import com.hedera.services.grpc.marshalling.CustomFeeMeta;
import com.hedera.services.state.merkle.MerkleToken;
import com.hedera.services.store.models.Id;
import com.hedera.services.utils.EntityNum;
import com.swirlds.merkle.map.MerkleMap;
import java.util.function.Supplier;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;

/** Active CustomFeeSchedules for an entity in the tokens FCMap */
@Singleton
public class FcmCustomFeeSchedules implements CustomFeeSchedules {
    private final Supplier<MerkleMap<EntityNum, MerkleToken>> tokens;

    @Inject
    public FcmCustomFeeSchedules(Supplier<MerkleMap<EntityNum, MerkleToken>> tokens) {
        this.tokens = tokens;
    }

    @Override
    public CustomFeeMeta lookupMetaFor(Id tokenId) {
        final var currentTokens = tokens.get();
        final var key = EntityNum.fromModel(tokenId);
        if (!currentTokens.containsKey(key)) {
            return CustomFeeMeta.MISSING_META;
        }
        final var merkleToken = currentTokens.get(key);
        return new CustomFeeMeta(
                tokenId, merkleToken.treasury().asId(), merkleToken.customFeeSchedule());
    }

    public Supplier<MerkleMap<EntityNum, MerkleToken>> getTokens() {
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
