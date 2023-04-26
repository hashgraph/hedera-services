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

package com.hedera.node.app.service.schedule.impl.test.handlers;

import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_SCHEDULE_ID;
import static com.hedera.hapi.node.base.ResponseCodeEnum.SCHEDULE_IS_IMMUTABLE;
import static com.hedera.node.app.service.mono.context.BasicTransactionContext.EMPTY_KEY;
import static com.hedera.node.app.service.mono.pbj.PbjConverter.toPbj;
import static com.hedera.node.app.service.mono.utils.MiscUtils.asKeyUnchecked;
import static com.hedera.node.app.service.schedule.impl.test.handlers.AdapterUtils.SigReqAdapterUtils.wellKnownAccountStoreAt;
import static com.hedera.node.app.spi.fixtures.Assertions.assertThrowsPreCheck;
import static com.hedera.test.factories.keys.NodeFactory.ed25519;
import static com.hedera.test.factories.scenarios.ContractCreateScenarios.KNOWN_SCHEDULE_IMMUTABLE_ID;
import static com.hedera.test.factories.scenarios.ContractCreateScenarios.KNOWN_SCHEDULE_WITH_ADMIN_ID;
import static com.hedera.test.factories.scenarios.ScheduleDeleteScenarios.SCHEDULE_DELETE_WITH_KNOWN_SCHEDULE;
import static com.hedera.test.factories.scenarios.ScheduleDeleteScenarios.SCHEDULE_DELETE_WITH_MISSING_SCHEDULE;
import static com.hedera.test.factories.scenarios.ScheduleDeleteScenarios.SCHEDULE_DELETE_WITH_MISSING_SCHEDULE_ADMIN_KEY;
import static com.hedera.test.factories.scenarios.TxnHandlingScenario.COMPLEX_KEY_ACCOUNT;
import static com.hedera.test.factories.scenarios.TxnHandlingScenario.COMPLEX_KEY_ACCOUNT_KT;
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
import static com.hedera.test.factories.scenarios.TxnHandlingScenario.FROM_OVERLAP_PAYER;
import static com.hedera.test.factories.scenarios.TxnHandlingScenario.FROM_OVERLAP_PAYER_KT;
import static com.hedera.test.factories.scenarios.TxnHandlingScenario.KNOWN_TOKEN_NO_SPECIAL_KEYS;
import static com.hedera.test.factories.scenarios.TxnHandlingScenario.KNOWN_TOKEN_WITH_WIPE;
import static com.hedera.test.factories.scenarios.TxnHandlingScenario.MISC_ACCOUNT;
import static com.hedera.test.factories.scenarios.TxnHandlingScenario.MISC_ACCOUNT_KT;
import static com.hedera.test.factories.scenarios.TxnHandlingScenario.NO_RECEIVER_SIG;
import static com.hedera.test.factories.scenarios.TxnHandlingScenario.NO_RECEIVER_SIG_KT;
import static com.hedera.test.factories.scenarios.TxnHandlingScenario.OWNER_ACCOUNT;
import static com.hedera.test.factories.scenarios.TxnHandlingScenario.OWNER_ACCOUNT_KT;
import static com.hedera.test.factories.scenarios.TxnHandlingScenario.RECEIVER_SIG;
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
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import com.hedera.hapi.node.base.Key;
import com.hedera.hapi.node.base.ScheduleID;
import com.hedera.hapi.node.state.token.Account;
import com.hedera.hapi.node.state.token.AccountCryptoAllowance;
import com.hedera.hapi.node.state.token.AccountFungibleTokenAllowance;
import com.hedera.hapi.node.state.token.AccountTokenAllowance;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.node.app.service.mono.pbj.PbjConverter;
import com.hedera.node.app.service.mono.state.virtual.EntityNumVirtualKey;
import com.hedera.node.app.service.mono.state.virtual.schedule.ScheduleVirtualValue;
import com.hedera.node.app.service.schedule.impl.ReadableScheduleStore;
import com.hedera.node.app.service.schedule.impl.handlers.ScheduleDeleteHandler;
import com.hedera.node.app.spi.accounts.AccountAccess;
import com.hedera.node.app.spi.fixtures.state.MapReadableKVState;
import com.hedera.node.app.spi.fixtures.state.MapReadableStates;
import com.hedera.node.app.spi.state.ReadableKVState;
import com.hedera.node.app.spi.state.ReadableKVStateBase;
import com.hedera.node.app.spi.state.ReadableStates;
import com.hedera.node.app.spi.workflows.PreHandleContext;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.hedera.test.factories.keys.KeyTree;
import com.hedera.test.factories.scenarios.TxnHandlingScenario;
import com.hedera.test.utils.IdUtils;
import com.hedera.test.utils.TestFixturesKeyLookup;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.apache.commons.codec.DecoderException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ScheduleDeleteHandlerParityTest {
    static final KeyTree ADMIN_KEY = KeyTree.withRoot(ed25519());
    private final ScheduleDeleteHandler subject = new ScheduleDeleteHandler();
    private AccountAccess keyLookup;
    private ReadableScheduleStore scheduleStore;

    @BeforeEach
    void setUp() {
        keyLookup = AdapterUtils.wellKnownKeyLookupAt();
    }

    @Test
    void getsScheduleDeleteWithMissingSchedule() throws Throwable {
        final var theTxn = txnFrom(SCHEDULE_DELETE_WITH_MISSING_SCHEDULE);
        scheduleStore = AdapterUtils.mockSchedule(
                999L, ADMIN_KEY, theTxn); // use any schedule id that does not match UNKNOWN_SCHEDULE_ID
        final var context = new PreHandleContext(keyLookup, theTxn);

        assertThrowsPreCheck(() -> subject.preHandle(context, scheduleStore), INVALID_SCHEDULE_ID);
    }

    @Test
    void getsScheduleDeleteWithMissingAdminKey() throws Throwable {
        final var theTxn = txnFrom(SCHEDULE_DELETE_WITH_MISSING_SCHEDULE_ADMIN_KEY);
        scheduleStore = AdapterUtils.mockSchedule(
                IdUtils.asSchedule(KNOWN_SCHEDULE_IMMUTABLE_ID).getScheduleNum(), null, theTxn);
        final var context = new PreHandleContext(keyLookup, theTxn);

        assertThrowsPreCheck(() -> subject.preHandle(context, scheduleStore), SCHEDULE_IS_IMMUTABLE);
    }

    @Test
    void getsScheduleDeleteKnownSchedule() throws Throwable {
        final var theTxn = txnFrom(SCHEDULE_DELETE_WITH_KNOWN_SCHEDULE);
        scheduleStore = AdapterUtils.mockSchedule(
                IdUtils.asSchedule(KNOWN_SCHEDULE_WITH_ADMIN_ID).getScheduleNum(), ADMIN_KEY, theTxn);
        final var context = new PreHandleContext(keyLookup, theTxn);
        subject.preHandle(context, scheduleStore);

        assertTrue(context.requiredNonPayerKeys().contains(ADMIN_KEY.asPbjKey()));
    }

    private TransactionBody txnFrom(final TxnHandlingScenario scenario) {
        try {
            return PbjConverter.toPbj(scenario.platformTxn().getTxn());
        } catch (final Throwable e) {
            throw new RuntimeException(e);
        }
    }
}

