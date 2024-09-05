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

package com.hedera.node.app.service.token;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.TokenID;
import com.hedera.hapi.node.state.token.TokenRelation;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;

/**
 * Provides read-only methods for getting underlying data for working with TokenRelations.
 *
 * <p>This class is not exported from the module. It is an internal implementation detail.
 */
public interface ReadableTokenRelationStore {
    /**
     * Returns the {@link TokenRelation} with the given IDs. If no such token relation exists,
     * returns {@code null}
     *
     * @param accountId - the id of the account in the token-relation to be retrieved
     * @param tokenId   - the id of the token in the token-relation to be retrieved
     * @return the token-relation with the given IDs, or {@code null} if no such token-relation exists
     */
    @Nullable
    TokenRelation get(@NonNull AccountID accountId, @NonNull TokenID tokenId);

    /**
     * Returns the number of tokens in the state.
     * @return the number of tokens in the state
     */
    long sizeOfState();

    /**
     * Warms the system by preloading a token relationship into memory
     *
     * <p>The default implementation is empty because preloading data into memory is only used for some implementations.
     *
     * @param accountID the account id
     * @param tokenId the token id
     */
    default void warm(@NonNull final AccountID accountID, @NonNull final TokenID tokenId) {}
}
