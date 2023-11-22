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

package com.hedera.node.app.throttle;

import static com.hedera.hapi.node.base.HederaFunctionality.CONSENSUS_SUBMIT_MESSAGE;
import static com.hedera.hapi.node.base.HederaFunctionality.CRYPTO_TRANSFER;
import static com.hedera.hapi.node.base.HederaFunctionality.SCHEDULE_CREATE;
import static com.hedera.hapi.node.base.HederaFunctionality.SCHEDULE_SIGN;
import static com.hedera.node.app.service.schedule.impl.ScheduleServiceImpl.SCHEDULES_BY_ID_KEY;
import static com.hedera.node.app.throttle.ThrottleAccumulator.ThrottleType.FRONTEND_THROTTLE;
import static com.hedera.pbj.runtime.ProtoTestTools.getThreadLocalDataBuffer;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hedera.hapi.node.base.AccountAmount;
import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.Key;
import com.hedera.hapi.node.base.ScheduleID;
import com.hedera.hapi.node.base.SignatureMap;
import com.hedera.hapi.node.base.Transaction;
import com.hedera.hapi.node.base.TransactionID;
import com.hedera.hapi.node.base.TransferList;
import com.hedera.hapi.node.consensus.ConsensusSubmitMessageTransactionBody;
import com.hedera.hapi.node.scheduled.SchedulableTransactionBody;
import com.hedera.hapi.node.scheduled.ScheduleCreateTransactionBody;
import com.hedera.hapi.node.scheduled.ScheduleSignTransactionBody;
import com.hedera.hapi.node.state.schedule.Schedule;
import com.hedera.hapi.node.token.CryptoTransferTransactionBody;
import com.hedera.hapi.node.transaction.ThrottleDefinitions;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.node.app.hapi.utils.throttles.BucketThrottle;
import com.hedera.node.app.spi.fixtures.util.LogCaptor;
import com.hedera.node.app.spi.fixtures.util.LogCaptureExtension;
import com.hedera.node.app.spi.fixtures.util.LoggingSubject;
import com.hedera.node.app.spi.fixtures.util.LoggingTarget;
import com.hedera.node.app.spi.state.ReadableKVState;
import com.hedera.node.app.spi.state.ReadableStates;
import com.hedera.node.app.state.HederaState;
import com.hedera.node.app.workflows.TransactionInfo;
import com.hedera.node.config.ConfigProvider;
import com.hedera.node.config.VersionedConfiguration;
import com.hedera.node.config.data.AccountsConfig;
import com.hedera.node.config.data.AutoCreationConfig;
import com.hedera.node.config.data.ContractsConfig;
import com.hedera.node.config.data.LazyCreationConfig;
import com.hedera.node.config.data.SchedulingConfig;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.util.ArrayList;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;

@ExtendWith({MockitoExtension.class, LogCaptureExtension.class})
class ThrottleAccumulatorTest {
    private static final int CAPACITY_SPLIT = 2;
    private static final Instant CONSENSUS_NOW = Instant.ofEpochSecond(1_234_567L, 123);
    private static final AccountID PAYER_ID =
            AccountID.newBuilder().accountNum(1234L).build();
    private static final Key A_PRIMITIVE_KEY = Key.newBuilder()
            .ed25519(Bytes.wrap("01234567890123456789012345678901"))
            .build();
    private static final ScheduleID SCHEDULE_ID =
            ScheduleID.newBuilder().scheduleNum(333333L).build();

    @LoggingSubject
    private ThrottleAccumulator subject;

    @LoggingTarget
    private LogCaptor logCaptor;

    @Mock
    private ConfigProvider configProvider;

    @Mock
    private VersionedConfiguration configuration;

    @Mock
    private SchedulingConfig schedulingConfig;

    @Mock
    private AccountsConfig accountsConfig;

    @Mock
    private ContractsConfig contractsConfig;

    @Mock
    private AutoCreationConfig autoCreationConfig;

    @Mock
    private LazyCreationConfig lazyCreationConfig;

    @Mock
    private HederaState state;

    @Mock
    private ReadableStates readableStates;

    @Mock
    private ReadableKVState aliases;

    @Mock
    private ReadableKVState schedules;

    @ParameterizedTest
    @CsvSource({
        "FRONTEND_THROTTLE,true,true",
        "FRONTEND_THROTTLE,true,false",
        "FRONTEND_THROTTLE,false,true",
        "FRONTEND_THROTTLE,false,false",
        "BACKEND_THROTTLE,true,true",
        "BACKEND_THROTTLE,true,false",
        "BACKEND_THROTTLE,false,true",
        "BACKEND_THROTTLE,false,false",
    })
    @MockitoSettings(strictness = org.mockito.quality.Strictness.LENIENT)
    void usesScheduleCreateThrottleForSubmitMessage(
            final ThrottleAccumulator.ThrottleType throttleType,
            final boolean longTermEnabled,
            final boolean waitForExpiry)
            throws IOException {
        // given
        subject = new ThrottleAccumulator(() -> CAPACITY_SPLIT, configProvider, throttleType);

        given(configProvider.getConfiguration()).willReturn(configuration);
        given(configuration.getConfigData(AccountsConfig.class)).willReturn(accountsConfig);
        given(accountsConfig.lastThrottleExempt()).willReturn(100L);
        given(configuration.getConfigData(ContractsConfig.class)).willReturn(contractsConfig);
        given(contractsConfig.throttleThrottleByGas()).willReturn(false);
        given(configuration.getConfigData(SchedulingConfig.class)).willReturn(schedulingConfig);
        given(schedulingConfig.longTermEnabled()).willReturn(longTermEnabled);
        given(configuration.getConfigData(AutoCreationConfig.class)).willReturn(autoCreationConfig);
        given(autoCreationConfig.enabled()).willReturn(true);
        given(configuration.getConfigData(LazyCreationConfig.class)).willReturn(lazyCreationConfig);
        given(lazyCreationConfig.enabled()).willReturn(false);

        final var scheduledSubmit = SchedulableTransactionBody.newBuilder()
                .consensusSubmitMessage(ConsensusSubmitMessageTransactionBody.DEFAULT)
                .build();
        final var defs = getThrottleDefs("bootstrap/schedule-create-throttles.json");
        subject.rebuildFor(defs);

        // when
        final var txnInfo = scheduleCreate(scheduledSubmit, waitForExpiry, null);
        final boolean firstAns = subject.shouldThrottle(txnInfo, CONSENSUS_NOW, state);
        boolean subsequentAns = false;
        for (int i = 1; i <= 150; i++) {
            subsequentAns = subject.shouldThrottle(txnInfo, CONSENSUS_NOW.plusNanos(i), state);
        }

        final var throttlesNow = subject.activeThrottlesFor(SCHEDULE_CREATE);
        final var aNow = throttlesNow.get(0);

        // then
        assertFalse(firstAns);
        assertTrue(subsequentAns);
        assertEquals(149999992500000L, aNow.used());
        assertEquals(
                longTermEnabled && throttleType == FRONTEND_THROTTLE && (!waitForExpiry) ? 149999255000000L : 0,
                subject.activeThrottlesFor(CONSENSUS_SUBMIT_MESSAGE).get(0).used());
    }

