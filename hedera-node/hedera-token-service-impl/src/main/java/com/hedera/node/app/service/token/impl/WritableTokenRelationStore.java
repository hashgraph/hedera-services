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

import static com.hedera.node.app.service.token.impl.WritableAccountStore.requireNotDefault;
import static com.hedera.node.app.service.token.impl.WritableTokenStore.requireNotDefault;
import static java.util.Objects.requireNonNull;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.TokenID;
import com.hedera.hapi.node.state.common.EntityIDPair;
import com.hedera.hapi.node.state.token.TokenRelation;
import com.hedera.node.app.service.token.impl.schemas.V0490TokenSchema;
import com.hedera.node.app.spi.metrics.StoreMetricsService;
import com.hedera.node.app.spi.metrics.StoreMetricsService.StoreType;
import com.hedera.node.config.data.TokensConfig;
import com.swirlds.config.api.Configuration;
import com.swirlds.state.spi.WritableKVState;
import com.swirlds.state.spi.WritableStates;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.util.Objects;
import java.util.Set;

/**
 * Provides write methods for modifying underlying data storage mechanisms for
 * working with TokenRelations.
 *
 * <p>This class is not exported from the module. It is an internal implementation detail.
 * This class is not complete, it will be extended with other methods like remove, update etc.,
 */
public class WritableTokenRelationStore extends ReadableTokenRelationStoreImpl {
    /** The underlying data storage class that holds the token data. */
    private final WritableKVState<EntityIDPair, TokenRelation> tokenRelState;

    /**
     * Create a new {@link WritableTokenRelationStore} instance.
     *
     * @param states The state to use.
     * @param configuration The configuration used to read the maximum capacity.
     * @param storeMetricsService Service that provides utilization metrics.
     */
    public WritableTokenRelationStore(
            @NonNull final WritableStates states,
            @NonNull final Configuration configuration,
            @NonNull final StoreMetricsService storeMetricsService) {
        super(states);
        this.tokenRelState = requireNonNull(states).get(V0490TokenSchema.TOKEN_RELS_KEY);

        final long maxCapacity = configuration.getConfigData(TokensConfig.class).maxAggregateRels();
        final var storeMetrics = storeMetricsService.get(StoreType.TOKEN_RELATION, maxCapacity);
        tokenRelState.setMetrics(storeMetrics);
    }

    /**
     * Persists a new {@link TokenRelation} into the state.
     *
     * @param tokenRelation - the tokenRelation to be persisted
     */
    public void put(@NonNull final TokenRelation tokenRelation) {
        requireNotDefault(tokenRelation.accountIdOrThrow());
        requireNotDefault(tokenRelation.tokenIdOrThrow());
        tokenRelState.put(
                EntityIDPair.newBuilder()
                        .accountId(tokenRelation.accountId())
                        .tokenId(tokenRelation.tokenId())
                        .build(),
                Objects.requireNonNull(tokenRelation));
    }

    /**
     * Removes a {@link TokenRelation} from the state.
     *
     * @param tokenRelation the {@code TokenRelation} to be removed
     */
    public void remove(@NonNull final TokenRelation tokenRelation) {
        tokenRelState.remove(EntityIDPair.newBuilder()
                .accountId(tokenRelation.accountId())
                .tokenId(tokenRelation.tokenId())
                .build());
    }

    /**
     * Returns the {@link TokenRelation} with the given token number and account number.
     * If no such token relation exists, returns {@code Optional.empty()}
     *
     * @param accountId - the number of the account to be retrieved
     * @param tokenId   - the number of the token to be retrieved
     * @return the token relation with the given token number and account number, or {@code Optional.empty()} if no such
     * token relation exists
     */
    @Nullable
    public TokenRelation getForModify(@NonNull final AccountID accountId, @NonNull final TokenID tokenId) {
        requireNonNull(accountId);
        requireNonNull(tokenId);

        if (AccountID.DEFAULT.equals(accountId) || TokenID.DEFAULT.equals(tokenId)) return null;

        return tokenRelState.getForModify(
                EntityIDPair.newBuilder().accountId(accountId).tokenId(tokenId).build());
    }

    /**
     * Gets the original value associated with the given tokenRelation before any modifications were made to
     * it. The returned value will be {@code null} if the tokenRelation does not exist.
     *
     * @param accountId The accountId of tokenRelation.
     * @param tokenId The tokenId of tokenRelation.
     * @return The original value, or null if there is no such tokenRelation in the state
     */
    @Nullable
    public TokenRelation getOriginalValue(@NonNull final AccountID accountId, @NonNull final TokenID tokenId) {
        requireNonNull(accountId);
        requireNonNull(tokenId);
        return tokenRelState.getOriginalValue(
                EntityIDPair.newBuilder().accountId(accountId).tokenId(tokenId).build());
    }

    /**
     * Returns the set of token relations modified in the state.
     * @return the set of token relations modified in existing state
     */
    public Set<EntityIDPair> modifiedTokens() {
        return tokenRelState.modifiedKeys();
    }
}
