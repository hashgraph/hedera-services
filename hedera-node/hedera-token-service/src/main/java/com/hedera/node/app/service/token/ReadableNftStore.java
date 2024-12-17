/*
 * Copyright (C) 2023-2024 Hedera Hashgraph, LLC
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

package com.hedera.node.app.service.token;

import static java.util.Objects.requireNonNull;

import com.hedera.hapi.node.base.NftID;
import com.hedera.hapi.node.base.TokenID;
import com.hedera.hapi.node.state.token.Nft;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;

/**
 * Provides read-only methods for interacting with the underlying data storage mechanisms for
 * working with Nfts.
 */
public interface ReadableNftStore {
    /**
     * Gets {@link Nft} data for a given {@link TokenID} and serial number.
     * @param id the token id to look up
     * @param serialNumber the serial number to look up
     * @return the {@link Nft} data for the given token id and serial number,
     * or {@code null} if the token serial doesn't exist
     */
    @Nullable
    default Nft get(@NonNull final TokenID id, final long serialNumber) {
        requireNonNull(id);
        final var nftID =
                NftID.newBuilder().tokenId(id).serialNumber(serialNumber).build();
        return get(nftID);
    }

    /**
     * Gets {@link Nft} data for a given {@link NftID}.
     * @param id the unique token id to look up
     * @return the {@link Nft} data for the given unique token id, {@code null} if the id doesn't exist
     */
    @Nullable
    Nft get(@NonNull NftID id);

    /**
     * Returns the number of nfts in the state.
     * @return the number of nfts in the state
     */
    long sizeOfState();

    /**
     * Warms the system by preloading an account into memory
     *
     * <p>The default implementation is empty because preloading data into memory is only used for some implementations.
     *
     * @param nftID the {@link NftID}
     */
    default void warm(@NonNull final NftID nftID) {}
}