    @ParameterizedTest
    @CsvSource({
        "FRONTEND_THROTTLE,true,true",
        "FRONTEND_THROTTLE,true,false",
        "FRONTEND_THROTTLE,false,true",
        "FRONTEND_THROTTLE,false,false",
        "BACKEND_THROTTLE,true,true",
        "BACKEND_THROTTLE,true,false",
        "BACKEND_THROTTLE,false,true",
        "BACKEND_THROTTLE,false,false",
    })
    @MockitoSettings(strictness = org.mockito.quality.Strictness.LENIENT)
    void usesScheduleCreateThrottleWithNestedThrottleExempt(
            final ThrottleAccumulator.ThrottleType throttleType,
            final boolean longTermEnabled,
            final boolean waitForExpiry)
            throws IOException {
        // given
        subject = new ThrottleAccumulator(() -> CAPACITY_SPLIT, configProvider, throttleType);

        given(configProvider.getConfiguration()).willReturn(configuration);
        given(configuration.getConfigData(AccountsConfig.class)).willReturn(accountsConfig);
        given(accountsConfig.lastThrottleExempt()).willReturn(100L);
        given(configuration.getConfigData(ContractsConfig.class)).willReturn(contractsConfig);
        given(contractsConfig.throttleThrottleByGas()).willReturn(false);
        given(configuration.getConfigData(SchedulingConfig.class)).willReturn(schedulingConfig);
        given(schedulingConfig.longTermEnabled()).willReturn(longTermEnabled);
        given(configuration.getConfigData(AutoCreationConfig.class)).willReturn(autoCreationConfig);
        given(autoCreationConfig.enabled()).willReturn(true);
        given(configuration.getConfigData(LazyCreationConfig.class)).willReturn(lazyCreationConfig);
        given(lazyCreationConfig.enabled()).willReturn(false);

        final var scheduledSubmit = SchedulableTransactionBody.newBuilder()
                .consensusSubmitMessage(ConsensusSubmitMessageTransactionBody.DEFAULT)
                .build();
        final var defs = getThrottleDefs("bootstrap/schedule-create-throttles.json");
        subject.rebuildFor(defs);

        // when
        final var txnInfo = scheduleCreate(
                scheduledSubmit,
                waitForExpiry,
                AccountID.newBuilder().accountNum(2L).build());
        final boolean firstAns = subject.shouldThrottle(txnInfo, CONSENSUS_NOW, state);
        boolean subsequentAns = false;
        for (int i = 1; i <= 150; i++) {
            subsequentAns = subject.shouldThrottle(txnInfo, CONSENSUS_NOW.plusNanos(i), state);
        }

        final var throttlesNow = subject.activeThrottlesFor(SCHEDULE_CREATE);
        final var aNow = throttlesNow.get(0);

        // then
        assertFalse(firstAns);
        assertTrue(subsequentAns);
        assertEquals(149999992500000L, aNow.used());
        assertEquals(
                0, subject.activeThrottlesFor(CONSENSUS_SUBMIT_MESSAGE).get(0).used());
    }

    @ParameterizedTest
    @EnumSource
    void scheduleCreateAlwaysThrottledWhenNoBody(final ThrottleAccumulator.ThrottleType throttleType)
            throws IOException {
        // given
        subject = new ThrottleAccumulator(() -> CAPACITY_SPLIT, configProvider, throttleType);

        given(configProvider.getConfiguration()).willReturn(configuration);
        given(configuration.getConfigData(AccountsConfig.class)).willReturn(accountsConfig);
        given(accountsConfig.lastThrottleExempt()).willReturn(100L);
        given(configuration.getConfigData(ContractsConfig.class)).willReturn(contractsConfig);
        given(contractsConfig.throttleThrottleByGas()).willReturn(false);

        final var defs = getThrottleDefs("bootstrap/schedule-create-throttles.json");
        subject.rebuildFor(defs);

        // when
        final var txnInfo = scheduleCreate(SchedulableTransactionBody.DEFAULT, false, null);
        final boolean firstAns = subject.shouldThrottle(txnInfo, CONSENSUS_NOW, state);
        for (int i = 1; i <= 150; i++) {
            assertTrue(subject.shouldThrottle(txnInfo, CONSENSUS_NOW.plusNanos(i), state));
        }

        final var throttlesNow = subject.activeThrottlesFor(SCHEDULE_CREATE);
        final var aNow = throttlesNow.get(0);

        // then
        assertTrue(firstAns);
        assertEquals(0, aNow.used());
        assertEquals(
                0, subject.activeThrottlesFor(CONSENSUS_SUBMIT_MESSAGE).get(0).used());
    }