class AdapterUtils {
    // NOTE: This class is duplicated in more than one service module.
    // !!!!!!!!!!🔥🔥🔥 It should be deleted once we find where to keep it. 🔥🔥🔥!!!!!!!!!!!
    private static final String ACCOUNTS_KEY = "ACCOUNTS";
    private static final ScheduleVirtualValue schedule = mock(ScheduleVirtualValue.class);

    @SuppressWarnings("unchecked")
    private static final ReadableKVStateBase<Long, ScheduleVirtualValue> schedulesById =
            (ReadableKVStateBase<Long, ScheduleVirtualValue>) mock(ReadableKVStateBase.class);

    private AdapterUtils() {
        throw new UnsupportedOperationException("Utility Class");
    }

    /**
     * Returns the {@link AccountAccess} containing the "well-known" accounts that exist in a
     * {@code SigRequirementsTest} scenario. This allows us to re-use these scenarios in unit tests
     * that require an {@link AccountAccess}.
     *
     * @return the well-known account store
     */
    public static AccountAccess wellKnownKeyLookupAt() {
        return new TestFixturesKeyLookup(mockStates(Map.of(ACCOUNTS_KEY, wellKnownAccountsState())));
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public static ReadableStates mockStates(final Map<String, ReadableKVState> keysToMock) {
        final var mockStates = mock(ReadableStates.class);
        keysToMock.forEach((key, state) -> given(mockStates.get(key)).willReturn(state));
        return mockStates;
    }

    public static ReadableScheduleStore mockSchedule(Long schedId, KeyTree key, TransactionBody txnBody)
            throws DecoderException {
        final ScheduleID scheduleID =
                ScheduleID.newBuilder().scheduleNum(schedId).build();
        given(schedule.hasAdminKey()).willReturn(key == null ? false : true);
        given(schedule.adminKey()).willReturn(key == null ? Optional.empty() : Optional.of(key.asJKey()));
        given(schedule.ordinaryViewOfScheduledTxn()).willReturn(PbjConverter.fromPbj(txnBody));
        given(schedulesById.get(scheduleID.scheduleNum())).willReturn(schedule);
        return new ReadableScheduleStore(new MapReadableStates(Map.of("SCHEDULES_BY_ID", schedulesById)));
    }

    private static ReadableKVState<EntityNumVirtualKey, Account> wellKnownAccountsState() {
        return new MapReadableKVState<>(ACCOUNTS_KEY, wellKnownAccountStoreAt());
    }

    public class SigReqAdapterUtils {
        private static final String ACCOUNTS_KEY = "ACCOUNTS";

        private static AccountCryptoAllowance cryptoAllowances = AccountCryptoAllowance.newBuilder()
                .accountNum(DEFAULT_PAYER.getAccountNum())
                .amount(500L)
                .build();
        private static AccountFungibleTokenAllowance fungibleTokenAllowances =
                AccountFungibleTokenAllowance.newBuilder()
                        .tokenAllowanceKey(AccountTokenAllowance.newBuilder()
                                .tokenNum(KNOWN_TOKEN_NO_SPECIAL_KEYS.getTokenNum())
                                .accountNum(DEFAULT_PAYER.getAccountNum())
                                .build())
                        .amount(10_000L)
                        .build();

        private static AccountTokenAllowance nftAllowances = AccountTokenAllowance.newBuilder()
                .tokenNum(KNOWN_TOKEN_WITH_WIPE.getTokenNum())
                .accountNum(DEFAULT_PAYER.getAccountNum())
                .build();

        public static Map<EntityNumVirtualKey, Account> wellKnownAccountStoreAt() {
            final var destination = new HashMap<EntityNumVirtualKey, Account>();
            destination.put(
                    EntityNumVirtualKey.fromLong(FIRST_TOKEN_SENDER.getAccountNum()),
                    toPbjAccount(FIRST_TOKEN_SENDER.getAccountNum(), FIRST_TOKEN_SENDER_KT.asPbjKey(), 10_000L));
            destination.put(
                    EntityNumVirtualKey.fromLong(SECOND_TOKEN_SENDER.getAccountNum()),
                    toPbjAccount(SECOND_TOKEN_SENDER.getAccountNum(), SECOND_TOKEN_SENDER_KT.asPbjKey(), 10_000L));
            destination.put(
                    EntityNumVirtualKey.fromLong(TOKEN_RECEIVER.getAccountNum()),
                    toPbjAccount(TOKEN_RECEIVER.getAccountNum(), TOKEN_WIPE_KT.asPbjKey(), 0L));
            destination.put(
                    EntityNumVirtualKey.fromLong(DEFAULT_NODE.getAccountNum()),
                    toPbjAccount(DEFAULT_NODE.getAccountNum(), DEFAULT_PAYER_KT.asPbjKey(), 0L));
            destination.put(
                    EntityNumVirtualKey.fromLong(DEFAULT_PAYER.getAccountNum()),
                    toPbjAccount(DEFAULT_PAYER.getAccountNum(), DEFAULT_PAYER_KT.asPbjKey(), DEFAULT_PAYER_BALANCE));
            destination.put(
                    EntityNumVirtualKey.fromLong(STAKING_FUND.getAccountNum()),
                    toPbjAccount(STAKING_FUND.getAccountNum(), toPbj(asKeyUnchecked(EMPTY_KEY)), 0L));
            destination.put(
                    EntityNumVirtualKey.fromLong(MASTER_PAYER.getAccountNum()),
                    toPbjAccount(MASTER_PAYER.getAccountNum(), DEFAULT_PAYER_KT.asPbjKey(), DEFAULT_PAYER_BALANCE));
            destination.put(
                    EntityNumVirtualKey.fromLong(TREASURY_PAYER.getAccountNum()),
                    toPbjAccount(TREASURY_PAYER.getAccountNum(), DEFAULT_PAYER_KT.asPbjKey(), DEFAULT_PAYER_BALANCE));
            destination.put(
                    EntityNumVirtualKey.fromLong(NO_RECEIVER_SIG.getAccountNum()),
                    toPbjAccount(NO_RECEIVER_SIG.getAccountNum(), NO_RECEIVER_SIG_KT.asPbjKey(), DEFAULT_BALANCE));
            destination.put(
                    EntityNumVirtualKey.fromLong(RECEIVER_SIG.getAccountNum()),
                    toPbjAccount(RECEIVER_SIG.getAccountNum(), RECEIVER_SIG_KT.asPbjKey(), DEFAULT_BALANCE, true));
            destination.put(
                    EntityNumVirtualKey.fromLong(SYS_ACCOUNT.getAccountNum()),
                    toPbjAccount(SYS_ACCOUNT.getAccountNum(), SYS_ACCOUNT_KT.asPbjKey(), DEFAULT_BALANCE));
            destination.put(
                    EntityNumVirtualKey.fromLong(MISC_ACCOUNT.getAccountNum()),
                    toPbjAccount(MISC_ACCOUNT.getAccountNum(), MISC_ACCOUNT_KT.asPbjKey(), DEFAULT_BALANCE));
            destination.put(
                    EntityNumVirtualKey.fromLong(CUSTOM_PAYER_ACCOUNT.getAccountNum()),
                    toPbjAccount(
                            CUSTOM_PAYER_ACCOUNT.getAccountNum(), CUSTOM_PAYER_ACCOUNT_KT.asPbjKey(), DEFAULT_BALANCE));
            destination.put(
                    EntityNumVirtualKey.fromLong(OWNER_ACCOUNT.getAccountNum()),
                    toPbjAccount(
                            OWNER_ACCOUNT.getAccountNum(),
                            OWNER_ACCOUNT_KT.asPbjKey(),
                            DEFAULT_BALANCE,
                            false,
                            List.of(cryptoAllowances),
                            List.of(fungibleTokenAllowances),
                            List.of(nftAllowances)));
            destination.put(
                    EntityNumVirtualKey.fromLong(DELEGATING_SPENDER.getAccountNum()),
                    toPbjAccount(
                            DELEGATING_SPENDER.getAccountNum(),
                            DELEGATING_SPENDER_KT.asPbjKey(),
                            DEFAULT_BALANCE,
                            false,
                            List.of(cryptoAllowances),
                            List.of(fungibleTokenAllowances),
                            List.of(nftAllowances)));
            destination.put(
                    EntityNumVirtualKey.fromLong(COMPLEX_KEY_ACCOUNT.getAccountNum()),
                    toPbjAccount(
                            COMPLEX_KEY_ACCOUNT.getAccountNum(), COMPLEX_KEY_ACCOUNT_KT.asPbjKey(), DEFAULT_BALANCE));
            destination.put(
                    EntityNumVirtualKey.fromLong(TOKEN_TREASURY.getAccountNum()),
                    toPbjAccount(TOKEN_TREASURY.getAccountNum(), TOKEN_TREASURY_KT.asPbjKey(), DEFAULT_BALANCE));
            destination.put(
                    EntityNumVirtualKey.fromLong(DILIGENT_SIGNING_PAYER.getAccountNum()),
                    toPbjAccount(
                            DILIGENT_SIGNING_PAYER.getAccountNum(),
                            DILIGENT_SIGNING_PAYER_KT.asPbjKey(),
                            DEFAULT_BALANCE));
            destination.put(
                    EntityNumVirtualKey.fromLong(FROM_OVERLAP_PAYER.getAccountNum()),
                    toPbjAccount(
                            FROM_OVERLAP_PAYER.getAccountNum(), FROM_OVERLAP_PAYER_KT.asPbjKey(), DEFAULT_BALANCE));
            return destination;
        }

        private static Account toPbjAccount(final long number, final Key key, long balance) {
            return toPbjAccount(number, key, balance, false, List.of(), List.of(), List.of());
        }

        private static Account toPbjAccount(
                final long number, final Key key, long balance, final boolean isReceiverSigRequired) {
            return toPbjAccount(number, key, balance, isReceiverSigRequired, List.of(), List.of(), List.of());
        }

        private static Account toPbjAccount(
                final long number,
                final Key key,
                long balance,
                boolean receiverSigRequired,
                List<AccountCryptoAllowance> cryptoAllowances,
                List<AccountFungibleTokenAllowance> fungibleTokenAllowances,
                List<AccountTokenAllowance> nftTokenAllowances) {
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
                    false,
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
                    false);
        }
    }
}
