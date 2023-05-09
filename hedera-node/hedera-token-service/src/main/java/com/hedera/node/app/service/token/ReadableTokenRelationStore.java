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

package com.hedera.node.app.service.token;


import com.hedera.hapi.node.state.token.TokenRelation;

import java.util.Optional;

/**
 * Provides read-only methods for getting underlying data for working with TokenRelations.
 *
 * <p>This class is not exported from the module. It is an internal implementation detail.
 */
public interface ReadableTokenRelationStore {
    /**
     * Returns the {@link TokenRelation} with the given number. If no such token relation exists, returns {@code Optional.empty()}
     *
     * @param tokenNum - the number of the token relation to be retrieved
     * @param accountNum - the number of the account relation to be retrieved
     */
    Optional<TokenRelation> get(final long tokenNum, final long accountNum);

    /**
     * Returns the number of tokens in the state.
     * @return the number of tokens in the state.
     */
    long sizeOfState();
}