    @ParameterizedTest
    @CsvSource({
        "FRONTEND_THROTTLE,true",
        "FRONTEND_THROTTLE,false",
        "BACKEND_THROTTLE,true",
        "BACKEND_THROTTLE,false",
    })
    @MockitoSettings(strictness = org.mockito.quality.Strictness.LENIENT)
    void usesScheduleCreateThrottleForCryptoTransferNoAutoCreations(
            final ThrottleAccumulator.ThrottleType throttleType, final boolean longTermEnabled) throws IOException {
        // given
        subject = new ThrottleAccumulator(() -> CAPACITY_SPLIT, configProvider, throttleType);

        given(configProvider.getConfiguration()).willReturn(configuration);
        given(configuration.getConfigData(AccountsConfig.class)).willReturn(accountsConfig);
        given(accountsConfig.lastThrottleExempt()).willReturn(100L);
        given(configuration.getConfigData(ContractsConfig.class)).willReturn(contractsConfig);
        given(contractsConfig.throttleThrottleByGas()).willReturn(false);
        given(configuration.getConfigData(SchedulingConfig.class)).willReturn(schedulingConfig);
        given(schedulingConfig.longTermEnabled()).willReturn(longTermEnabled);
        given(configuration.getConfigData(AutoCreationConfig.class)).willReturn(autoCreationConfig);
        given(autoCreationConfig.enabled()).willReturn(true);
        given(configuration.getConfigData(LazyCreationConfig.class)).willReturn(lazyCreationConfig);
        given(lazyCreationConfig.enabled()).willReturn(false);

        given(state.createReadableStates(any())).willReturn(readableStates);

        final var scheduledTransferNoAliases = SchedulableTransactionBody.newBuilder()
                .cryptoTransfer(cryptoTransferWithImplicitCreations(0))
                .build();
        final var defs = getThrottleDefs("bootstrap/schedule-create-throttles.json");
        subject.rebuildFor(defs);

        // when
        final var txnInfo = scheduleCreate(scheduledTransferNoAliases, false, null);
        final boolean ans = subject.shouldThrottle(txnInfo, CONSENSUS_NOW, state);
        final var throttlesNow = subject.activeThrottlesFor(SCHEDULE_CREATE);
        final var aNow = throttlesNow.get(0);

        // then
        assertFalse(ans);
        assertEquals(BucketThrottle.capacityUnitsPerTxn(), aNow.used());
        assertEquals(
                longTermEnabled && throttleType == FRONTEND_THROTTLE ? BucketThrottle.capacityUnitsPerTxn() : 0,
                subject.activeThrottlesFor(CRYPTO_TRANSFER).get(0).used());
    }

    @ParameterizedTest
    @CsvSource({
        "FRONTEND_THROTTLE,true",
        "FRONTEND_THROTTLE,false",
        "BACKEND_THROTTLE,true",
        "BACKEND_THROTTLE,false",
    })
    @MockitoSettings(strictness = org.mockito.quality.Strictness.LENIENT)
    void doesntUseCryptoCreateThrottleForCryptoTransferWithAutoCreationIfAutoAndLazyCreationDisabled(
            final ThrottleAccumulator.ThrottleType throttleType, final boolean longTermEnabled) throws IOException {
        // given
        subject = new ThrottleAccumulator(() -> CAPACITY_SPLIT, configProvider, throttleType);

        given(configProvider.getConfiguration()).willReturn(configuration);
        given(configuration.getConfigData(AccountsConfig.class)).willReturn(accountsConfig);
        given(accountsConfig.lastThrottleExempt()).willReturn(100L);
        given(configuration.getConfigData(ContractsConfig.class)).willReturn(contractsConfig);
        given(contractsConfig.throttleThrottleByGas()).willReturn(false);
        given(configuration.getConfigData(SchedulingConfig.class)).willReturn(schedulingConfig);
        given(schedulingConfig.longTermEnabled()).willReturn(longTermEnabled);
        given(configuration.getConfigData(AutoCreationConfig.class)).willReturn(autoCreationConfig);
        given(autoCreationConfig.enabled()).willReturn(false);
        given(configuration.getConfigData(LazyCreationConfig.class)).willReturn(lazyCreationConfig);
        given(lazyCreationConfig.enabled()).willReturn(false);

        given(state.createReadableStates(any())).willReturn(readableStates);
        given(readableStates.get(any())).willReturn(aliases);

        final var alias = keyToBytes(A_PRIMITIVE_KEY);
        var accountAmounts = new ArrayList<AccountAmount>();
        accountAmounts.add(AccountAmount.newBuilder()
                .amount(-1_000_000_000L)
                .accountID(AccountID.newBuilder().accountNum(3333L).build())
                .build());
        accountAmounts.add(AccountAmount.newBuilder()
                .amount(+1_000_000_000L)
                .accountID(AccountID.newBuilder().alias(alias).build())
                .build());
        final var scheduledTransferWithAutoCreation = SchedulableTransactionBody.newBuilder()
                .cryptoTransfer(CryptoTransferTransactionBody.newBuilder()
                        .transfers(TransferList.newBuilder()
                                .accountAmounts(accountAmounts)
                                .build()))
                .build();

        final var defs = getThrottleDefs("bootstrap/schedule-create-throttles.json");
        subject.rebuildFor(defs);

        // when
        final var txnInfo = scheduleCreate(scheduledTransferWithAutoCreation, false, null);
        final boolean ans = subject.shouldThrottle(txnInfo, CONSENSUS_NOW, state);
        final var throttlesNow = subject.activeThrottlesFor(SCHEDULE_CREATE);
        final var aNow = throttlesNow.get(0);

        // then
        assertFalse(ans);
        assertEquals(BucketThrottle.capacityUnitsPerTxn(), aNow.used());

        assertEquals(
                longTermEnabled && throttleType == FRONTEND_THROTTLE ? BucketThrottle.capacityUnitsPerTxn() : 0,
                subject.activeThrottlesFor(CRYPTO_TRANSFER).get(0).used());
    }

