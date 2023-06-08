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

package com.hedera.node.app.service.token.impl.test.handlers.util;

import static com.hedera.node.app.service.mono.pbj.PbjConverter.toPbj;
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

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.node.app.service.mono.state.migration.HederaAccount;
import com.hedera.node.app.service.mono.state.virtual.EntityNumValue;
import com.hedera.node.app.service.mono.utils.EntityNum;
import com.hedera.node.app.service.token.ReadableAccountStore;
import com.hedera.node.app.spi.fixtures.state.MapReadableKVState;
import com.hedera.node.app.spi.fixtures.state.MapWritableKVState;
import com.hedera.node.app.spi.state.ReadableKVState;
import com.hedera.node.app.spi.state.ReadableStates;
import com.hedera.node.app.spi.state.WritableKVState;
import com.hedera.node.app.spi.state.WritableStates;
import com.hedera.test.factories.scenarios.TxnHandlingScenario;
import com.hedera.test.utils.StateKeyAdapter;
import com.hedera.test.utils.TestFixturesKeyLookup;
import java.util.Map;
import org.mockito.Mockito;

public class AdapterUtils {
    private static final String ACCOUNTS_KEY = "ACCOUNTS";
    private static final String ALIASES_KEY = "ALIASES";

    private AdapterUtils() {
        throw new UnsupportedOperationException("Utility Class");
    }

    /**
     * Returns the {@link ReadableAccountStore} containing the "well-known" accounts and aliases that
     * exist in a {@code SigRequirementsTest} scenario. This allows us to re-use these scenarios in
     * unit tests that require a {@link ReadableAccountStore}.
     *
     * @return the well-known account store
     */
    public static ReadableAccountStore wellKnownKeyLookupAt() {
        return new TestFixturesKeyLookup(mockStates(Map.of(
                ALIASES_KEY, wellKnownAliasState(),
                ACCOUNTS_KEY, wellKnownAccountsState())));
    }

    public static ReadableStates mockStates(final Map<String, ReadableKVState> keysToMock) {
        final var mockStates = Mockito.mock(ReadableStates.class);
        keysToMock.forEach((key, state) -> given(mockStates.get(key)).willReturn(state));
        return mockStates;
    }

    public static WritableStates mockWritableStates(final Map<String, WritableKVState> keysToMock) {
        final var mockStates = Mockito.mock(WritableStates.class);
        keysToMock.forEach((key, state) -> given(mockStates.get(key)).willReturn(state));
        return mockStates;
    }

    private static ReadableKVState<AccountID, ? extends HederaAccount> wellKnownAccountsState() {
        final var wrappedState = new MapReadableKVState<>(ACCOUNTS_KEY, TxnHandlingScenario.wellKnownAccounts());
        return new StateKeyAdapter<>(
                wrappedState,
                (AccountID id) -> new EntityNum(id.accountNumOrThrow().intValue()));
    }

    public static WritableKVState<String, EntityNumValue> wellKnownAliasState() {
        final Map<String, EntityNumValue> wellKnownAliases = Map.ofEntries(
                Map.entry(CURRENTLY_UNUSED_ALIAS, new EntityNumValue(MISSING_NUM.longValue())),
                Map.entry(
                        NO_RECEIVER_SIG_ALIAS,
                        new EntityNumValue(fromAccountId(NO_RECEIVER_SIG).longValue())),
                Map.entry(
                        RECEIVER_SIG_ALIAS,
                        new EntityNumValue(fromAccountId(RECEIVER_SIG).longValue())),
                Map.entry(
                        FIRST_TOKEN_SENDER_LITERAL_ALIAS.toStringUtf8(),
                        new EntityNumValue(fromAccountId(FIRST_TOKEN_SENDER).longValue())));
        return new MapWritableKVState<>(ALIASES_KEY, wellKnownAliases);
    }

    public static TransactionBody txnFrom(final TxnHandlingScenario scenario) {
        try {
            return toPbj(scenario.platformTxn().getTxn());
        } catch (final Throwable e) {
            throw new RuntimeException(e);
        }
    }
}
