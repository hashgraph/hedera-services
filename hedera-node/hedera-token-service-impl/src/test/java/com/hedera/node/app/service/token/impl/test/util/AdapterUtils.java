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
package com.hedera.node.app.service.token.impl.test.util;

import static com.hedera.node.app.service.mono.utils.EntityNum.MISSING_NUM;
import static com.hedera.node.app.service.mono.utils.EntityNum.fromAccountId;
import static com.hedera.test.factories.scenarios.TxnHandlingScenario.*;
import static org.mockito.BDDMockito.given;

import com.google.protobuf.ByteString;
import com.hedera.node.app.service.mono.state.impl.InMemoryStateImpl;
import com.hedera.node.app.service.mono.state.impl.RebuiltStateImpl;
import com.hedera.node.app.service.mono.state.merkle.MerkleToken;
import com.hedera.node.app.service.mono.state.migration.HederaAccount;
import com.hedera.node.app.service.mono.store.AccountStore;
import com.hedera.node.app.service.mono.store.tokens.TokenStore;
import com.hedera.node.app.service.mono.utils.EntityNum;
import com.hedera.node.app.service.mono.utils.accessors.PlatformTxnAccessor;
import com.hedera.node.app.service.token.impl.ReadableTokenStore;
import com.hedera.node.app.spi.state.State;
import com.hedera.node.app.spi.state.States;
import com.hedera.test.factories.scenarios.TxnHandlingScenario;
import com.hedera.test.utils.StateKeyAdapter;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.swirlds.merkle.map.MerkleMap;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.apache.commons.lang3.NotImplementedException;
import org.mockito.Mockito;

public class AdapterUtils {
    private static final String TOKENS_KEY = "TOKENS";
    private static final String ACCOUNTS_KEY = "ACCOUNTS";
    private static final String ALIASES_KEY = "ALIASES";

    private AdapterUtils() {
        throw new UnsupportedOperationException("Utility Class");
    }

    /**
     * Returns the {@link AccountStore} containing the "well-known" accounts and aliases that exist
     * in a {@code SigRequirementsTest} scenario. This allows us to re-use these scenarios in unit
     * tests of {@link com.hedera.node.app.spi.PreTransactionHandler} implementations that require
     * an {@link AccountStore}.
     *
     * @param mockLastModified the mock last modified time for the store to assume
     * @return the well-known account store
     */
    public static ReadableTokenStore wellKnownAccountStoreAt(final Instant mockLastModified) {
        return new ReadableTokenStore(
                mockStates(
                        Map.of(
                                ALIASES_KEY, wellKnownAliasState(mockLastModified),
                                ACCOUNTS_KEY, wellKnownAccountsState(mockLastModified))));
    }

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
        final MerkleMap<EntityNum, MerkleToken> destination = new MerkleMap<>();
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
        final var wrappedState = new InMemoryStateImpl<>(TOKENS_KEY, destination, mockLastModified);
        final var state = new StateKeyAdapter<>(wrappedState, EntityNum::fromLong);
        return new ReadableTokenStore(mockStates(Map.of(TOKENS_KEY, state)));
    }

    private static States mockStates(final Map<String, State> keysToMock) {
        final var mockStates = Mockito.mock(States.class);
        keysToMock.forEach((key, state) -> given(mockStates.get(key)).willReturn(state));
        return mockStates;
    }

    private static State<Long, ? extends HederaAccount> wellKnownAccountsState(
            final Instant mockLastModified) {
        final var wrappedState =
                new InMemoryStateImpl<>(
                        ACCOUNTS_KEY, TxnHandlingScenario.wellKnownAccounts(), mockLastModified);
        return new StateKeyAdapter<>(wrappedState, EntityNum::fromLong);
    }

    private static State<ByteString, Long> wellKnownAliasState(final Instant mockLastModified) {
        final var wellKnownAliases =
                Map.ofEntries(
                        Map.entry(
                                ByteString.copyFromUtf8(CURRENTLY_UNUSED_ALIAS),
                                MISSING_NUM.longValue()),
                        Map.entry(
                                ByteString.copyFromUtf8(NO_RECEIVER_SIG_ALIAS),
                                fromAccountId(NO_RECEIVER_SIG).longValue()),
                        Map.entry(
                                ByteString.copyFromUtf8(RECEIVER_SIG_ALIAS),
                                fromAccountId(RECEIVER_SIG).longValue()),
                        Map.entry(
                                FIRST_TOKEN_SENDER_LITERAL_ALIAS,
                                fromAccountId(FIRST_TOKEN_SENDER).longValue()));
        return new RebuiltStateImpl<>(ALIASES_KEY, wellKnownAliases, mockLastModified);
    }

    @SuppressWarnings("java:S1604")
    private static TokenStore sigReqsMockTokenStore() {
        final var dummyScenario =
                new TxnHandlingScenario() {
                    @Override
                    public PlatformTxnAccessor platformTxn() {
                        throw new NotImplementedException();
                    }
                };
        return dummyScenario.tokenStore();
    }

    public static TransactionBody txnFrom(final TxnHandlingScenario scenario) {
        try {
            return scenario.platformTxn().getTxn();
        } catch (final Throwable e) {
            throw new RuntimeException(e);
        }
    }
}