    @ParameterizedTest
    @CsvSource({
        "FRONTEND_THROTTLE,true,true",
        "FRONTEND_THROTTLE,true,false",
        "FRONTEND_THROTTLE,false,true",
        "FRONTEND_THROTTLE,false,false",
        "BACKEND_THROTTLE,true,true",
        "BACKEND_THROTTLE,true,false",
        "BACKEND_THROTTLE,false,true",
        "BACKEND_THROTTLE,false,false",
    })
    @MockitoSettings(strictness = org.mockito.quality.Strictness.LENIENT)
    void doesntUseCryptoCreateThrottleForCryptoTransferWithNoAliases(
            final ThrottleAccumulator.ThrottleType throttleType,
            final boolean longTermEnabled,
            final boolean autoOrLazyCreationEnabled)
            throws IOException {
        // given
        subject = new ThrottleAccumulator(() -> CAPACITY_SPLIT, configProvider, throttleType);

        given(configProvider.getConfiguration()).willReturn(configuration);
        given(configuration.getConfigData(AccountsConfig.class)).willReturn(accountsConfig);
        given(accountsConfig.lastThrottleExempt()).willReturn(100L);
        given(configuration.getConfigData(ContractsConfig.class)).willReturn(contractsConfig);
        given(contractsConfig.throttleThrottleByGas()).willReturn(false);
        given(configuration.getConfigData(SchedulingConfig.class)).willReturn(schedulingConfig);
        given(schedulingConfig.longTermEnabled()).willReturn(longTermEnabled);
        given(configuration.getConfigData(AutoCreationConfig.class)).willReturn(autoCreationConfig);
        given(autoCreationConfig.enabled()).willReturn(autoOrLazyCreationEnabled);
        given(configuration.getConfigData(LazyCreationConfig.class)).willReturn(lazyCreationConfig);
        given(lazyCreationConfig.enabled()).willReturn(!autoOrLazyCreationEnabled);

        given(state.createReadableStates(any())).willReturn(readableStates);

        var accountAmounts = new ArrayList<AccountAmount>();
        accountAmounts.add(AccountAmount.newBuilder()
                .amount(-1_000_000_000L)
                .accountID(AccountID.newBuilder().accountNum(3333L).build())
                .build());
        accountAmounts.add(AccountAmount.newBuilder()
                .amount(+1_000_000_000L)
                .accountID(AccountID.newBuilder().accountNum(4444L).build())
                .build());
        final var scheduledTransferNoAliases = SchedulableTransactionBody.newBuilder()
                .cryptoTransfer(CryptoTransferTransactionBody.newBuilder()
                        .transfers(TransferList.newBuilder()
                                .accountAmounts(accountAmounts)
                                .build()))
                .build();

        final var defs = getThrottleDefs("bootstrap/schedule-create-throttles.json");
        subject.rebuildFor(defs);

        // when
        final var txnInfo = scheduleCreate(scheduledTransferNoAliases, false, null);
        final boolean ans = subject.shouldThrottle(txnInfo, CONSENSUS_NOW, state);
        final var throttlesNow = subject.activeThrottlesFor(SCHEDULE_CREATE);
        final var aNow = throttlesNow.get(0);

        // then
        assertFalse(ans);
        assertEquals(BucketThrottle.capacityUnitsPerTxn(), aNow.used());
        assertEquals(
                longTermEnabled && throttleType == FRONTEND_THROTTLE ? BucketThrottle.capacityUnitsPerTxn() : 0,
                subject.activeThrottlesFor(CRYPTO_TRANSFER).get(0).used());
    }

    @ParameterizedTest
    @CsvSource({
        "FRONTEND_THROTTLE,true,true",
        "FRONTEND_THROTTLE,true,false",
        "FRONTEND_THROTTLE,false,true",
        "FRONTEND_THROTTLE,false,false",
        "BACKEND_THROTTLE,true,true",
        "BACKEND_THROTTLE,true,false",
        "BACKEND_THROTTLE,false,true",
        "BACKEND_THROTTLE,false,false",
    })
    void doesntUseCryptoCreateThrottleForNonCryptoTransfer(
            final ThrottleAccumulator.ThrottleType throttleType,
            final boolean autoCreationEnabled,
            final boolean lazyCreationEnabled)
            throws IOException {
        // given
        subject = new ThrottleAccumulator(() -> CAPACITY_SPLIT, configProvider, throttleType);

        given(configProvider.getConfiguration()).willReturn(configuration);
        given(configuration.getConfigData(AccountsConfig.class)).willReturn(accountsConfig);
        given(accountsConfig.lastThrottleExempt()).willReturn(100L);
        given(configuration.getConfigData(ContractsConfig.class)).willReturn(contractsConfig);
        given(contractsConfig.throttleThrottleByGas()).willReturn(false);
        given(configuration.getConfigData(SchedulingConfig.class)).willReturn(schedulingConfig);
        given(schedulingConfig.longTermEnabled()).willReturn(false);
        given(configuration.getConfigData(AutoCreationConfig.class)).willReturn(autoCreationConfig);
        given(autoCreationConfig.enabled()).willReturn(autoCreationEnabled);
        given(configuration.getConfigData(LazyCreationConfig.class)).willReturn(lazyCreationConfig);
        given(lazyCreationConfig.enabled()).willReturn(lazyCreationEnabled);

        final var scheduledTxn = SchedulableTransactionBody.newBuilder()
                .consensusSubmitMessage(ConsensusSubmitMessageTransactionBody.DEFAULT)
                .build();

        final var defs = getThrottleDefs("bootstrap/schedule-create-throttles.json");
        subject.rebuildFor(defs);

        // when
        final var txnInfo = scheduleCreate(scheduledTxn, false, null);
        final boolean ans = subject.shouldThrottle(txnInfo, CONSENSUS_NOW, state);
        final var throttlesNow = subject.activeThrottlesFor(SCHEDULE_CREATE);
        final var aNow = throttlesNow.get(0);

        // then
        assertFalse(ans);
        assertEquals(BucketThrottle.capacityUnitsPerTxn(), aNow.used());
    }

    @ParameterizedTest
    @CsvSource({
        "FRONTEND_THROTTLE,true",
        "FRONTEND_THROTTLE,false",
        "BACKEND_THROTTLE,true",
        "BACKEND_THROTTLE,false",
    })
    @MockitoSettings(strictness = org.mockito.quality.Strictness.LENIENT)
    void usesCryptoCreateThrottleForCryptoTransferWithAutoCreationInScheduleCreate(
            final ThrottleAccumulator.ThrottleType throttleType, final boolean longTermEnabled) throws IOException {
        // given
        subject = new ThrottleAccumulator(() -> CAPACITY_SPLIT, configProvider, throttleType);

        given(configProvider.getConfiguration()).willReturn(configuration);
        given(configuration.getConfigData(AccountsConfig.class)).willReturn(accountsConfig);
        given(accountsConfig.lastThrottleExempt()).willReturn(100L);
        given(configuration.getConfigData(ContractsConfig.class)).willReturn(contractsConfig);
        given(contractsConfig.throttleThrottleByGas()).willReturn(false);
        given(configuration.getConfigData(SchedulingConfig.class)).willReturn(schedulingConfig);
        given(schedulingConfig.longTermEnabled()).willReturn(longTermEnabled);
        given(configuration.getConfigData(AutoCreationConfig.class)).willReturn(autoCreationConfig);
        given(autoCreationConfig.enabled()).willReturn(true);
        given(configuration.getConfigData(LazyCreationConfig.class)).willReturn(lazyCreationConfig);
        given(lazyCreationConfig.enabled()).willReturn(false);

        given(state.createReadableStates(any())).willReturn(readableStates);
        given(readableStates.get(any())).willReturn(aliases);

        final var alias = keyToBytes(A_PRIMITIVE_KEY);
        if (!(throttleType != FRONTEND_THROTTLE && longTermEnabled)) {
            given(aliases.get(any())).willReturn(null);
        }

        var accountAmounts = new ArrayList<AccountAmount>();
        accountAmounts.add(AccountAmount.newBuilder()
                .amount(-1_000_000_000L)
                .accountID(AccountID.newBuilder().accountNum(3333L).build())
                .build());
        accountAmounts.add(AccountAmount.newBuilder()
                .amount(+1_000_000_000L)
                .accountID(AccountID.newBuilder().alias(alias).build())
                .build());
        final var scheduledTransferWithAutoCreation = SchedulableTransactionBody.newBuilder()
                .cryptoTransfer(CryptoTransferTransactionBody.newBuilder()
                        .transfers(TransferList.newBuilder()
                                .accountAmounts(accountAmounts)
                                .build()))
                .build();

        final var defs = getThrottleDefs("bootstrap/schedule-create-throttles.json");
        subject.rebuildFor(defs);

        // when
        final var txnInfo = scheduleCreate(scheduledTransferWithAutoCreation, false, null);
        final boolean ans = subject.shouldThrottle(txnInfo, CONSENSUS_NOW, state);
        final var throttlesNow = subject.activeThrottlesFor(SCHEDULE_CREATE);
        final var aNow = throttlesNow.get(0);

        // then
        assertFalse(ans);
        if (longTermEnabled && throttleType == FRONTEND_THROTTLE) {
            // with long term enabled, we count the schedule create in addition to the auto
            // creations, which
            // is how it should have been to start with
            assertEquals(51 * BucketThrottle.capacityUnitsPerTxn(), aNow.used());
        } else if (longTermEnabled) {
            // with long term enabled, consensus throttles do not count the contained txn
            assertEquals(BucketThrottle.capacityUnitsPerTxn(), aNow.used());
        } else {
            assertEquals(50 * BucketThrottle.capacityUnitsPerTxn(), aNow.used());
        }

        assertEquals(0, subject.activeThrottlesFor(CRYPTO_TRANSFER).get(0).used());
    }

