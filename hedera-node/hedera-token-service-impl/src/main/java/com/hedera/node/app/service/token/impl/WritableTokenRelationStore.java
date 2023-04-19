/*
 * Copyright (C) 2022-2023 Hedera Hashgraph, LLC
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

import com.hedera.hapi.node.state.token.TokenRelation;
import com.hedera.node.app.service.mono.utils.EntityNumPair;
import com.hedera.node.app.spi.state.WritableKVState;
import com.hedera.node.app.spi.state.WritableKVStateBase;
import com.hedera.node.app.spi.state.WritableStates;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Provides write methods for modifying underlying data storage mechanisms for
 * working with TokenRelations.
 *
 * <p>This class is not exported from the module. It is an internal implementation detail.
 * This class is not complete, it will be extended with other methods like remove, update etc.,
 */
public class WritableTokenRelationStore {
    /** The underlying data storage class that holds the token data. */
    private final WritableKVState<EntityNumPair, TokenRelation> tokenRelState;

    /**
     * Create a new {@link WritableTokenRelationStore} instance.
     *
     * @param states The state to use.
     */
    public WritableTokenRelationStore(@NonNull final WritableStates states) {
        this.tokenRelState = requireNonNull(states).get("TOKEN_RELATIONS");
    }

    /**
     * Persists a new {@link TokenRelation} into the state
     *
     * @param tokenRelation - the tokenRelation to be persisted
     */
    public void put(@NonNull final TokenRelation tokenRelation) {
        requireNonNull(tokenRelState)
                .put(
                        EntityNumPair.fromLongs(tokenRelation.tokenNumber(), tokenRelation.accountNumber()),
                        Objects.requireNonNull(tokenRelation));
    }

    /**
     * Commits the changes to the underlying data storage
     */
    public void commit() {
        requireNonNull(tokenRelState);
        ((WritableKVStateBase<EntityNumPair, TokenRelation>) tokenRelState).commit();
    }

    /**
     * Returns the {@link TokenRelation} with the given number. If no such token relation exists, returns {@code Optional.empty()}
     *
     * @param tokenNum - the number of the token relation to be retrieved
     * @param accountNum - the number of the account relation to be retrieved
     */
    public Optional<TokenRelation> get(final long tokenNum, final long accountNum) {
        final var tokenRelation =
                Objects.requireNonNull(tokenRelState).get(EntityNumPair.fromLongs(tokenNum, accountNum));
        return Optional.ofNullable(tokenRelation);
    }

    /**
     * Returns the {@link TokenRelation} with the given token number and account number.
     * If no such token relation exists, returns {@code Optional.empty()}
     *
     * @param tokenNum - the number of the token to be retrieved
     * @param accountNum - the number of the account to be retrieved
     */
    public Optional<TokenRelation> getForModify(final long tokenNum, final long accountNum) {
        final var token =
                Objects.requireNonNull(tokenRelState).getForModify(EntityNumPair.fromLongs(tokenNum, accountNum));
        return Optional.ofNullable(token);
    }

    /**
     * Returns the number of tokens in the state.
     * @return the number of tokens in the state.
     */
    public long sizeOfState() {
        return tokenRelState.size();
    }

    /**
     * @return the set of token relations modified in existing state
     */
    public Set<EntityNumPair> modifiedTokens() {
        return tokenRelState.modifiedKeys();
    }
}
