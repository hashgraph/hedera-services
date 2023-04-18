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

package com.hedera.node.app.service.contract.impl.test.handlers;

import static com.hedera.node.app.service.contract.impl.test.handlers.AdapterUtils.SigReqAdapterUtils.wellKnownAccountsState;
import static com.hedera.node.app.service.mono.context.BasicTransactionContext.EMPTY_KEY;
import static com.hedera.node.app.service.mono.pbj.PbjConverter.toPbj;
import static com.hedera.node.app.service.mono.utils.EntityNum.MISSING_NUM;
import static com.hedera.node.app.service.mono.utils.EntityNum.fromAccountId;
import static com.hedera.node.app.service.mono.utils.MiscUtils.asKeyUnchecked;
import static com.hedera.test.factories.scenarios.TxnHandlingScenario.COMPLEX_KEY_ACCOUNT;
import static com.hedera.test.factories.scenarios.TxnHandlingScenario.COMPLEX_KEY_ACCOUNT_KT;
import static com.hedera.test.factories.scenarios.TxnHandlingScenario.CURRENTLY_UNUSED_ALIAS;
import static com.hedera.test.factories.scenarios.TxnHandlingScenario.CUSTOM_PAYER_ACCOUNT;
import static com.hedera.test.factories.scenarios.TxnHandlingScenario.CUSTOM_PAYER_ACCOUNT_KT;
import static com.hedera.test.factories.scenarios.TxnHandlingScenario.DEFAULT_BALANCE;
import static com.hedera.test.factories.scenarios.TxnHandlingScenario.DEFAULT_PAYER_BALANCE;
import static com.hedera.test.factories.scenarios.TxnHandlingScenario.DELEGATING_SPENDER;
import static com.hedera.test.factories.scenarios.TxnHandlingScenario.DELEGATING_SPENDER_KT;
import static com.hedera.test.factories.scenarios.TxnHandlingScenario.DILIGENT_SIGNING_PAYER;
import static com.hedera.test.factories.scenarios.TxnHandlingScenario.DILIGENT_SIGNING_PAYER_KT;
import static com.hedera.test.factories.scenarios.TxnHandlingScenario.FIRST_TOKEN_SENDER;
import static com.hedera.test.factories.scenarios.TxnHandlingScenario.FIRST_TOKEN_SENDER_KT;
import static com.hedera.test.factories.scenarios.TxnHandlingScenario.FIRST_TOKEN_SENDER_LITERAL_ALIAS;
import static com.hedera.test.factories.scenarios.TxnHandlingScenario.FROM_OVERLAP_PAYER;
import static com.hedera.test.factories.scenarios.TxnHandlingScenario.FROM_OVERLAP_PAYER_KT;
import static com.hedera.test.factories.scenarios.TxnHandlingScenario.IMMUTABLE_CONTRACT;
import static com.hedera.test.factories.scenarios.TxnHandlingScenario.KNOWN_TOKEN_NO_SPECIAL_KEYS;
import static com.hedera.test.factories.scenarios.TxnHandlingScenario.KNOWN_TOKEN_WITH_WIPE;
import static com.hedera.test.factories.scenarios.TxnHandlingScenario.MISC_ACCOUNT;
import static com.hedera.test.factories.scenarios.TxnHandlingScenario.MISC_ACCOUNT_KT;
import static com.hedera.test.factories.scenarios.TxnHandlingScenario.MISC_ADMIN_KT;
import static com.hedera.test.factories.scenarios.TxnHandlingScenario.MISC_CONTRACT;
import static com.hedera.test.factories.scenarios.TxnHandlingScenario.MISC_RECIEVER_SIG_CONTRACT;
import static com.hedera.test.factories.scenarios.TxnHandlingScenario.NO_RECEIVER_SIG;
import static com.hedera.test.factories.scenarios.TxnHandlingScenario.NO_RECEIVER_SIG_ALIAS;
import static com.hedera.test.factories.scenarios.TxnHandlingScenario.NO_RECEIVER_SIG_KT;
import static com.hedera.test.factories.scenarios.TxnHandlingScenario.OWNER_ACCOUNT;
import static com.hedera.test.factories.scenarios.TxnHandlingScenario.OWNER_ACCOUNT_KT;
import static com.hedera.test.factories.scenarios.TxnHandlingScenario.RECEIVER_SIG;
import static com.hedera.test.factories.scenarios.TxnHandlingScenario.RECEIVER_SIG_ALIAS;
import static com.hedera.test.factories.scenarios.TxnHandlingScenario.RECEIVER_SIG_KT;
import static com.hedera.test.factories.scenarios.TxnHandlingScenario.SECOND_TOKEN_SENDER;
import static com.hedera.test.factories.scenarios.TxnHandlingScenario.SECOND_TOKEN_SENDER_KT;
import static com.hedera.test.factories.scenarios.TxnHandlingScenario.SYS_ACCOUNT;
import static com.hedera.test.factories.scenarios.TxnHandlingScenario.SYS_ACCOUNT_KT;
import static com.hedera.test.factories.scenarios.TxnHandlingScenario.TOKEN_RECEIVER;
import static com.hedera.test.factories.scenarios.TxnHandlingScenario.TOKEN_TREASURY;
import static com.hedera.test.factories.scenarios.TxnHandlingScenario.TOKEN_TREASURY_KT;
import static com.hedera.test.factories.scenarios.TxnHandlingScenario.TOKEN_WIPE_KT;
import static com.hedera.test.factories.txns.SignedTxnFactory.DEFAULT_NODE;
import static com.hedera.test.factories.txns.SignedTxnFactory.DEFAULT_PAYER;
import static com.hedera.test.factories.txns.SignedTxnFactory.DEFAULT_PAYER_KT;
import static com.hedera.test.factories.txns.SignedTxnFactory.MASTER_PAYER;
import static com.hedera.test.factories.txns.SignedTxnFactory.STAKING_FUND;
import static com.hedera.test.factories.txns.SignedTxnFactory.TREASURY_PAYER;
import static org.mockito.BDDMockito.given;