    @ParameterizedTest
    @CsvSource({
        "FRONTEND_THROTTLE,true",
        "FRONTEND_THROTTLE,false",
        "BACKEND_THROTTLE,true",
        "BACKEND_THROTTLE,false",
    })
    @MockitoSettings(strictness = org.mockito.quality.Strictness.LENIENT)
    void usesScheduleCreateThrottleForAliasedCryptoTransferWithNoAutoCreation(
            final ThrottleAccumulator.ThrottleType throttleType, final boolean longTermEnabled) throws IOException {
        // given
        subject = new ThrottleAccumulator(() -> CAPACITY_SPLIT, configProvider, throttleType);

        given(configProvider.getConfiguration()).willReturn(configuration);
        given(configuration.getConfigData(AccountsConfig.class)).willReturn(accountsConfig);
        given(accountsConfig.lastThrottleExempt()).willReturn(100L);
        given(configuration.getConfigData(ContractsConfig.class)).willReturn(contractsConfig);
        given(contractsConfig.throttleThrottleByGas()).willReturn(false);
        given(configuration.getConfigData(SchedulingConfig.class)).willReturn(schedulingConfig);
        given(schedulingConfig.longTermEnabled()).willReturn(longTermEnabled);
        given(configuration.getConfigData(AutoCreationConfig.class)).willReturn(autoCreationConfig);
        given(autoCreationConfig.enabled()).willReturn(true);
        given(configuration.getConfigData(LazyCreationConfig.class)).willReturn(lazyCreationConfig);
        given(lazyCreationConfig.enabled()).willReturn(false);

        given(state.createReadableStates(any())).willReturn(readableStates);
        given(readableStates.get(any())).willReturn(aliases);

        final var alias = keyToBytes(A_PRIMITIVE_KEY);
        if (!(throttleType != FRONTEND_THROTTLE && longTermEnabled)) {
            given(aliases.get(any()))
                    .willReturn(AccountID.newBuilder().accountNum(1_234L).build());
        }

        var accountAmounts = new ArrayList<AccountAmount>();
        accountAmounts.add(AccountAmount.newBuilder()
                .amount(-1_000_000_000L)
                .accountID(AccountID.newBuilder().accountNum(3333L).build())
                .build());
        accountAmounts.add(AccountAmount.newBuilder()
                .amount(+1_000_000_000L)
                .accountID(AccountID.newBuilder().alias(alias).build())
                .build());
        final var scheduledTransferWithAutoCreation = SchedulableTransactionBody.newBuilder()
                .cryptoTransfer(CryptoTransferTransactionBody.newBuilder()
                        .transfers(TransferList.newBuilder()
                                .accountAmounts(accountAmounts)
                                .build()))
                .build();

        final var defs = getThrottleDefs("bootstrap/schedule-create-throttles.json");
        subject.rebuildFor(defs);

        // when
        final var txnInfo = scheduleCreate(scheduledTransferWithAutoCreation, false, null);
        final boolean ans = subject.shouldThrottle(txnInfo, CONSENSUS_NOW, state);
        final var throttlesNow = subject.activeThrottlesFor(SCHEDULE_CREATE);
        final var aNow = throttlesNow.get(0);

        // then
        assertFalse(ans);
        assertEquals(BucketThrottle.capacityUnitsPerTxn(), aNow.used());

        assertEquals(
                longTermEnabled && throttleType == FRONTEND_THROTTLE ? BucketThrottle.capacityUnitsPerTxn() : 0,
                subject.activeThrottlesFor(CRYPTO_TRANSFER).get(0).used());
    }

    @Test
    void reclaimsAllUsagesOnThrottledShouldThrottleTxn() throws IOException {
        // given
        subject = new ThrottleAccumulator(() -> CAPACITY_SPLIT, configProvider, FRONTEND_THROTTLE);

        given(configProvider.getConfiguration()).willReturn(configuration);
        given(configuration.getConfigData(AccountsConfig.class)).willReturn(accountsConfig);
        given(accountsConfig.lastThrottleExempt()).willReturn(100L);
        given(configuration.getConfigData(ContractsConfig.class)).willReturn(contractsConfig);
        given(contractsConfig.throttleThrottleByGas()).willReturn(false);
        given(configuration.getConfigData(SchedulingConfig.class)).willReturn(schedulingConfig);
        given(schedulingConfig.longTermEnabled()).willReturn(true);

        final var scheduledSubmit = SchedulableTransactionBody.newBuilder()
                .consensusSubmitMessage(ConsensusSubmitMessageTransactionBody.DEFAULT)
                .build();
        final var defs = getThrottleDefs("bootstrap/schedule-create-throttles-inverted.json");
        subject.rebuildFor(defs);

        final var txnInfo = scheduleCreate(scheduledSubmit, false, null);
        final boolean firstAns = subject.shouldThrottle(txnInfo, CONSENSUS_NOW, state);
        boolean subsequentAns = false;
        for (int i = 1; i <= 150; i++) {
            subsequentAns = subject.shouldThrottle(txnInfo, CONSENSUS_NOW.plusNanos(i), state);
        }

        assertFalse(firstAns);
        assertTrue(subsequentAns);
        assertEquals(
                4999250000000L,
                subject.activeThrottlesFor(SCHEDULE_CREATE).get(0).used());

        assertEquals(
                4999999250000L,
                subject.activeThrottlesFor(CONSENSUS_SUBMIT_MESSAGE).get(0).used());

        // when
        subject.resetUsage();

        // then
        assertEquals(0L, subject.activeThrottlesFor(SCHEDULE_CREATE).get(0).used());
        assertEquals(
                0L, subject.activeThrottlesFor(CONSENSUS_SUBMIT_MESSAGE).get(0).used());
    }

