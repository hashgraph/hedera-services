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

package com.hedera.node.app.service.token.impl;

import static com.hedera.hapi.node.transaction.CustomFee.FeeOneOfType.ROYALTY_FEE;
import static java.util.Objects.requireNonNull;

import com.hedera.hapi.node.base.TokenID;
import com.hedera.hapi.node.state.token.Token;
import com.hedera.hapi.node.transaction.CustomFee;
import com.hedera.node.app.service.token.ReadableTokenStore;
import com.hedera.node.app.service.token.impl.schemas.V0490TokenSchema;
import com.swirlds.state.spi.ReadableKVState;
import com.swirlds.state.spi.ReadableStates;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.util.Optional;

/**
 * Default implementation of {@link ReadableTokenStore}.
 */
public class ReadableTokenStoreImpl implements ReadableTokenStore {
    /** The underlying data storage class that holds the token data. */
    private final ReadableKVState<TokenID, Token> tokenState;

    /**
     * Create a new {@link ReadableTokenStoreImpl} instance.
     *
     * @param states The state to use.
     */
    public ReadableTokenStoreImpl(@NonNull final ReadableStates states) {
        requireNonNull(states);
        this.tokenState = states.get(V0490TokenSchema.TOKENS_KEY);
    }

    // FUTURE: remove this method and the TokenMetadata object entirely
    @Override
    @Nullable
    public TokenMetadata getTokenMeta(@NonNull final TokenID id) {
        requireNonNull(id);
        final var token = getTokenLeaf(id);
        if (token.isEmpty()) {
            return null;
        }
        return tokenMetaFrom(token.get());
    }

    @Override
    @Nullable
    public Token get(@NonNull final TokenID id) {
        requireNonNull(id);
        return getTokenLeaf(id).orElse(null);
    }

    // Suppressing the warning that we are passing null values when building TokenMetadata(*OrElse methods)
    @SuppressWarnings("java:S2637")
    private TokenMetadata tokenMetaFrom(final Token token) {
        boolean hasRoyaltyWithFallback = false;
        final var customFees = token.customFees();
        if (!customFees.isEmpty()) {
            for (final var customFee : customFees) {
                if (isRoyaltyWithFallback(customFee)) {
                    hasRoyaltyWithFallback = true;
                    break;
                }
            }
        }
        return new TokenMetadata(
                token.adminKeyOrElse(null),
                token.kycKeyOrElse(null),
                token.wipeKeyOrElse(null),
                token.freezeKeyOrElse(null),
                token.supplyKeyOrElse(null),
                token.feeScheduleKeyOrElse(null),
                token.pauseKeyOrElse(null),
                token.symbol(),
                hasRoyaltyWithFallback,
                token.treasuryAccountId(),
                token.decimals());
    }

    private boolean isRoyaltyWithFallback(final CustomFee fee) {
        return fee.fee().kind() == ROYALTY_FEE && fee.royaltyFee().hasFallbackFee();
    }

    /**
     * Returns the merkleToken leaf for the given tokenId. If the token doesn't exist returns {@code
     * Optional.empty()}
     *
     * @param tokenId given tokenId
     * @return merkleToken leaf for the given tokenId
     */
    private Optional<Token> getTokenLeaf(final TokenID tokenId) {
        final var token = tokenState.get(tokenId);
        return Optional.ofNullable(token);
    }

    /**
     * Returns the number of tokens in the state.
     * @return the number of tokens in the state
     */
    @Override
    public long sizeOfState() {
        return tokenState.size();
    }

    @Override
    public void warm(@NonNull final TokenID tokenId) {
        tokenState.warm(tokenId);
    }
}
