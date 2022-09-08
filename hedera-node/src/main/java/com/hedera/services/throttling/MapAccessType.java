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
package com.hedera.services.throttling;

import com.hedera.services.utils.MiscUtils;
import java.util.List;

/**
 * Enumerates the type of {@link com.swirlds.merkle.map.MerkleMap} and {@link
 * com.swirlds.virtualmap.VirtualMap} operations needed to auto-renew or auto-remove expired
 * entities.
 *
 * <p><b>IMPORTANT:</b> since expiration is only enabled for contracts at this time, not all map
 * access types are included now.
 */
public enum MapAccessType {
    /** To get a candidate expired contract, auto-renew account, or token treasury. */
    ACCOUNTS_GET,
    /** To update properties of a contract, auto-renew account, or token treasury. */
    ACCOUNTS_GET_FOR_MODIFY,
    /** To remove an expired contract that is deleted or past its grace period. */
    ACCOUNTS_REMOVE,
    /** To remove the bytecode of an expired contract that is deleted or past its grace period. */
    BLOBS_REMOVE,
    /** To get a head NFT's {@code next} link before removal. */
    NFTS_GET,
    /** To remove a head NFT. */
    NFTS_REMOVE,
    /**
     * To update an NFT's owner on treasury return, or {@code prev} link after returning the
     * preceding head NFT.
     */
    NFTS_GET_FOR_MODIFY,
    /**
     * To get the {@link com.hedera.services.state.virtual.IterableContractValue} for a storage key.
     */
    STORAGE_GET,
    /**
     * To replace a {@link com.hedera.services.state.virtual.IterableContractValue} with modified
     * links.
     */
    STORAGE_PUT,
    /** To remove a key/value pair from storage. */
    STORAGE_REMOVE,
    /** To get metadata of a token associated to an expired contract being removed from state. */
    TOKENS_GET,
    /** To get a head token association's {@code next} link before removal. */
    TOKEN_ASSOCIATIONS_GET,
    /** To remove a head token association. */
    TOKEN_ASSOCIATIONS_REMOVE,
    /** To update a token association {@code prev} after removing the preceding head association. */
    TOKEN_ASSOCIATIONS_GET_FOR_MODIFY;

    public static List<MapAccessType> csvAccessList(final String propertyValue) {
        return MiscUtils.csvList(propertyValue, MapAccessType::valueOf);
    }
}