    @ParameterizedTest
    @CsvSource({
        "FRONTEND_THROTTLE,true,true",
        "FRONTEND_THROTTLE,true,false",
        "FRONTEND_THROTTLE,false,true",
        "FRONTEND_THROTTLE,false,false",
        "BACKEND_THROTTLE,true,true",
        "BACKEND_THROTTLE,true,false",
        "BACKEND_THROTTLE,false,true",
        "BACKEND_THROTTLE,false,false",
    })
    void usesScheduleSignThrottle(
            final ThrottleAccumulator.ThrottleType throttleType,
            final boolean longTermEnabled,
            final boolean waitForExpiry)
            throws IOException {
        // given
        subject = new ThrottleAccumulator(() -> CAPACITY_SPLIT, configProvider, throttleType);

        given(configProvider.getConfiguration()).willReturn(configuration);
        given(configuration.getConfigData(AccountsConfig.class)).willReturn(accountsConfig);
        given(accountsConfig.lastThrottleExempt()).willReturn(100L);
        given(configuration.getConfigData(ContractsConfig.class)).willReturn(contractsConfig);
        given(contractsConfig.throttleThrottleByGas()).willReturn(false);
        given(configuration.getConfigData(SchedulingConfig.class)).willReturn(schedulingConfig);
        given(schedulingConfig.longTermEnabled()).willReturn(longTermEnabled);

        if (longTermEnabled && throttleType == FRONTEND_THROTTLE) {
            final var scheduledSubmit = SchedulableTransactionBody.newBuilder()
                    .consensusSubmitMessage(ConsensusSubmitMessageTransactionBody.DEFAULT)
                    .build();

            final var txnInfo = scheduleCreate(scheduledSubmit, waitForExpiry, null);
            final var schedule = Schedule.newBuilder()
                    .waitForExpiry(txnInfo.txBody().scheduleCreate().waitForExpiry())
                    .originalCreateTransaction(txnInfo.txBody())
                    .payerAccountId(txnInfo.payerID())
                    .scheduledTransaction(scheduledSubmit)
                    .build();

            given(state.createReadableStates(any())).willReturn(readableStates);
            given(readableStates.get(any())).willReturn(schedules);
            given(schedules.get(SCHEDULE_ID)).willReturn(schedule);
        }

        final var defs = getThrottleDefs("bootstrap/schedule-create-throttles.json");
        subject.rebuildFor(defs);

        // when
        final var txnInfo = scheduleSign(SCHEDULE_ID);
        final boolean firstAns = subject.shouldThrottle(txnInfo, CONSENSUS_NOW, state);
        boolean subsequentAns = false;
        for (int i = 1; i <= 150; i++) {
            subsequentAns = subject.shouldThrottle(txnInfo, CONSENSUS_NOW.plusNanos(i), state);
        }

        final var throttlesNow = subject.activeThrottlesFor(SCHEDULE_SIGN);
        final var aNow = throttlesNow.get(0);

        // then
        assertFalse(firstAns);
        assertTrue(subsequentAns);
        assertEquals(149999992500000L, aNow.used());

        assertEquals(
                longTermEnabled && throttleType == FRONTEND_THROTTLE && (!waitForExpiry) ? 149999255000000L : 0,
                subject.activeThrottlesFor(CONSENSUS_SUBMIT_MESSAGE).get(0).used());
    }

    @ParameterizedTest
    @CsvSource({
        "FRONTEND_THROTTLE,true,true",
        "FRONTEND_THROTTLE,true,false",
        "FRONTEND_THROTTLE,false,true",
        "FRONTEND_THROTTLE,false,false",
        "BACKEND_THROTTLE,true,true",
        "BACKEND_THROTTLE,true,false",
        "BACKEND_THROTTLE,false,true",
        "BACKEND_THROTTLE,false,false",
    })
    void usesScheduleSignThrottleWithNestedThrottleExempt(
            final ThrottleAccumulator.ThrottleType throttleType,
            final boolean longTermEnabled,
            final boolean waitForExpiry)
            throws IOException {
        // given
        subject = new ThrottleAccumulator(() -> CAPACITY_SPLIT, configProvider, throttleType);

        given(configProvider.getConfiguration()).willReturn(configuration);
        given(configuration.getConfigData(AccountsConfig.class)).willReturn(accountsConfig);
        given(accountsConfig.lastThrottleExempt()).willReturn(100L);
        given(configuration.getConfigData(ContractsConfig.class)).willReturn(contractsConfig);
        given(contractsConfig.throttleThrottleByGas()).willReturn(false);
        given(configuration.getConfigData(SchedulingConfig.class)).willReturn(schedulingConfig);
        given(schedulingConfig.longTermEnabled()).willReturn(longTermEnabled);

        if (longTermEnabled && throttleType == FRONTEND_THROTTLE) {
            final var scheduledSubmit = SchedulableTransactionBody.newBuilder()
                    .consensusSubmitMessage(ConsensusSubmitMessageTransactionBody.DEFAULT)
                    .build();

            final var txnInfo = scheduleCreate(
                    scheduledSubmit,
                    waitForExpiry,
                    AccountID.newBuilder().accountNum(2L).build());
            final var schedule = Schedule.newBuilder()
                    .waitForExpiry(txnInfo.txBody().scheduleCreate().waitForExpiry())
                    .originalCreateTransaction(txnInfo.txBody())
                    .payerAccountId(AccountID.newBuilder().accountNum(2L).build())
                    .scheduledTransaction(scheduledSubmit)
                    .build();

            given(state.createReadableStates(any())).willReturn(readableStates);
            given(readableStates.get(any())).willReturn(schedules);
            given(schedules.get(SCHEDULE_ID)).willReturn(schedule);
        }

        final var defs = getThrottleDefs("bootstrap/schedule-create-throttles.json");
        subject.rebuildFor(defs);

        // when
        final var txnInfo = scheduleSign(SCHEDULE_ID);
        final boolean firstAns = subject.shouldThrottle(txnInfo, CONSENSUS_NOW, state);
        boolean subsequentAns = false;
        for (int i = 1; i <= 150; i++) {
            subsequentAns = subject.shouldThrottle(txnInfo, CONSENSUS_NOW.plusNanos(i), state);
        }

        final var throttlesNow = subject.activeThrottlesFor(SCHEDULE_SIGN);
        final var aNow = throttlesNow.get(0);

        // then
        assertFalse(firstAns);
        assertTrue(subsequentAns);
        assertEquals(149999992500000L, aNow.used());

        assertEquals(
                0, subject.activeThrottlesFor(CONSENSUS_SUBMIT_MESSAGE).get(0).used());
    }

