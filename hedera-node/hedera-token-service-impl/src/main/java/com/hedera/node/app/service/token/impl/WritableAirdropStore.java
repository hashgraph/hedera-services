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

package com.hedera.node.app.service.token.impl;

import static com.hedera.node.app.service.token.impl.schemas.V0530TokenSchema.AIRDROPS_KEY;
import static java.util.Objects.requireNonNull;

import com.hedera.hapi.node.base.PendingAirdropId;
import com.hedera.hapi.node.state.token.AccountPendingAirdrop;
import com.hedera.node.app.spi.metrics.StoreMetricsService;
import com.hedera.node.config.data.TokensConfig;
import com.swirlds.config.api.Configuration;
import com.swirlds.state.spi.WritableKVState;
import com.swirlds.state.spi.WritableStates;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;

/**
 * Provides write methods for modifying underlying data storage mechanisms for
 * working with Pending Airdrops.
 *
 * <p>This class is not exported from the module. It is an internal implementation detail.
 * This class is not complete, it will be extended with other methods like remove, update etc.,
 */
public class WritableAirdropStore extends ReadableAirdropStoreImpl {
    /**
     * The underlying data storage class that holds the Pending Airdrops data.
     */
    private final WritableKVState<PendingAirdropId, AccountPendingAirdrop> airdropState;

    /**
     * Create a new {@link WritableAirdropStore} instance.
     *
     * @param states The state to use.
     */
    public WritableAirdropStore(
            @NonNull final WritableStates states,
            @NonNull final Configuration configuration,
            @NonNull final StoreMetricsService storeMetricsService) {
        super(states);
        airdropState = states.get(AIRDROPS_KEY);

        final long maxCapacity = configuration.getConfigData(TokensConfig.class).maxAllowedPendingAirdrops();
        final var storeMetrics = storeMetricsService.get(StoreMetricsService.StoreType.AIRDROP, maxCapacity);
        airdropState.setMetrics(storeMetrics);
    }

    /**
     * Persists a new {@link PendingAirdropId} with given {@link AccountPendingAirdrop} into the state.
     * This always replaces the existing value.
     *
     * @param airdropId    - the airdropId to be persisted.
     * @param accountAirdrop - the account airdrop mapping for the given airdropId to be persisted.
     */
    public void put(@NonNull final PendingAirdropId airdropId, @NonNull final AccountPendingAirdrop accountAirdrop) {
        requireNonNull(airdropId);
        requireNonNull(accountAirdrop);
        airdropState.put(airdropId, accountAirdrop);
    }

    /**
     * Removes a {@link PendingAirdropId} from the state.
     *
     * @param airdropId the {@code PendingAirdropId} to be removed
     */
    public void remove(@NonNull final PendingAirdropId airdropId) {
        airdropState.remove(requireNonNull(airdropId));
    }

    /**
     * Returns the {@link AccountPendingAirdrop} with the given airdrop id. If the airdrop contains only NFT return {@code null}.
     * If no such airdrop exists, returns {@code null}
     *
     * @param airdropId - the id of the airdrop, which value should be retrieved
     * @return the fungible airdrop value, or {@code null} if no such
     * airdrop exists
     */
    @Nullable
    public AccountPendingAirdrop getForModify(@NonNull final PendingAirdropId airdropId) {
        requireNonNull(airdropId);
        return airdropState.getForModify(airdropId);
    }

    public boolean contains(final PendingAirdropId pendingId) {
        return airdropState.contains(pendingId);
    }
}
