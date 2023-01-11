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
import com.hedera.node.app.service.mono.ledger.TransactionalLedger;
import com.hedera.node.app.service.mono.ledger.properties.TokenProperty;
import com.hedera.node.app.service.mono.state.merkle.MerkleToken;
import com.hedera.node.app.service.mono.store.models.Id;
import com.hederahashgraph.api.proto.java.TokenID;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;

/** Active CustomFeeSchedules for an entity in the tokens ledger */
@Singleton
public class LedgerCustomFeeSchedules implements CustomFeeSchedules {
    private TransactionalLedger<TokenID, TokenProperty, MerkleToken> tokensLedger;

    @Inject
    public LedgerCustomFeeSchedules(
            TransactionalLedger<TokenID, TokenProperty, MerkleToken> tokensLedger) {
        this.tokensLedger = tokensLedger;
    }

    @Override
    public CustomFeeMeta lookupMetaFor(final Id tokenId) {
        if (!tokensLedger.exists(tokenId.asGrpcToken())) {
            return CustomFeeMeta.forMissingLookupOf(tokenId);
        }
        final var merkleToken = tokensLedger.getImmutableRef(tokenId.asGrpcToken());

        return new CustomFeeMeta(
                tokenId, merkleToken.treasury().asId(), merkleToken.customFeeSchedule());
    }

    public TransactionalLedger<TokenID, TokenProperty, MerkleToken> getTokens() {
        return tokensLedger;
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