    @Test
    void scheduleSignAlwaysThrottledWhenNoBody() throws IOException {
        // given
        subject = new ThrottleAccumulator(() -> CAPACITY_SPLIT, configProvider, FRONTEND_THROTTLE);

        given(configProvider.getConfiguration()).willReturn(configuration);
        given(configuration.getConfigData(AccountsConfig.class)).willReturn(accountsConfig);
        given(accountsConfig.lastThrottleExempt()).willReturn(100L);
        given(configuration.getConfigData(ContractsConfig.class)).willReturn(contractsConfig);
        given(contractsConfig.throttleThrottleByGas()).willReturn(false);
        given(configuration.getConfigData(SchedulingConfig.class)).willReturn(schedulingConfig);
        given(schedulingConfig.longTermEnabled()).willReturn(true);

        final var defs = getThrottleDefs("bootstrap/schedule-create-throttles.json");
        subject.rebuildFor(defs);

        final var scheduleCreateTxnInfo = scheduleCreate(SchedulableTransactionBody.DEFAULT, false, null);
        final var schedule = Schedule.newBuilder()
                .waitForExpiry(scheduleCreateTxnInfo.txBody().scheduleCreate().waitForExpiry())
                .originalCreateTransaction(scheduleCreateTxnInfo.txBody())
                .payerAccountId(AccountID.newBuilder().accountNum(2L).build())
                .scheduledTransaction(SchedulableTransactionBody.DEFAULT)
                .build();

        given(state.createReadableStates(any())).willReturn(readableStates);
        given(readableStates.get(any())).willReturn(schedules);
        given(schedules.get(SCHEDULE_ID)).willReturn(schedule);

        // when
        final var scheduleSignTxnInfo = scheduleSign(SCHEDULE_ID);
        final var firstAns = subject.shouldThrottle(scheduleSignTxnInfo, CONSENSUS_NOW, state);
        for (int i = 1; i <= 150; i++) {
            assertTrue(subject.shouldThrottle(scheduleSignTxnInfo, CONSENSUS_NOW.plusNanos(i), state));
        }

        final var throttlesNow = subject.activeThrottlesFor(SCHEDULE_SIGN);
        final var aNow = throttlesNow.get(0);

        // then
        assertTrue(firstAns);
        assertEquals(0L, aNow.used());
        assertEquals(
                0, subject.activeThrottlesFor(CONSENSUS_SUBMIT_MESSAGE).get(0).used());
    }

    @Test
    void scheduleSignAlwaysThrottledWhenNotExisting() throws IOException {
        // given
        subject = new ThrottleAccumulator(() -> CAPACITY_SPLIT, configProvider, FRONTEND_THROTTLE);

        given(configProvider.getConfiguration()).willReturn(configuration);
        given(configuration.getConfigData(AccountsConfig.class)).willReturn(accountsConfig);
        given(accountsConfig.lastThrottleExempt()).willReturn(100L);
        given(configuration.getConfigData(ContractsConfig.class)).willReturn(contractsConfig);
        given(contractsConfig.throttleThrottleByGas()).willReturn(false);
        given(configuration.getConfigData(SchedulingConfig.class)).willReturn(schedulingConfig);
        given(schedulingConfig.longTermEnabled()).willReturn(true);

        final var defs = getThrottleDefs("bootstrap/schedule-create-throttles.json");
        subject.rebuildFor(defs);

        given(state.createReadableStates(any())).willReturn(readableStates);
        given(readableStates.get(any())).willReturn(schedules);

        // when
        final var scheduleSignTxnInfo = scheduleSign(SCHEDULE_ID);
        final var firstAns = subject.shouldThrottle(scheduleSignTxnInfo, CONSENSUS_NOW, state);
        for (int i = 1; i <= 150; i++) {
            assertTrue(subject.shouldThrottle(scheduleSignTxnInfo, CONSENSUS_NOW.plusNanos(i), state));
        }

        final var throttlesNow = subject.activeThrottlesFor(SCHEDULE_SIGN);
        final var aNow = throttlesNow.get(0);

        assertTrue(firstAns);
        assertEquals(0L, aNow.used());

        assertEquals(
                0, subject.activeThrottlesFor(CONSENSUS_SUBMIT_MESSAGE).get(0).used());
    }