import com.hedera.hapi.node.base.Key;
import com.hedera.hapi.node.state.token.Account;
import com.hedera.hapi.node.state.token.AccountApprovalForAllAllowance;
import com.hedera.hapi.node.state.token.AccountCryptoAllowance;
import com.hedera.hapi.node.state.token.AccountFungibleTokenAllowance;
import com.hedera.node.app.service.mono.state.virtual.EntityNumValue;
import com.hedera.node.app.service.mono.state.virtual.EntityNumVirtualKey;
import com.hedera.node.app.spi.accounts.AccountAccess;
import com.hedera.node.app.spi.fixtures.state.MapReadableKVState;
import com.hedera.node.app.spi.state.ReadableKVState;
import com.hedera.node.app.spi.state.ReadableStates;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.hedera.test.utils.TestFixturesKeyLookup;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.mockito.Mockito;

// NOTE: This class is duplicated in more than one service module.
// !!!!!!!!!!🔥🔥🔥 It should be deleted once we find where to keep it. 🔥🔥🔥!!!!!!!!!!!
public class AdapterUtils {
    private static final String ACCOUNTS_KEY = "ACCOUNTS";
    private static final String ALIASES_KEY = "ALIASES";

    private AdapterUtils() {
        throw new UnsupportedOperationException("Utility Class");
    }

    /**
     * Returns the {@link AccountAccess} containing the "well-known" accounts and aliases that
     * exist in a {@code SigRequirementsTest} scenario. This allows us to re-use these scenarios in
     * unit tests that require an {@link AccountAccess}.
     *
     * @return the well-known account store
     */
    public static AccountAccess wellKnownKeyLookupAt() {
        return new TestFixturesKeyLookup(mockStates(Map.of(
                ALIASES_KEY, wellKnownAliasState(),
                ACCOUNTS_KEY, wellKnownAccountsState())));
    }

    public static ReadableStates mockStates(final Map<String, ReadableKVState> keysToMock) {
        final var mockStates = Mockito.mock(ReadableStates.class);
        keysToMock.forEach((key, state) -> given(mockStates.get(key)).willReturn(state));
        return mockStates;
    }

    private static MapReadableKVState<String, EntityNumValue> wellKnownAliasState() {
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
        return new MapReadableKVState<>(ALIASES_KEY, wellKnownAliases);
    }

    class SigReqAdapterUtils {
        private static final String ACCOUNTS_KEY = "ACCOUNTS";

        private static AccountCryptoAllowance cryptoAllowances = AccountCryptoAllowance.newBuilder()
                .spenderNum(DEFAULT_PAYER.getAccountNum())
                .amount(500L)
                .build();
        private static AccountFungibleTokenAllowance fungibleTokenAllowances =
                AccountFungibleTokenAllowance.newBuilder()
                        .tokenNum(KNOWN_TOKEN_NO_SPECIAL_KEYS.getTokenNum())
                        .spenderNum(DEFAULT_PAYER.getAccountNum())
                        .amount(10_000L)
                        .build();

