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

import static java.util.Objects.requireNonNull;

import com.hedera.hapi.node.base.NftID;
import com.hedera.hapi.node.base.TokenID;
import com.hedera.hapi.node.state.token.Nft;
import com.hedera.hapi.node.state.token.Token;
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
 * working with NFTs.
 *
 * <p>This class is not exported from the module. It is an internal implementation detail.
 * This class is not complete, it will be extended with other methods like remove, update etc.,
 */
public class WritableNftStore extends ReadableNftStoreImpl {
    /** The underlying data storage class that holds the NFT data. */
    private final WritableKVState<NftID, Nft> nftState;

    /**
     * Create a new {@link WritableNftStore} instance.
     *
     * @param states The state to use.
     * @param configuration The configuration used to read the maximum allowed mints.
     * @param storeMetricsService Service that provides utilization metrics.
     */
    public WritableNftStore(
            @NonNull final WritableStates states,
            @NonNull final Configuration configuration,
            @NonNull final StoreMetricsService storeMetricsService) {
        super(states);
        this.nftState = states.get(V0490TokenSchema.NFTS_KEY);

        final long maxCapacity = configuration.getConfigData(TokensConfig.class).nftsMaxAllowedMints();
        final var storeMetrics = storeMetricsService.get(StoreType.NFT, maxCapacity);
        nftState.setMetrics(storeMetrics);
    }

    /**
     * Persists a new {@link Nft} into the state, as well as exporting its ID to the transaction
     * receipt.
     *
     * @param nft - the nft to be persisted.
     */
    public void put(@NonNull final Nft nft) {
        Objects.requireNonNull(nft);
        requireNotDefault(nft.nftId());
        nftState.put(nft.nftId(), nft);
    }

    /**
     * Returns the {@link Token} with the given number using {@link WritableKVState#getForModify}.
     * If no such token exists, returns {@code Optional.empty()}
     * @param id - the number of the unique token id to be retrieved.
     * @return the Nft with the given NftId, or null if no such token exists
     */
    @Nullable
    public Nft getForModify(final NftID id) {
        return nftState.getForModify(requireNonNull(id));
    }

    /**
     * Returns the {@link Nft} with the given number using {@link WritableKVState#getForModify}.
     * If no such token exists, returns {@code Optional.empty()}
     * @param tokenId - the number of the unique token id to be retrieved.
     * @param serialNumber - the serial number of the NFT to be retrieved.
     * @return the Nft with the given tokenId and serial, or null if no such token exists
     */
    @Nullable
    public Nft getForModify(final TokenID tokenId, final long serialNumber) {
        requireNonNull(tokenId);
        return nftState.getForModify(
                NftID.newBuilder().tokenId(tokenId).serialNumber(serialNumber).build());
    }

    /**
     * Returns the set of nfts modified in existing state.
     * @return the set of nfts modified in existing state
     */
    @NonNull
    public Set<NftID> modifiedNfts() {
        return nftState.modifiedKeys();
    }

    /**
     * Removes the {@link Nft} with the given serial number.
     *
     * @param serialNum - the combined unique ID of the NFT to remove
     */
    public void remove(final @NonNull NftID serialNum) {
        nftState.remove(requireNonNull(serialNum));
    }

    /**
     * Removes the {@link Nft} with the given serial number.
     *
     * @param tokenId - the token id of the NFT to remove
     * @param serialNum - the serial number of the NFT to remove
     */
    public void remove(final @NonNull TokenID tokenId, final long serialNum) {
        final var nftId =
                NftID.newBuilder().tokenId(tokenId).serialNumber(serialNum).build();
        requireNotDefault(nftId);
        remove(nftId);
    }

    /**
     * Gets the original value associated with the given nftId before any modifications were made to
     * it. The returned value will be {@code null} if the nftId does not exist.
     *
     * @param nftId The nftId. Cannot be null, otherwise an exception is thrown.
     * @return The original value, or null if there is no such nftId in the state
     * @throws NullPointerException if the accountId is null.
     */
    @Nullable
    public Nft getOriginalValue(@NonNull final NftID nftId) {
        requireNonNull(nftId);
        return nftState.getOriginalValue(nftId);
    }

    private void requireNotDefault(@NonNull final NftID nftId) {
        if (nftId.equals(NftID.DEFAULT)) {
            throw new IllegalArgumentException("Nft ID cannot be default");
        }
    }
}