    @ParameterizedTest
    @CsvSource({
        "FRONTEND_THROTTLE,true",
        "FRONTEND_THROTTLE,false",
        "BACKEND_THROTTLE,true",
        "BACKEND_THROTTLE,false",
    })
    @MockitoSettings(strictness = org.mockito.quality.Strictness.LENIENT)
    void usesCryptoCreateThrottleForCryptoTransferWithAutoCreationInScheduleSign(
            final ThrottleAccumulator.ThrottleType throttleType, final boolean longTermEnabled) throws IOException {

        // given
        subject = new ThrottleAccumulator(() -> CAPACITY_SPLIT, configProvider, throttleType);

        given(configProvider.getConfiguration()).willReturn(configuration);
        given(configuration.getConfigData(AccountsConfig.class)).willReturn(accountsConfig);
        given(accountsConfig.lastThrottleExempt()).willReturn(100L);
        given(configuration.getConfigData(ContractsConfig.class)).willReturn(contractsConfig);
        given(contractsConfig.throttleThrottleByGas()).willReturn(false);
        given(configuration.getConfigData(SchedulingConfig.class)).willReturn(schedulingConfig);
        given(schedulingConfig.longTermEnabled()).willReturn(longTermEnabled);
        given(configuration.getConfigData(AutoCreationConfig.class)).willReturn(autoCreationConfig);
        given(autoCreationConfig.enabled()).willReturn(true);
        given(configuration.getConfigData(LazyCreationConfig.class)).willReturn(lazyCreationConfig);
        given(lazyCreationConfig.enabled()).willReturn(false);

        given(state.createReadableStates(any())).willReturn(readableStates);
        given(readableStates.get("ALIASES")).willReturn(aliases);

        final var alias = keyToBytes(A_PRIMITIVE_KEY);
        if (throttleType == FRONTEND_THROTTLE && longTermEnabled) {
            given(aliases.get(any())).willReturn(null);
        }

        if (longTermEnabled && throttleType == FRONTEND_THROTTLE) {
            var accountAmounts = new ArrayList<AccountAmount>();
            accountAmounts.add(AccountAmount.newBuilder()
                    .amount(-1_000_000_000L)
                    .accountID(AccountID.newBuilder().accountNum(3333L).build())
                    .build());
            accountAmounts.add(AccountAmount.newBuilder()
                    .amount(+1_000_000_000L)
                    .accountID(AccountID.newBuilder().alias(alias).build())
                    .build());
            final var scheduledTransferWithAutoCreation = SchedulableTransactionBody.newBuilder()
                    .cryptoTransfer(CryptoTransferTransactionBody.newBuilder()
                            .transfers(TransferList.newBuilder()
                                    .accountAmounts(accountAmounts)
                                    .build()))
                    .build();

            final var scheduleCreateTxnInfo = scheduleCreate(scheduledTransferWithAutoCreation, false, null);
            final var schedule = Schedule.newBuilder()
                    .waitForExpiry(
                            scheduleCreateTxnInfo.txBody().scheduleCreate().waitForExpiry())
                    .originalCreateTransaction(scheduleCreateTxnInfo.txBody())
                    .payerAccountId(scheduleCreateTxnInfo.payerID())
                    .scheduledTransaction(scheduledTransferWithAutoCreation)
                    .build();
            given(readableStates.get(SCHEDULES_BY_ID_KEY)).willReturn(schedules);
            given(schedules.get(SCHEDULE_ID)).willReturn(schedule);
        }

        final var defs = getThrottleDefs("bootstrap/schedule-create-throttles.json");
        subject.rebuildFor(defs);

        // when
        final var scheduleSignTxnInfo = scheduleSign(SCHEDULE_ID);
        final var ans = subject.shouldThrottle(scheduleSignTxnInfo, CONSENSUS_NOW, state);
        final var throttlesNow = subject.activeThrottlesFor(SCHEDULE_SIGN);
        final var aNow = throttlesNow.get(0);

        // then
        assertFalse(ans);
        if (longTermEnabled && throttleType == FRONTEND_THROTTLE) {
            // with long term enabled, we count the schedule create in addition to the auto
            // creations, which
            // is how it should have been to start with
            assertEquals(51 * BucketThrottle.capacityUnitsPerTxn(), aNow.used());
        } else {
            // with long term disabled or mode not being HAPI, ScheduleSign is the only part that
            // counts
            assertEquals(BucketThrottle.capacityUnitsPerTxn(), aNow.used());
        }

        assertEquals(0, subject.activeThrottlesFor(CRYPTO_TRANSFER).get(0).used());
    }

    @NotNull
    private static Bytes keyToBytes(Key key) throws IOException {
        final var dataBuffer = getThreadLocalDataBuffer();
        Key.PROTOBUF.write(key, dataBuffer);
        // clamp limit to bytes written
        dataBuffer.limit(dataBuffer.position());
        return dataBuffer.getBytes(0, dataBuffer.length());
    }

    private TransactionInfo scheduleCreate(
            final SchedulableTransactionBody inner, boolean waitForExpiry, AccountID customPayer) {
        final var schedule = ScheduleCreateTransactionBody.newBuilder()
                .waitForExpiry(waitForExpiry)
                .scheduledTransactionBody(inner);
        if (customPayer != null) {
            schedule.payerAccountID(customPayer);
        }
        final var body = TransactionBody.newBuilder()
                .transactionID(TransactionID.newBuilder().accountID(PAYER_ID).build())
                .scheduleCreate(schedule)
                .build();
        final var txn = Transaction.newBuilder().body(body).build();
        return new TransactionInfo(
                txn,
                body,
                TransactionID.newBuilder().accountID(PAYER_ID).build(),
                PAYER_ID,
                SignatureMap.DEFAULT,
                Bytes.EMPTY,
                SCHEDULE_CREATE);
    }

    private TransactionInfo scheduleSign(ScheduleID scheduleID) {
        final var schedule = ScheduleSignTransactionBody.newBuilder().scheduleID(scheduleID);
        final var body = TransactionBody.newBuilder()
                .transactionID(TransactionID.newBuilder().accountID(PAYER_ID).build())
                .scheduleSign(schedule)
                .build();
        final var txn = Transaction.newBuilder().body(body).build();
        return new TransactionInfo(
                txn,
                body,
                TransactionID.newBuilder().accountID(PAYER_ID).build(),
                PAYER_ID,
                SignatureMap.DEFAULT,
                Bytes.EMPTY,
                SCHEDULE_SIGN);
    }

    private ThrottleDefinitions getThrottleDefs(String testResource) throws IOException {
        try (InputStream in = ThrottleDefinitions.class.getClassLoader().getResourceAsStream(testResource)) {
            var om = new ObjectMapper();
            var throttleDefinitionsObj = om.readValue(
                    in, com.hedera.node.app.hapi.utils.sysfiles.domain.throttling.ThrottleDefinitions.class);
            final var throttleDefsBytes =
                    Bytes.wrap(throttleDefinitionsObj.toProto().toByteArray());
            return ThrottleDefinitions.PROTOBUF.parse(throttleDefsBytes.toReadableSequentialData());
        }
    }

    private CryptoTransferTransactionBody cryptoTransferWithImplicitCreations(int numImplicitCreations) {
        var accountAmounts = new ArrayList<AccountAmount>();
        for (int i = 1; i <= numImplicitCreations; i++) {
            accountAmounts.add(AccountAmount.newBuilder()
                    .accountID(AccountID.newBuilder()
                            .alias(Bytes.wrap("abcdeabcdeabcdeabcde"))
                            .build())
                    .amount(i)
                    .build());
        }

        return CryptoTransferTransactionBody.newBuilder()
                .transfers(
                        TransferList.newBuilder().accountAmounts(accountAmounts).build())
                .build();
    }
}
