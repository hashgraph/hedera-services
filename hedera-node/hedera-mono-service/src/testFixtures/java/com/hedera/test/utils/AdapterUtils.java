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
package com.hedera.test.utils;

import static com.hedera.node.app.service.mono.utils.EntityNum.MISSING_NUM;
import static com.hedera.node.app.service.mono.utils.EntityNum.fromAccountId;
import static com.hedera.test.factories.scenarios.TxnHandlingScenario.CURRENTLY_UNUSED_ALIAS;
import static com.hedera.test.factories.scenarios.TxnHandlingScenario.FIRST_TOKEN_SENDER;
import static com.hedera.test.factories.scenarios.TxnHandlingScenario.FIRST_TOKEN_SENDER_LITERAL_ALIAS;
import static com.hedera.test.factories.scenarios.TxnHandlingScenario.NO_RECEIVER_SIG;
import static com.hedera.test.factories.scenarios.TxnHandlingScenario.NO_RECEIVER_SIG_ALIAS;
import static com.hedera.test.factories.scenarios.TxnHandlingScenario.RECEIVER_SIG;
import static com.hedera.test.factories.scenarios.TxnHandlingScenario.RECEIVER_SIG_ALIAS;
import static org.mockito.BDDMockito.given;

import com.google.protobuf.ByteString;
import com.hedera.node.app.service.mono.state.impl.InMemoryStateImpl;
import com.hedera.node.app.service.mono.state.impl.RebuiltStateImpl;
import com.hedera.node.app.service.mono.state.migration.HederaAccount;
import com.hedera.node.app.service.mono.utils.EntityNum;
import com.hedera.node.app.spi.AccountKeyLookup;
import com.hedera.node.app.spi.state.State;
import com.hedera.node.app.spi.state.States;
import com.hedera.test.factories.scenarios.TxnHandlingScenario;
import java.time.Instant;
import java.util.Map;
import org.mockito.Mockito;

public class AdapterUtils {
    private static final String ACCOUNTS_KEY = "ACCOUNTS";
    private static final String ALIASES_KEY = "ALIASES";

    private AdapterUtils() {
        throw new UnsupportedOperationException("Utility Class");
    }

    /**
     * Returns the {@link AccountKeyLookup} containing the "well-known" accounts and aliases that
     * exist in a {@code SigRequirementsTest} scenario. This allows us to re-use these scenarios in
     * unit tests of {@link com.hedera.node.app.spi.PreTransactionHandler} implementations that
     * require an {@link AccountKeyLookup}.
     *
     * @param mockLastModified the mock last modified time for the store to assume
     * @return the well-known account store
     */
    public static AccountKeyLookup wellKnownKeyLookupAt(final Instant mockLastModified) {
        return new SimpleKeyLookup(
                mockStates(
                        Map.of(
                                ALIASES_KEY, wellKnownAliasState(mockLastModified),
                                ACCOUNTS_KEY, wellKnownAccountsState(mockLastModified))));
    }

    public static States mockStates(final Map<String, State> keysToMock) {
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
}
