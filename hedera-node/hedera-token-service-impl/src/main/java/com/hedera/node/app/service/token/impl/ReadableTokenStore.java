/*
 * Copyright (C) 2022 Hedera Hashgraph, LLC
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
package com.hedera.node.app.service.token.impl;

import com.hedera.node.app.service.mono.legacy.core.jproto.JKey;
import com.hedera.node.app.service.mono.state.merkle.MerkleToken;
import com.hedera.node.app.service.mono.state.submerkle.EntityId;
import com.hedera.node.app.service.mono.state.submerkle.FcCustomFee;
import com.hedera.node.app.spi.state.State;
import com.hedera.node.app.spi.state.States;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.TokenID;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Optional;

/**
 * Provides read-only methods for interacting with the underlying data storage mechanisms for
 * working with Tokens.
 *
 * <p>This class is not exported from the module. It is an internal implementation detail.
 */
public class ReadableTokenStore {
    /** The underlying data storage class that holds the token data. */
    private final State<Long, MerkleToken> tokenState;

    /**
     * Create a new {@link ReadableTokenStore} instance.
     *
     * @param states The state to use.
     */
    public ReadableTokenStore(@NonNull final States states) {
        this.tokenState = states.get("TOKENS");
    }

    public record TokenMetadata(
            Optional<JKey> adminKey,
            Optional<JKey> kycKey,
            Optional<JKey> wipeKey,
            Optional<JKey> freezeKey,
            Optional<JKey> supplyKey,
            Optional<JKey> feeScheduleKey,
            Optional<JKey> pauseKey,
            boolean hasRoyaltyWithFallback,
            EntityId treasury) {}

    public record TokenMetaOrLookupFailureReason(
            TokenMetadata metadata, ResponseCodeEnum failureReason) {
        public boolean failed() {
            return failureReason != null;
        }
    }

    /**
     * Returns the token metadata needed for signing requirements. If the token doesn't exist
     * returns failureReason. If the token exists , the failure reason will be null.
     *
     * @param id token id being looked up
     * @return token's metadata
     */
    public TokenMetaOrLookupFailureReason getTokenMeta(final TokenID id) {
        final var token = getTokenLeaf(id);

        if (token.isEmpty()) {
            return new TokenMetaOrLookupFailureReason(null, ResponseCodeEnum.INVALID_TOKEN_ID);
        }
        return new TokenMetaOrLookupFailureReason(tokenMetaFrom(token.get()), null);
    }

    private TokenMetadata tokenMetaFrom(final MerkleToken token) {
        boolean hasRoyaltyWithFallback = false;
        final var customFees = token.customFeeSchedule();
        if (!customFees.isEmpty()) {
            for (final var customFee : customFees) {
                if (isRoyaltyWithFallback(customFee)) {
                    hasRoyaltyWithFallback = true;
                    break;
                }
            }
        }
        return new TokenMetadata(
                token.adminKey(),
                token.kycKey(),
                token.wipeKey(),
                token.freezeKey(),
                token.supplyKey(),
                token.feeScheduleKey(),
                token.pauseKey(),
                hasRoyaltyWithFallback,
                token.treasury());
    }

    private boolean isRoyaltyWithFallback(final FcCustomFee fee) {
        return fee.getFeeType() == FcCustomFee.FeeType.ROYALTY_FEE
                && fee.getRoyaltyFeeSpec().fallbackFee() != null;
    }

    /**
     * Returns the merkleToken leaf for the given tokenId. If the token doesn't exist returns {@code
     * Optional.empty()}
     *
     * @param id given tokenId
     * @return merkleToken leaf for the given tokenId
     */
    private Optional<MerkleToken> getTokenLeaf(final TokenID id) {
        final var token = tokenState.get(id.getTokenNum());
        if (token.isEmpty()) {
            return Optional.empty();
        }
        return token;
    }
}
