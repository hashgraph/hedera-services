/*
 * Copyright (C) 2023 Hedera Hashgraph, LLC
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

package com.hedera.node.app.service.token.impl.handlers;

import static com.hedera.hapi.node.base.ResponseCodeEnum.*;
import static com.hedera.node.app.spi.workflows.HandleException.validateFalse;
import static com.hedera.node.app.spi.workflows.HandleException.validateTrue;
import static java.util.Objects.requireNonNull;

import com.hedera.hapi.node.base.TokenID;
import com.hedera.hapi.node.state.token.Token;
import com.hedera.node.app.service.token.ReadableTokenStore;
import com.hedera.node.app.spi.workflows.HandleException;
import edu.umd.cs.findbugs.annotations.NonNull;

public class TokenHandlerHelper {
    /**
     * Returns the token if it exists and is usable. A {@link HandleException} is thrown if the token is invalid
     *
     * @param tokenId the ID of the token to get
     * @param tokenStore the {@link ReadableTokenStore} to use for token retrieval
     * @throws HandleException if any of the token conditions are not met
     */
    public static Token getIfUsable(@NonNull final TokenID tokenId, @NonNull final ReadableTokenStore tokenStore) {
        requireNonNull(tokenId);
        requireNonNull(tokenStore);

        final var token = tokenStore.get(tokenId);
        validateTrue(token != null, INVALID_TOKEN_ID);
        validateFalse(token.deleted(), TOKEN_WAS_DELETED);
        validateFalse(token.paused(), TOKEN_IS_PAUSED);
        return token;
    }
}