        private static AccountApprovalForAllAllowance nftAllowances = AccountApprovalForAllAllowance.newBuilder()
                .tokenNum(KNOWN_TOKEN_WITH_WIPE.getTokenNum())
                .spenderNum(DEFAULT_PAYER.getAccountNum())
                .build();

        static ReadableKVState<EntityNumVirtualKey, Account> wellKnownAccountsState() {
            return new MapReadableKVState<>(ACCOUNTS_KEY, wellKnownAccountStoreAt());
        }

        static Map<EntityNumVirtualKey, Account> wellKnownAccountStoreAt() {
            final var destination = new HashMap<EntityNumVirtualKey, Account>();
            destination.put(
                    EntityNumVirtualKey.fromLong(FIRST_TOKEN_SENDER.getAccountNum()),
                    toPbjAccount(FIRST_TOKEN_SENDER.getAccountNum(), FIRST_TOKEN_SENDER_KT.asPbjKey(), 10_000L, false));
            destination.put(
                    EntityNumVirtualKey.fromLong(SECOND_TOKEN_SENDER.getAccountNum()),
                    toPbjAccount(
                            SECOND_TOKEN_SENDER.getAccountNum(), SECOND_TOKEN_SENDER_KT.asPbjKey(), 10_000L, false));
            destination.put(
                    EntityNumVirtualKey.fromLong(TOKEN_RECEIVER.getAccountNum()),
                    toPbjAccount(TOKEN_RECEIVER.getAccountNum(), TOKEN_WIPE_KT.asPbjKey(), 0L, false));
            destination.put(
                    EntityNumVirtualKey.fromLong(DEFAULT_NODE.getAccountNum()),
                    toPbjAccount(DEFAULT_NODE.getAccountNum(), DEFAULT_PAYER_KT.asPbjKey(), 0L, false));
            destination.put(
                    EntityNumVirtualKey.fromLong(DEFAULT_PAYER.getAccountNum()),
                    toPbjAccount(
                            DEFAULT_PAYER.getAccountNum(), DEFAULT_PAYER_KT.asPbjKey(), DEFAULT_PAYER_BALANCE, false));
            destination.put(
                    EntityNumVirtualKey.fromLong(STAKING_FUND.getAccountNum()),
                    toPbjAccount(STAKING_FUND.getAccountNum(), toPbj(asKeyUnchecked(EMPTY_KEY)), 0L, false));
            destination.put(
                    EntityNumVirtualKey.fromLong(MASTER_PAYER.getAccountNum()),
                    toPbjAccount(
                            MASTER_PAYER.getAccountNum(), DEFAULT_PAYER_KT.asPbjKey(), DEFAULT_PAYER_BALANCE, false));
            destination.put(
                    EntityNumVirtualKey.fromLong(TREASURY_PAYER.getAccountNum()),
                    toPbjAccount(
                            TREASURY_PAYER.getAccountNum(), DEFAULT_PAYER_KT.asPbjKey(), DEFAULT_PAYER_BALANCE, false));
            destination.put(
                    EntityNumVirtualKey.fromLong(NO_RECEIVER_SIG.getAccountNum()),
                    toPbjAccount(
                            NO_RECEIVER_SIG.getAccountNum(), NO_RECEIVER_SIG_KT.asPbjKey(), DEFAULT_BALANCE, false));
            destination.put(
                    EntityNumVirtualKey.fromLong(RECEIVER_SIG.getAccountNum()),
                    toPbjAccount(
                            RECEIVER_SIG.getAccountNum(), RECEIVER_SIG_KT.asPbjKey(), DEFAULT_BALANCE, true, false));
            destination.put(
                    EntityNumVirtualKey.fromLong(SYS_ACCOUNT.getAccountNum()),
                    toPbjAccount(SYS_ACCOUNT.getAccountNum(), SYS_ACCOUNT_KT.asPbjKey(), DEFAULT_BALANCE, false));
            destination.put(
                    EntityNumVirtualKey.fromLong(MISC_ACCOUNT.getAccountNum()),
                    toPbjAccount(MISC_ACCOUNT.getAccountNum(), MISC_ACCOUNT_KT.asPbjKey(), DEFAULT_BALANCE, false));
            destination.put(
                    EntityNumVirtualKey.fromLong(CUSTOM_PAYER_ACCOUNT.getAccountNum()),
                    toPbjAccount(
                            CUSTOM_PAYER_ACCOUNT.getAccountNum(),
                            CUSTOM_PAYER_ACCOUNT_KT.asPbjKey(),
                            DEFAULT_BALANCE,
                            false));
            destination.put(
                    EntityNumVirtualKey.fromLong(OWNER_ACCOUNT.getAccountNum()),
                    toPbjAccount(
                            OWNER_ACCOUNT.getAccountNum(),
                            OWNER_ACCOUNT_KT.asPbjKey(),
                            DEFAULT_BALANCE,
                            false,
                            List.of(cryptoAllowances),
                            List.of(fungibleTokenAllowances),
                            List.of(nftAllowances),
                            false));
            destination.put(
                    EntityNumVirtualKey.fromLong(DELEGATING_SPENDER.getAccountNum()),
                    toPbjAccount(
                            DELEGATING_SPENDER.getAccountNum(),
                            DELEGATING_SPENDER_KT.asPbjKey(),
                            DEFAULT_BALANCE,
                            false,
                            List.of(cryptoAllowances),
                            List.of(fungibleTokenAllowances),
                            List.of(nftAllowances),
                            false));
            destination.put(
                    EntityNumVirtualKey.fromLong(COMPLEX_KEY_ACCOUNT.getAccountNum()),
                    toPbjAccount(
                            COMPLEX_KEY_ACCOUNT.getAccountNum(),
                            COMPLEX_KEY_ACCOUNT_KT.asPbjKey(),
                            DEFAULT_BALANCE,
                            false));
            destination.put(
                    EntityNumVirtualKey.fromLong(TOKEN_TREASURY.getAccountNum()),
                    toPbjAccount(TOKEN_TREASURY.getAccountNum(), TOKEN_TREASURY_KT.asPbjKey(), DEFAULT_BALANCE, false));
            destination.put(
                    EntityNumVirtualKey.fromLong(DILIGENT_SIGNING_PAYER.getAccountNum()),
                    toPbjAccount(
                            DILIGENT_SIGNING_PAYER.getAccountNum(),
                            DILIGENT_SIGNING_PAYER_KT.asPbjKey(),
                            DEFAULT_BALANCE,
                            false));
            destination.put(
                    EntityNumVirtualKey.fromLong(FROM_OVERLAP_PAYER.getAccountNum()),
                    toPbjAccount(
                            FROM_OVERLAP_PAYER.getAccountNum(),
                            FROM_OVERLAP_PAYER_KT.asPbjKey(),
                            DEFAULT_BALANCE,
                            false));
            destination.put(
                    EntityNumVirtualKey.fromLong(MISC_RECIEVER_SIG_CONTRACT.getContractNum()),
                    toPbjAccount(
                            MISC_RECIEVER_SIG_CONTRACT.getContractNum(),
                            DILIGENT_SIGNING_PAYER_KT.asPbjKey(),
                            DEFAULT_BALANCE,
                            true));
            destination.put(
                    EntityNumVirtualKey.fromLong(IMMUTABLE_CONTRACT.getContractNum()),
                    toPbjAccount(IMMUTABLE_CONTRACT.getContractNum(), Key.DEFAULT, DEFAULT_BALANCE, true));
            destination.put(
                    EntityNumVirtualKey.fromLong(MISC_CONTRACT.getContractNum()),
                    toPbjAccount(MISC_CONTRACT.getContractNum(), MISC_ADMIN_KT.asPbjKey(), DEFAULT_BALANCE, true));
            return destination;
        }

