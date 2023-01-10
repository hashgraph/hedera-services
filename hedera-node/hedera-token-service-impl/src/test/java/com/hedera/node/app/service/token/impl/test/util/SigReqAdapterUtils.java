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
package com.hedera.node.app.service.token.impl.test.util;

import com.hedera.node.app.service.mono.state.merkle.MerkleToken;
import com.hedera.node.app.service.mono.utils.EntityNum;
import com.hedera.node.app.service.mono.utils.accessors.PlatformTxnAccessor;
import com.hedera.node.app.service.token.impl.ReadableTokenStore;
import com.hedera.node.app.spi.fixtures.state.MapReadableKVState;
import com.hedera.test.factories.scenarios.TxnHandlingScenario;
import com.hedera.test.utils.StateKeyAdapter;
import org.apache.commons.lang3.NotImplementedException;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.hedera.node.app.service.token.impl.test.handlers.AdapterUtils.mockStates;
import static com.hedera.test.factories.scenarios.TxnHandlingScenario.*;

public class SigReqAdapterUtils {
    private static final String TOKENS_KEY = "TOKENS";

    /**
     * Returns the {@link ReadableTokenStore} containing the "well-known" tokens that exist in a
     * {@code SigRequirementsTest} scenario. This allows us to re-use these scenarios in unit tests
     * of {@link com.hedera.node.app.spi.PreTransactionHandler} implementations that require a
     * {@link ReadableTokenStore}.
     *
     * @param mockLastModified the mock last modified time for the store to assume
     * @return the well-known token store
     */
    public static ReadableTokenStore wellKnownTokenStoreAt(final Instant mockLastModified) {
        final var source = sigReqsMockTokenStore();
        final Map<EntityNum, MerkleToken> destination = new HashMap<>();
        List.of(
                        KNOWN_TOKEN_IMMUTABLE,
                        KNOWN_TOKEN_NO_SPECIAL_KEYS,
                        KNOWN_TOKEN_WITH_PAUSE,
                        KNOWN_TOKEN_WITH_FREEZE,
                        KNOWN_TOKEN_WITH_KYC,
                        KNOWN_TOKEN_WITH_FEE_SCHEDULE_KEY,
                        KNOWN_TOKEN_WITH_ROYALTY_FEE_AND_FALLBACK,
                        KNOWN_TOKEN_WITH_SUPPLY,
                        KNOWN_TOKEN_WITH_WIPE)
                .forEach(id -> destination.put(EntityNum.fromTokenId(id), source.get(id)));
        final var wrappedState = new MapReadableKVState<>("TOKENS", destination);
        final var state = new StateKeyAdapter<>(wrappedState, EntityNum::fromLong);
        return new ReadableTokenStore(mockStates(Map.of(TOKENS_KEY, state)));
    }

    @SuppressWarnings("java:S1604")
    private static com.hedera.node.app.service.mono.store.tokens.TokenStore
            sigReqsMockTokenStore() {
        final var dummyScenario =
                new TxnHandlingScenario() {
                    @Override
                    public PlatformTxnAccessor platformTxn() {
                        throw new NotImplementedException();
                    }
                };
        return dummyScenario.tokenStore();
    }
}