        private static Account toPbjAccount(final long number, final Key key, long balance, boolean isSmartContract) {
            return toPbjAccount(number, key, balance, false, List.of(), List.of(), List.of(), isSmartContract);
        }

        private static Account toPbjAccount(
                final long number,
                final Key key,
                long balance,
                final boolean isReceiverSigRequired,
                boolean isSmartContract) {
            return toPbjAccount(
                    number, key, balance, isReceiverSigRequired, List.of(), List.of(), List.of(), isSmartContract);
        }

        private static Account toPbjAccount(
                final long number,
                final Key key,
                long balance,
                boolean receiverSigRequired,
                List<AccountCryptoAllowance> cryptoAllowances,
                List<AccountFungibleTokenAllowance> fungibleTokenAllowances,
                List<AccountApprovalForAllAllowance> nftTokenAllowances,
                boolean isSmartContract) {
            return new Account(
                    number,
                    Bytes.EMPTY,
                    key,
                    10_000L,
                    balance,
                    "test",
                    false,
                    1_234_567L,
                    1_234_567L,
                    0L,
                    false,
                    receiverSigRequired,
                    3L,
                    2L,
                    1L,
                    2,
                    3,
                    3,
                    3,
                    isSmartContract,
                    3,
                    0,
                    1_234_5678L,
                    2,
                    76_000L,
                    0,
                    cryptoAllowances,
                    nftTokenAllowances,
                    fungibleTokenAllowances,
                    2,
                    false,
                    null);
        }
    }
}
