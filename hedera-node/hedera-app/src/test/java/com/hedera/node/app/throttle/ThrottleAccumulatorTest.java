/*
 * Copyright (C) 2023-2024 Hedera Hashgraph, LLC
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
import static com.hedera.hapi.node.base.HederaFunctionality.CONTRACT_CALL;
import static com.hedera.hapi.node.base.HederaFunctionality.CONTRACT_CREATE;
import static com.hedera.hapi.node.base.HederaFunctionality.CRYPTO_GET_ACCOUNT_BALANCE;
import static com.hedera.hapi.node.base.HederaFunctionality.CRYPTO_TRANSFER;
import static com.hedera.hapi.node.base.HederaFunctionality.ETHEREUM_TRANSACTION;
import static com.hedera.hapi.node.base.HederaFunctionality.GET_VERSION_INFO;
import static com.hedera.hapi.node.base.HederaFunctionality.NETWORK_GET_EXECUTION_TIME;
import static com.hedera.hapi.node.base.HederaFunctionality.SCHEDULE_CREATE;
import static com.hedera.hapi.node.base.HederaFunctionality.SCHEDULE_SIGN;
import static com.hedera.hapi.node.base.HederaFunctionality.TOKEN_BURN;
import static com.hedera.hapi.node.base.HederaFunctionality.TOKEN_MINT;
import static com.hedera.node.app.service.schedule.impl.ScheduleServiceImpl.SCHEDULES_BY_ID_KEY;
import static com.hedera.node.app.throttle.ThrottleAccumulator.ThrottleType.FRONTEND_THROTTLE;
import static com.hedera.pbj.runtime.ProtoTestTools.getThreadLocalDataBuffer;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.collection.IsIterableContainingInOrder.contains;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.protobuf.ByteString;
import com.hedera.hapi.node.base.AccountAmount;
import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.Key;
import com.hedera.hapi.node.base.ScheduleID;
import com.hedera.hapi.node.base.SignatureMap;
import com.hedera.hapi.node.base.Transaction;
import com.hedera.hapi.node.base.TransactionID;
import com.hedera.hapi.node.base.TransferList;
import com.hedera.hapi.node.consensus.ConsensusSubmitMessageTransactionBody;
import com.hedera.hapi.node.contract.ContractCallTransactionBody;
import com.hedera.hapi.node.contract.ContractCreateTransactionBody;
import com.hedera.hapi.node.contract.EthereumTransactionBody;
import com.hedera.hapi.node.scheduled.SchedulableTransactionBody;
import com.hedera.hapi.node.scheduled.ScheduleCreateTransactionBody;
import com.hedera.hapi.node.scheduled.ScheduleSignTransactionBody;
import com.hedera.hapi.node.state.schedule.Schedule;
import com.hedera.hapi.node.token.CryptoTransferTransactionBody;
import com.hedera.hapi.node.token.TokenMintTransactionBody;
import com.hedera.hapi.node.transaction.Query;
import com.hedera.hapi.node.transaction.ThrottleDefinitions;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.node.app.hapi.utils.sysfiles.domain.throttling.ScaleFactor;
import com.hedera.node.app.hapi.utils.throttles.BucketThrottle;
import com.hedera.node.app.hapi.utils.throttles.DeterministicThrottle;
import com.hedera.node.app.hapi.utils.throttles.GasLimitDeterministicThrottle;
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
import com.hedera.node.config.data.TokensConfig;
import com.hedera.pbj.runtime.ParseException;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Assertions;
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
    private static final Instant TIME_INSTANT = Instant.ofEpochSecond(1_234_567L, 123);
    private static final AccountID PAYER_ID =
            AccountID.newBuilder().accountNum(1234L).build();
    private static final Key A_PRIMITIVE_KEY = Key.newBuilder()
            .ed25519(Bytes.wrap("01234567890123456789012345678901"))
            .build();
    private static final ScheduleID SCHEDULE_ID =
            ScheduleID.newBuilder().scheduleNum(333333L).build();

    private static final ScaleFactor NFT_SCALE_FACTOR = ScaleFactor.from("5:2");
    private static final String ALIASES_KEY = "ALIASES";

    private static final Bytes ETH_LAZY_CREATE = Bytes.fromHex(
            "02f8ad82012a80a000000000000000000000000000000000000000000000000000000000000003e8a0000000000000000000000000000000000000000000000000000000746a528800831e848094fee687d5088faff48013a6767505c027e2742536880de0b6b3a764000080c080a0f5ddf2394311e634e2147bf38583a017af45f4326bdf5746cac3a1110f973e4fa025bad52d9a9f8b32eb983c9fb8959655258bd75e2826b2c6a48d4c26ec30d112");

    @LoggingSubject
    private ThrottleAccumulator subject;

    @LoggingTarget
    private LogCaptor logCaptor;

    @Mock
    private ConfigProvider configProvider;

    @Mock
    private GasLimitDeterministicThrottle gasThrottle;

    @Mock
    private ThrottleMetrics throttleMetrics;

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
    private TokensConfig tokensConfig;

    @Mock
    private HederaState state;

    @Mock
    private ReadableStates readableStates;

    @Mock
    private ReadableKVState aliases;

    @Mock
    private ReadableKVState schedules;

    @Mock
    private Query query;

    @Mock
    private TransactionInfo transactionInfo;

    @Test
    void worksAsExpectedForKnownQueries() throws IOException, ParseException {
        // given
        subject = new ThrottleAccumulator(
                () -> CAPACITY_SPLIT, configProvider, FRONTEND_THROTTLE, throttleMetrics, gasThrottle);
        given(configProvider.getConfiguration()).willReturn(configuration);
        given(configuration.getConfigData(AccountsConfig.class)).willReturn(accountsConfig);
        given(accountsConfig.lastThrottleExempt()).willReturn(100L);

        final var defs = getThrottleDefs("bootstrap/throttles.json");
        subject.rebuildFor(defs);

        // when
        final var queryPayerId = AccountID.newBuilder().accountNum(1_234L).build();
        var noAns = subject.shouldThrottle(CRYPTO_GET_ACCOUNT_BALANCE, TIME_INSTANT, query, queryPayerId);
        subject.shouldThrottle(GET_VERSION_INFO, TIME_INSTANT.plusNanos(1), query, queryPayerId);
        final var yesAns = subject.shouldThrottle(GET_VERSION_INFO, TIME_INSTANT.plusNanos(2), query, queryPayerId);
        final var throttlesNow = subject.activeThrottlesFor(CRYPTO_GET_ACCOUNT_BALANCE);
        final var dNow = throttlesNow.get(0);

        // then
        assertFalse(noAns);
        assertTrue(yesAns);
        assertEquals(10999999990000L, dNow.used());
    }

    @Test
    void worksAsExpectedForUnknownQueries() throws IOException, ParseException {
        // given
        subject = new ThrottleAccumulator(
                () -> CAPACITY_SPLIT, configProvider, FRONTEND_THROTTLE, throttleMetrics, gasThrottle);
        given(configProvider.getConfiguration()).willReturn(configuration);
        given(configuration.getConfigData(AccountsConfig.class)).willReturn(accountsConfig);
        given(accountsConfig.lastThrottleExempt()).willReturn(100L);

        final var defs = getThrottleDefs("bootstrap/throttles.json");

        // when
        subject.rebuildFor(defs);

        // then
        final var queryPayerId = AccountID.newBuilder().accountNum(1_234L).build();
        assertTrue(subject.shouldThrottle(NETWORK_GET_EXECUTION_TIME, TIME_INSTANT, query, queryPayerId));
    }

    @ParameterizedTest
    @EnumSource
    void shouldThrottleByGasAndTotalAllowedGasPerSecNotSetOrZero(ThrottleAccumulator.ThrottleType throttleType) {
        // given
        subject = new ThrottleAccumulator(() -> CAPACITY_SPLIT, configProvider, throttleType, throttleMetrics);
        given(configProvider.getConfiguration()).willReturn(configuration);
        given(configuration.getConfigData(ContractsConfig.class)).willReturn(contractsConfig);
        given(contractsConfig.throttleThrottleByGas()).willReturn(true);

        // when
        subject.applyGasConfig();

        // then
        System.out.println(logCaptor.warnLogs());
        assertThat(logCaptor.warnLogs(), contains(throttleType + " gas throttling enabled, but limited to 0 gas/sec"));
    }

    @ParameterizedTest
    @EnumSource
    void managerBehavesAsExpectedForFungibleMint(ThrottleAccumulator.ThrottleType throttleType)
            throws IOException, ParseException {
        // given
        subject = new ThrottleAccumulator(
                () -> CAPACITY_SPLIT, configProvider, throttleType, throttleMetrics, gasThrottle);
        given(configProvider.getConfiguration()).willReturn(configuration);
        given(configuration.getConfigData(AccountsConfig.class)).willReturn(accountsConfig);
        given(accountsConfig.lastThrottleExempt()).willReturn(100L);
        given(configuration.getConfigData(ContractsConfig.class)).willReturn(contractsConfig);
        given(contractsConfig.throttleThrottleByGas()).willReturn(true);

        given(transactionInfo.payerID())
                .willReturn(AccountID.newBuilder().accountNum(1234L).build());

        final var defs = getThrottleDefs("bootstrap/throttles.json");

        final int numNfts = 0;
        givenMintWith(numNfts);

        // when
        subject.rebuildFor(defs);
        // and
        var firstAns = subject.shouldThrottle(transactionInfo, TIME_INSTANT, state);
        boolean subsequentAns = false;
        for (int i = 1; i <= 3000; i++) {
            subsequentAns = subject.shouldThrottle(transactionInfo, TIME_INSTANT.plusNanos(i), state);
        }
        var throttlesNow = subject.activeThrottlesFor(TOKEN_MINT);
        var aNow = throttlesNow.get(0);

        // then
        assertFalse(firstAns);
        assertTrue(subsequentAns);
        assertEquals(29999955000000000L, aNow.used());
    }

    @ParameterizedTest
    @EnumSource
    void managerBehavesAsExpectedForNftMint(ThrottleAccumulator.ThrottleType throttleType)
            throws IOException, ParseException {
        // given
        subject = new ThrottleAccumulator(
                () -> CAPACITY_SPLIT, configProvider, throttleType, throttleMetrics, gasThrottle);
        given(configProvider.getConfiguration()).willReturn(configuration);
        given(configuration.getConfigData(AccountsConfig.class)).willReturn(accountsConfig);
        given(accountsConfig.lastThrottleExempt()).willReturn(100L);
        given(configuration.getConfigData(ContractsConfig.class)).willReturn(contractsConfig);
        given(contractsConfig.throttleThrottleByGas()).willReturn(true);

        given(transactionInfo.payerID())
                .willReturn(AccountID.newBuilder().accountNum(1234L).build());

        final var defs = getThrottleDefs("bootstrap/throttles.json");

        final var numNfts = 3;
        givenMintWith(numNfts);
        given(configuration.getConfigData(TokensConfig.class)).willReturn(tokensConfig);
        given(tokensConfig.nftsMintThrottleScaleFactor()).willReturn(NFT_SCALE_FACTOR);

        // when
        subject.rebuildFor(defs);
        // and
        var firstAns = subject.shouldThrottle(transactionInfo, TIME_INSTANT, state);
        boolean subsequentAns = false;
        for (int i = 1; i <= 400; i++) {
            subsequentAns = subject.shouldThrottle(transactionInfo, TIME_INSTANT.plusNanos(i), state);
        }
        var throttlesNow = subject.activeThrottlesFor(TOKEN_MINT);
        // and
        var aNow = throttlesNow.get(0);

        // then
        assertFalse(firstAns);
        assertTrue(subsequentAns);
        assertEquals(29999994000000000L, aNow.used());
    }

    @ParameterizedTest
    @EnumSource
    void managerBehavesAsExpectedForMultiBucketOp(ThrottleAccumulator.ThrottleType throttleType)
            throws IOException, ParseException {
        // given
        subject = new ThrottleAccumulator(
                () -> CAPACITY_SPLIT, configProvider, throttleType, throttleMetrics, gasThrottle);
        given(configProvider.getConfiguration()).willReturn(configuration);
        given(configuration.getConfigData(AccountsConfig.class)).willReturn(accountsConfig);
        given(accountsConfig.lastThrottleExempt()).willReturn(100L);
        given(configuration.getConfigData(ContractsConfig.class)).willReturn(contractsConfig);
        given(contractsConfig.throttleThrottleByGas()).willReturn(false);

        given(transactionInfo.payerID())
                .willReturn(AccountID.newBuilder().accountNum(1234L).build());

        final var defs = getThrottleDefs("bootstrap/throttles.json");

        given(transactionInfo.functionality()).willReturn(CONTRACT_CALL);

        // when
        subject.rebuildFor(defs);
        // and
        var firstAns = subject.shouldThrottle(transactionInfo, TIME_INSTANT, state);
        boolean subsequentAns = false;
        for (int i = 1; i <= 12; i++) {
            subsequentAns = subject.shouldThrottle(transactionInfo, TIME_INSTANT.plusNanos(i), state);
        }
        var throttlesNow = subject.activeThrottlesFor(CONTRACT_CALL);
        // and
        var aNow = throttlesNow.get(0);
        var bNow = throttlesNow.get(1);

        // then
        assertFalse(firstAns);
        assertTrue(subsequentAns);
        assertEquals(24999999820000000L, aNow.used());
        assertEquals(9999999940000L, bNow.used());
    }

    @ParameterizedTest
    @EnumSource
    void handlesThrottleExemption(ThrottleAccumulator.ThrottleType throttleType) throws IOException, ParseException {
        // given
        subject = new ThrottleAccumulator(
                () -> CAPACITY_SPLIT, configProvider, throttleType, throttleMetrics, gasThrottle);
        given(configProvider.getConfiguration()).willReturn(configuration);
        given(configuration.getConfigData(AccountsConfig.class)).willReturn(accountsConfig);
        given(accountsConfig.lastThrottleExempt()).willReturn(100L);

        final var defs = getThrottleDefs("bootstrap/throttles.json");
        given(transactionInfo.functionality()).willReturn(CONTRACT_CALL);
        given(transactionInfo.payerID())
                .willReturn(AccountID.newBuilder().accountNum(1L).build());

        // when:
        subject.rebuildFor(defs);
        // and:
        var firstAns = subject.shouldThrottle(transactionInfo, TIME_INSTANT, state);
        for (int i = 1; i <= 12; i++) {
            assertFalse(subject.shouldThrottle(transactionInfo, TIME_INSTANT.plusNanos(i), state));
        }
        var throttlesNow = subject.activeThrottlesFor(CONTRACT_CALL);
        // and:
        var aNow = throttlesNow.get(0);
        var bNow = throttlesNow.get(1);

        // then:
        assertFalse(firstAns);
        assertEquals(0, aNow.used());
        assertEquals(0, bNow.used());
    }

    @ParameterizedTest
    @EnumSource
    void computesNumImplicitCreationsIfNotAlreadyKnown(ThrottleAccumulator.ThrottleType throttleType)
            throws IOException, ParseException {
        // given
        subject = new ThrottleAccumulator(
                () -> CAPACITY_SPLIT, configProvider, throttleType, throttleMetrics, gasThrottle);
        given(configProvider.getConfiguration()).willReturn(configuration);
        given(configuration.getConfigData(AccountsConfig.class)).willReturn(accountsConfig);
        given(accountsConfig.lastThrottleExempt()).willReturn(100L);
        given(configuration.getConfigData(ContractsConfig.class)).willReturn(contractsConfig);
        given(contractsConfig.throttleThrottleByGas()).willReturn(false);

        given(transactionInfo.payerID())
                .willReturn(AccountID.newBuilder().accountNum(1234L).build());

        final var defs = getThrottleDefs("bootstrap/throttles.json");

        given(transactionInfo.functionality()).willReturn(CRYPTO_TRANSFER);
        final var txn = TransactionBody.newBuilder()
                .cryptoTransfer(CryptoTransferTransactionBody.DEFAULT)
                .build();
        given(transactionInfo.txBody()).willReturn(txn);

        given(state.getReadableStates(any())).willReturn(readableStates);

        given(configuration.getConfigData(AutoCreationConfig.class)).willReturn(autoCreationConfig);
        given(autoCreationConfig.enabled()).willReturn(true);
        given(configuration.getConfigData(LazyCreationConfig.class)).willReturn(lazyCreationConfig);
        given(lazyCreationConfig.enabled()).willReturn(false);

        // when
        subject.rebuildFor(defs);
        var ans = subject.shouldThrottle(transactionInfo, TIME_INSTANT, state);

        // then
        assertFalse(ans);
    }

    @ParameterizedTest
    @EnumSource
    void ifLazyCreationEnabledComputesNumImplicitCreationsIfNotAlreadyKnown(
            ThrottleAccumulator.ThrottleType throttleType) throws IOException, ParseException {
        // given
        subject = new ThrottleAccumulator(
                () -> CAPACITY_SPLIT, configProvider, throttleType, throttleMetrics, gasThrottle);
        given(configProvider.getConfiguration()).willReturn(configuration);
        given(configuration.getConfigData(AccountsConfig.class)).willReturn(accountsConfig);
        given(accountsConfig.lastThrottleExempt()).willReturn(100L);
        given(configuration.getConfigData(ContractsConfig.class)).willReturn(contractsConfig);
        given(contractsConfig.throttleThrottleByGas()).willReturn(false);

        given(transactionInfo.payerID())
                .willReturn(AccountID.newBuilder().accountNum(1234L).build());

        final var defs = getThrottleDefs("bootstrap/throttles.json");

        given(transactionInfo.functionality()).willReturn(CRYPTO_TRANSFER);
        final var txn = TransactionBody.newBuilder()
                .cryptoTransfer(CryptoTransferTransactionBody.DEFAULT)
                .build();
        given(transactionInfo.txBody()).willReturn(txn);

        given(state.getReadableStates(any())).willReturn(readableStates);

        given(configuration.getConfigData(AutoCreationConfig.class)).willReturn(autoCreationConfig);
        given(autoCreationConfig.enabled()).willReturn(false);
        given(configuration.getConfigData(LazyCreationConfig.class)).willReturn(lazyCreationConfig);
        given(lazyCreationConfig.enabled()).willReturn(true);

        // when
        subject.rebuildFor(defs);
        var ans = subject.shouldThrottle(transactionInfo, TIME_INSTANT, state);

        // then
        assertFalse(ans);
    }

    @ParameterizedTest
    @EnumSource
    void cryptoTransfersWithNoAutoAccountCreationsAreThrottledAsExpected(ThrottleAccumulator.ThrottleType throttleType)
            throws IOException, ParseException {
        // given
        subject = new ThrottleAccumulator(
                () -> CAPACITY_SPLIT, configProvider, throttleType, throttleMetrics, gasThrottle);
        given(configProvider.getConfiguration()).willReturn(configuration);
        given(configuration.getConfigData(AccountsConfig.class)).willReturn(accountsConfig);
        given(accountsConfig.lastThrottleExempt()).willReturn(100L);
        given(configuration.getConfigData(ContractsConfig.class)).willReturn(contractsConfig);
        given(contractsConfig.throttleThrottleByGas()).willReturn(false);

        given(transactionInfo.payerID())
                .willReturn(AccountID.newBuilder().accountNum(1234L).build());

        final var defs = getThrottleDefs("bootstrap/throttles.json");

        given(transactionInfo.functionality()).willReturn(CRYPTO_TRANSFER);
        final int numImplicitCreations = 0;
        givenTransferWithImplicitCreations(numImplicitCreations);

        given(state.getReadableStates(any())).willReturn(readableStates);

        given(configuration.getConfigData(AutoCreationConfig.class)).willReturn(autoCreationConfig);
        given(autoCreationConfig.enabled()).willReturn(false);
        given(configuration.getConfigData(LazyCreationConfig.class)).willReturn(lazyCreationConfig);
        given(lazyCreationConfig.enabled()).willReturn(false);

        // when
        subject.rebuildFor(defs);
        var ans = subject.shouldThrottle(transactionInfo, TIME_INSTANT, state);

        // then
        assertFalse(ans);
    }

    @ParameterizedTest
    @EnumSource
    void managerAllowsCryptoTransfersWithAutoAccountCreationsAsExpected(ThrottleAccumulator.ThrottleType throttleType)
            throws IOException, ParseException {
        // given
        subject = new ThrottleAccumulator(
                () -> CAPACITY_SPLIT, configProvider, throttleType, throttleMetrics, gasThrottle);
        given(configProvider.getConfiguration()).willReturn(configuration);
        given(configuration.getConfigData(AccountsConfig.class)).willReturn(accountsConfig);
        given(accountsConfig.lastThrottleExempt()).willReturn(100L);
        given(configuration.getConfigData(ContractsConfig.class)).willReturn(contractsConfig);
        given(contractsConfig.throttleThrottleByGas()).willReturn(false);

        given(transactionInfo.payerID())
                .willReturn(AccountID.newBuilder().accountNum(1234L).build());

        final var defs = getThrottleDefs("bootstrap/throttles.json");

        given(transactionInfo.functionality()).willReturn(CRYPTO_TRANSFER);
        final int numImplicitCreations = 1;
        givenTransferWithImplicitCreations(numImplicitCreations);
        given(state.getReadableStates(any())).willReturn(readableStates);
        given(readableStates.get(any())).willReturn(aliases);

        given(configuration.getConfigData(AutoCreationConfig.class)).willReturn(autoCreationConfig);
        given(autoCreationConfig.enabled()).willReturn(true);
        given(configuration.getConfigData(LazyCreationConfig.class)).willReturn(lazyCreationConfig);
        given(lazyCreationConfig.enabled()).willReturn(false);

        // when
        subject.rebuildFor(defs);
        var ans = subject.shouldThrottle(transactionInfo, TIME_INSTANT, state);

        // then
        assertFalse(ans);
    }

    @ParameterizedTest
    @EnumSource
    void managerRejectsCryptoTransfersWithAutoAccountCreationsAsExpected(ThrottleAccumulator.ThrottleType throttleType)
            throws IOException, ParseException {
        // given
        subject = new ThrottleAccumulator(
                () -> CAPACITY_SPLIT, configProvider, throttleType, throttleMetrics, gasThrottle);
        given(configProvider.getConfiguration()).willReturn(configuration);
        given(configuration.getConfigData(AccountsConfig.class)).willReturn(accountsConfig);
        given(accountsConfig.lastThrottleExempt()).willReturn(100L);
        given(configuration.getConfigData(ContractsConfig.class)).willReturn(contractsConfig);
        given(contractsConfig.throttleThrottleByGas()).willReturn(false);

        given(transactionInfo.payerID())
                .willReturn(AccountID.newBuilder().accountNum(1234L).build());

        final var defs = getThrottleDefs("bootstrap/throttles.json");

        given(transactionInfo.functionality()).willReturn(CRYPTO_TRANSFER);
        givenTransferWithImplicitCreations(10);
        given(state.getReadableStates(any())).willReturn(readableStates);
        given(readableStates.get(any())).willReturn(aliases);

        given(configuration.getConfigData(AutoCreationConfig.class)).willReturn(autoCreationConfig);
        given(autoCreationConfig.enabled()).willReturn(true);
        given(configuration.getConfigData(LazyCreationConfig.class)).willReturn(lazyCreationConfig);
        given(lazyCreationConfig.enabled()).willReturn(false);

        // when
        subject.rebuildFor(defs);
        var ans = subject.shouldThrottle(transactionInfo, TIME_INSTANT, state);

        // then
        assertTrue(ans);
    }

    @ParameterizedTest
    @EnumSource
    void managerRejectsCryptoTransfersWithMissingCryptoCreateThrottle(ThrottleAccumulator.ThrottleType throttleType)
            throws IOException, ParseException {
        // given
        subject = new ThrottleAccumulator(
                () -> CAPACITY_SPLIT, configProvider, throttleType, throttleMetrics, gasThrottle);
        given(configProvider.getConfiguration()).willReturn(configuration);
        given(configuration.getConfigData(AccountsConfig.class)).willReturn(accountsConfig);
        given(accountsConfig.lastThrottleExempt()).willReturn(100L);
        given(configuration.getConfigData(ContractsConfig.class)).willReturn(contractsConfig);
        given(contractsConfig.throttleThrottleByGas()).willReturn(false);

        given(transactionInfo.payerID())
                .willReturn(AccountID.newBuilder().accountNum(1234L).build());

        final var defs = getThrottleDefs("bootstrap/throttles-sans-creation.json");

        given(transactionInfo.functionality()).willReturn(CRYPTO_TRANSFER);
        givenTransferWithImplicitCreations(1);
        given(state.getReadableStates(any())).willReturn(readableStates);
        given(readableStates.get(any())).willReturn(aliases);

        given(configuration.getConfigData(AutoCreationConfig.class)).willReturn(autoCreationConfig);
        given(autoCreationConfig.enabled()).willReturn(true);
        given(configuration.getConfigData(LazyCreationConfig.class)).willReturn(lazyCreationConfig);
        given(lazyCreationConfig.enabled()).willReturn(false);

        // when
        subject.rebuildFor(defs);
        var ans = subject.shouldThrottle(transactionInfo, TIME_INSTANT, state);

        // then
        assertTrue(ans);
    }

    @ParameterizedTest
    @EnumSource
    void ethereumTransactionWithNoAutoAccountCreationsAreThrottledAsExpected(
            ThrottleAccumulator.ThrottleType throttleType) throws IOException, ParseException {
        // given
        subject = new ThrottleAccumulator(
                () -> CAPACITY_SPLIT, configProvider, throttleType, throttleMetrics, gasThrottle);
        given(configProvider.getConfiguration()).willReturn(configuration);
        given(configuration.getConfigData(AccountsConfig.class)).willReturn(accountsConfig);
        given(accountsConfig.lastThrottleExempt()).willReturn(100L);
        given(configuration.getConfigData(ContractsConfig.class)).willReturn(contractsConfig);
        given(contractsConfig.throttleThrottleByGas()).willReturn(false);

        given(transactionInfo.payerID())
                .willReturn(AccountID.newBuilder().accountNum(1234L).build());

        final var defs = getThrottleDefs("bootstrap/throttles.json");

        given(transactionInfo.functionality()).willReturn(ETHEREUM_TRANSACTION);
        final var ethTxnBody =
                EthereumTransactionBody.newBuilder().ethereumData(Bytes.EMPTY).build();
        given(transactionInfo.txBody())
                .willReturn(TransactionBody.newBuilder()
                        .ethereumTransaction(ethTxnBody)
                        .build());

        given(state.getReadableStates(any())).willReturn(readableStates);
        given(readableStates.get(any())).willReturn(aliases);

        given(configuration.getConfigData(AutoCreationConfig.class)).willReturn(autoCreationConfig);
        given(autoCreationConfig.enabled()).willReturn(false);
        given(configuration.getConfigData(LazyCreationConfig.class)).willReturn(lazyCreationConfig);
        given(lazyCreationConfig.enabled()).willReturn(false);

        // when
        subject.rebuildFor(defs);
        var ans = subject.shouldThrottle(transactionInfo, TIME_INSTANT, state);

        // then
        assertFalse(ans);
    }

    @ParameterizedTest
    @EnumSource
    void ethereumTransactionWithAutoAccountCreationsButNoLazyCreationsAreThrottledAsExpected(
            ThrottleAccumulator.ThrottleType throttleType) throws IOException, ParseException {
        // given
        subject = new ThrottleAccumulator(
                () -> CAPACITY_SPLIT, configProvider, throttleType, throttleMetrics, gasThrottle);
        given(configProvider.getConfiguration()).willReturn(configuration);
        given(configuration.getConfigData(AccountsConfig.class)).willReturn(accountsConfig);
        given(accountsConfig.lastThrottleExempt()).willReturn(100L);
        given(configuration.getConfigData(ContractsConfig.class)).willReturn(contractsConfig);
        given(contractsConfig.throttleThrottleByGas()).willReturn(false);

        given(transactionInfo.payerID())
                .willReturn(AccountID.newBuilder().accountNum(1234L).build());

        final var defs = getThrottleDefs("bootstrap/throttles.json");

        given(transactionInfo.functionality()).willReturn(ETHEREUM_TRANSACTION);
        final var ethTxnBody =
                EthereumTransactionBody.newBuilder().ethereumData(Bytes.EMPTY).build();
        given(transactionInfo.txBody())
                .willReturn(TransactionBody.newBuilder()
                        .ethereumTransaction(ethTxnBody)
                        .build());

        given(state.getReadableStates(any())).willReturn(readableStates);
        given(readableStates.get(any())).willReturn(aliases);

        given(configuration.getConfigData(AutoCreationConfig.class)).willReturn(autoCreationConfig);
        given(autoCreationConfig.enabled()).willReturn(true);
        given(configuration.getConfigData(LazyCreationConfig.class)).willReturn(lazyCreationConfig);
        given(lazyCreationConfig.enabled()).willReturn(false);

        // when
        subject.rebuildFor(defs);
        var ans = subject.shouldThrottle(transactionInfo, TIME_INSTANT, state);

        // then
        assertFalse(ans);
    }

    @ParameterizedTest
    @EnumSource
    void managerAllowsEthereumTransactionWithAutoAccountCreationsAsExpected(
            ThrottleAccumulator.ThrottleType throttleType) throws IOException, ParseException {
        // given
        subject = new ThrottleAccumulator(
                () -> CAPACITY_SPLIT, configProvider, throttleType, throttleMetrics, gasThrottle);
        given(configProvider.getConfiguration()).willReturn(configuration);
        given(configuration.getConfigData(AccountsConfig.class)).willReturn(accountsConfig);
        given(accountsConfig.lastThrottleExempt()).willReturn(100L);
        given(configuration.getConfigData(ContractsConfig.class)).willReturn(contractsConfig);
        given(contractsConfig.throttleThrottleByGas()).willReturn(false);

        given(transactionInfo.payerID())
                .willReturn(AccountID.newBuilder().accountNum(1234L).build());

        final var defs = getThrottleDefs("bootstrap/throttles.json");

        given(transactionInfo.functionality()).willReturn(ETHEREUM_TRANSACTION);
        final var ethTxnBody =
                EthereumTransactionBody.newBuilder().ethereumData(Bytes.EMPTY).build();
        given(transactionInfo.txBody())
                .willReturn(TransactionBody.newBuilder()
                        .ethereumTransaction(ethTxnBody)
                        .build());

        given(state.getReadableStates(any())).willReturn(readableStates);
        given(readableStates.get(any())).willReturn(aliases);

        given(configuration.getConfigData(AutoCreationConfig.class)).willReturn(autoCreationConfig);
        given(autoCreationConfig.enabled()).willReturn(true);
        given(configuration.getConfigData(LazyCreationConfig.class)).willReturn(lazyCreationConfig);
        given(lazyCreationConfig.enabled()).willReturn(true);

        // when
        subject.rebuildFor(defs);
        var ans = subject.shouldThrottle(transactionInfo, TIME_INSTANT, state);

        // then
        assertFalse(ans);
    }

    @ParameterizedTest
    @EnumSource
    void managerRejectsEthereumTransactionWithMissingCryptoCreateThrottle(ThrottleAccumulator.ThrottleType throttleType)
            throws IOException, ParseException {
        // given
        subject = new ThrottleAccumulator(
                () -> CAPACITY_SPLIT, configProvider, throttleType, throttleMetrics, gasThrottle);
        given(configProvider.getConfiguration()).willReturn(configuration);
        given(configuration.getConfigData(AccountsConfig.class)).willReturn(accountsConfig);
        given(accountsConfig.lastThrottleExempt()).willReturn(100L);
        given(configuration.getConfigData(ContractsConfig.class)).willReturn(contractsConfig);
        given(contractsConfig.throttleThrottleByGas()).willReturn(false);

        given(transactionInfo.payerID())
                .willReturn(AccountID.newBuilder().accountNum(1234L).build());

        final var defs = getThrottleDefs("bootstrap/throttles-sans-creation.json");

        given(transactionInfo.functionality()).willReturn(ETHEREUM_TRANSACTION);
        final var ethTxnBody = EthereumTransactionBody.newBuilder()
                .ethereumData(ETH_LAZY_CREATE)
                .build();
        given(transactionInfo.txBody())
                .willReturn(TransactionBody.newBuilder()
                        .ethereumTransaction(ethTxnBody)
                        .build());

        given(state.getReadableStates(any())).willReturn(readableStates);
        given(readableStates.get(any())).willReturn(aliases);

        given(configuration.getConfigData(AutoCreationConfig.class)).willReturn(autoCreationConfig);
        given(autoCreationConfig.enabled()).willReturn(true);
        given(configuration.getConfigData(LazyCreationConfig.class)).willReturn(lazyCreationConfig);
        given(lazyCreationConfig.enabled()).willReturn(true);

        // when
        subject.rebuildFor(defs);
        var ans = subject.shouldThrottle(transactionInfo, TIME_INSTANT, state);

        // then
        assertTrue(ans);
    }

    @ParameterizedTest
    @EnumSource
    void alwaysThrottlesContractCallWhenGasThrottleIsNotDefined(ThrottleAccumulator.ThrottleType throttleType) {
        // given
        subject = new ThrottleAccumulator(() -> CAPACITY_SPLIT, configProvider, throttleType, throttleMetrics);
        given(configProvider.getConfiguration()).willReturn(configuration);
        given(configuration.getConfigData(AccountsConfig.class)).willReturn(accountsConfig);
        given(accountsConfig.lastThrottleExempt()).willReturn(100L);
        given(configuration.getConfigData(ContractsConfig.class)).willReturn(contractsConfig);
        given(contractsConfig.throttleThrottleByGas()).willReturn(true);
        given(contractsConfig.maxGasPerSec()).willReturn(0L);

        given(transactionInfo.payerID())
                .willReturn(AccountID.newBuilder().accountNum(1234L).build());

        given(transactionInfo.functionality()).willReturn(CONTRACT_CALL);
        given(transactionInfo.txBody())
                .willReturn(TransactionBody.newBuilder()
                        .contractCall(ContractCallTransactionBody.DEFAULT)
                        .build());

        // when
        subject.applyGasConfig();

        // then
        assertTrue(subject.shouldThrottle(transactionInfo, TIME_INSTANT, state));
    }

    @ParameterizedTest
    @EnumSource
    void alwaysThrottlesContractCallWhenGasThrottleReturnsTrue(ThrottleAccumulator.ThrottleType throttleType) {
        // given
        subject = new ThrottleAccumulator(() -> CAPACITY_SPLIT, configProvider, throttleType, throttleMetrics);
        given(configProvider.getConfiguration()).willReturn(configuration);
        given(configuration.getConfigData(AccountsConfig.class)).willReturn(accountsConfig);
        given(accountsConfig.lastThrottleExempt()).willReturn(100L);
        given(configuration.getConfigData(ContractsConfig.class)).willReturn(contractsConfig);
        given(contractsConfig.throttleThrottleByGas()).willReturn(true);
        given(contractsConfig.maxGasPerSec()).willReturn(1L);

        given(transactionInfo.payerID())
                .willReturn(AccountID.newBuilder().accountNum(1234L).build());

        given(transactionInfo.functionality()).willReturn(CONTRACT_CALL);
        final var contractCallTxnBody =
                ContractCallTransactionBody.newBuilder().gas(2L).build();
        given(transactionInfo.txBody())
                .willReturn(TransactionBody.newBuilder()
                        .contractCall(contractCallTxnBody)
                        .build());

        // when
        subject.applyGasConfig();

        // then
        assertTrue(subject.shouldThrottle(transactionInfo, TIME_INSTANT, state));
    }

    @ParameterizedTest
    @EnumSource
    void alwaysThrottlesContractCreateWhenGasThrottleIsNotDefined(ThrottleAccumulator.ThrottleType throttleType) {
        // given
        subject = new ThrottleAccumulator(() -> CAPACITY_SPLIT, configProvider, throttleType, throttleMetrics);
        given(configProvider.getConfiguration()).willReturn(configuration);
        given(configuration.getConfigData(AccountsConfig.class)).willReturn(accountsConfig);
        given(accountsConfig.lastThrottleExempt()).willReturn(100L);
        given(configuration.getConfigData(ContractsConfig.class)).willReturn(contractsConfig);
        given(contractsConfig.throttleThrottleByGas()).willReturn(true);
        given(contractsConfig.maxGasPerSec()).willReturn(0L);

        given(transactionInfo.payerID())
                .willReturn(AccountID.newBuilder().accountNum(1234L).build());

        given(transactionInfo.functionality()).willReturn(CONTRACT_CREATE);
        given(transactionInfo.txBody())
                .willReturn(TransactionBody.newBuilder()
                        .contractCreateInstance(ContractCreateTransactionBody.DEFAULT)
                        .build());

        // when
        subject.applyGasConfig();

        // then
        assertTrue(subject.shouldThrottle(transactionInfo, TIME_INSTANT, state));
    }

    @ParameterizedTest
    @EnumSource
    void alwaysThrottlesContractCreateWhenGasThrottleReturnsTrue(ThrottleAccumulator.ThrottleType throttleType) {
        // given
        subject = new ThrottleAccumulator(() -> CAPACITY_SPLIT, configProvider, throttleType, throttleMetrics);
        given(configProvider.getConfiguration()).willReturn(configuration);
        given(configuration.getConfigData(AccountsConfig.class)).willReturn(accountsConfig);
        given(accountsConfig.lastThrottleExempt()).willReturn(100L);
        given(configuration.getConfigData(ContractsConfig.class)).willReturn(contractsConfig);
        given(contractsConfig.throttleThrottleByGas()).willReturn(true);
        given(contractsConfig.maxGasPerSec()).willReturn(1L);

        given(transactionInfo.payerID())
                .willReturn(AccountID.newBuilder().accountNum(1234L).build());

        given(transactionInfo.functionality()).willReturn(CONTRACT_CREATE);
        final var contractCreateTxnBody =
                ContractCreateTransactionBody.newBuilder().gas(2L).build();
        given(transactionInfo.txBody())
                .willReturn(TransactionBody.newBuilder()
                        .contractCreateInstance(contractCreateTxnBody)
                        .build());

        // when
        subject.applyGasConfig();

        // then
        assertTrue(subject.shouldThrottle(transactionInfo, TIME_INSTANT, state));
        assertTrue(subject.wasLastTxnGasThrottled());

        given(transactionInfo.functionality()).willReturn(TOKEN_BURN);
        subject.shouldThrottle(transactionInfo, TIME_INSTANT.plusSeconds(1), state);
        assertFalse(subject.wasLastTxnGasThrottled());
    }

    @ParameterizedTest
    @EnumSource
    void alwaysThrottlesEthereumTxnWhenGasThrottleIsNotDefined(ThrottleAccumulator.ThrottleType throttleType) {
        // given
        subject = new ThrottleAccumulator(() -> CAPACITY_SPLIT, configProvider, throttleType, throttleMetrics);
        given(configProvider.getConfiguration()).willReturn(configuration);
        given(configuration.getConfigData(AccountsConfig.class)).willReturn(accountsConfig);
        given(accountsConfig.lastThrottleExempt()).willReturn(100L);
        given(configuration.getConfigData(ContractsConfig.class)).willReturn(contractsConfig);
        given(contractsConfig.throttleThrottleByGas()).willReturn(true);
        given(contractsConfig.maxGasPerSec()).willReturn(0L);

        given(transactionInfo.payerID())
                .willReturn(AccountID.newBuilder().accountNum(1234L).build());

        given(transactionInfo.functionality()).willReturn(ETHEREUM_TRANSACTION);
        given(transactionInfo.txBody())
                .willReturn(TransactionBody.newBuilder()
                        .ethereumTransaction(EthereumTransactionBody.DEFAULT)
                        .build());

        // when
        subject.applyGasConfig();

        // then
        assertTrue(subject.shouldThrottle(transactionInfo, TIME_INSTANT, state));
    }

    @ParameterizedTest
    @EnumSource
    void alwaysThrottlesEthereumTxnWhenGasThrottleReturnsTrue(ThrottleAccumulator.ThrottleType throttleType) {
        // given
        subject = new ThrottleAccumulator(() -> CAPACITY_SPLIT, configProvider, throttleType, throttleMetrics);
        given(configProvider.getConfiguration()).willReturn(configuration);
        given(configuration.getConfigData(AccountsConfig.class)).willReturn(accountsConfig);
        given(accountsConfig.lastThrottleExempt()).willReturn(100L);
        given(configuration.getConfigData(ContractsConfig.class)).willReturn(contractsConfig);
        given(contractsConfig.throttleThrottleByGas()).willReturn(true);
        given(contractsConfig.maxGasPerSec()).willReturn(1L);

        given(transactionInfo.payerID())
                .willReturn(AccountID.newBuilder().accountNum(1234L).build());

        given(transactionInfo.functionality()).willReturn(ETHEREUM_TRANSACTION);
        final var ethTxnBody = EthereumTransactionBody.newBuilder()
                .ethereumData(ETH_LAZY_CREATE)
                .build();
        given(transactionInfo.txBody())
                .willReturn(TransactionBody.newBuilder()
                        .ethereumTransaction(ethTxnBody)
                        .build());

        // when
        subject.applyGasConfig();

        // then
        assertTrue(subject.shouldThrottle(transactionInfo, TIME_INSTANT, state));
        assertTrue(subject.wasLastTxnGasThrottled());

        given(transactionInfo.functionality()).willReturn(TOKEN_BURN);
        subject.shouldThrottle(transactionInfo, TIME_INSTANT.plusSeconds(1), state);
        assertFalse(subject.wasLastTxnGasThrottled());
    }

    @ParameterizedTest
    @EnumSource
    void gasLimitThrottleReturnsCorrectObject(ThrottleAccumulator.ThrottleType throttleType) {
        // given
        subject = new ThrottleAccumulator(() -> CAPACITY_SPLIT, configProvider, throttleType, throttleMetrics);
        given(configProvider.getConfiguration()).willReturn(configuration);
        given(configuration.getConfigData(ContractsConfig.class)).willReturn(contractsConfig);
        given(contractsConfig.throttleThrottleByGas()).willReturn(true);
        var capacity = 10L;
        given(contractsConfig.maxGasPerSec()).willReturn(capacity);

        // when
        subject.applyGasConfig();

        // then
        assertEquals(capacity, subject.gasLimitThrottle().capacity());
        verify(throttleMetrics).setupGasThrottleMetric(subject.gasLimitThrottle(), configuration);
    }

    @ParameterizedTest
    @EnumSource
    void constructsExpectedBucketsFromTestResource(ThrottleAccumulator.ThrottleType throttleType)
            throws IOException, ParseException {
        // given
        subject = new ThrottleAccumulator(() -> CAPACITY_SPLIT, configProvider, throttleType, throttleMetrics);
        given(configProvider.getConfiguration()).willReturn(configuration);
        final var defs = getThrottleDefs("bootstrap/throttles.json");

        // and
        var expected = List.of(
                DeterministicThrottle.withMtpsAndBurstPeriod(15_000_000, 2),
                DeterministicThrottle.withMtpsAndBurstPeriod(5_000, 2),
                DeterministicThrottle.withMtpsAndBurstPeriod(50_000, 3),
                DeterministicThrottle.withMtpsAndBurstPeriod(5000, 4));

        // when
        subject.rebuildFor(defs);
        // and
        var rebuilt = subject.allActiveThrottles();

        // then
        assertEquals(expected, rebuilt);
        verify(throttleMetrics).setupThrottleMetrics(rebuilt, configuration);
    }

    @ParameterizedTest
    @EnumSource
    void alwaysRejectsIfNoThrottle(ThrottleAccumulator.ThrottleType throttleType) {
        subject = new ThrottleAccumulator(
                () -> CAPACITY_SPLIT, configProvider, throttleType, throttleMetrics, gasThrottle);
        given(configProvider.getConfiguration()).willReturn(configuration);
        given(configuration.getConfigData(AccountsConfig.class)).willReturn(accountsConfig);
        given(accountsConfig.lastThrottleExempt()).willReturn(100L);
        given(configuration.getConfigData(ContractsConfig.class)).willReturn(contractsConfig);
        given(contractsConfig.throttleThrottleByGas()).willReturn(false);
        given(transactionInfo.payerID())
                .willReturn(AccountID.newBuilder().accountNum(1234L).build());

        given(transactionInfo.functionality()).willReturn(CONTRACT_CALL);

        assertTrue(subject.shouldThrottle(transactionInfo, TIME_INSTANT, state));
        Assertions.assertSame(Collections.emptyList(), subject.activeThrottlesFor(CONTRACT_CALL));
    }

    @ParameterizedTest
    @EnumSource
    void verifyLeakUnusedGas(ThrottleAccumulator.ThrottleType throttleType) throws IOException, ParseException {
        subject = new ThrottleAccumulator(() -> CAPACITY_SPLIT, configProvider, throttleType, throttleMetrics);
        given(configProvider.getConfiguration()).willReturn(configuration);
        given(configuration.getConfigData(AccountsConfig.class)).willReturn(accountsConfig);
        given(accountsConfig.lastThrottleExempt()).willReturn(100L);
        given(configuration.getConfigData(ContractsConfig.class)).willReturn(contractsConfig);
        given(contractsConfig.throttleThrottleByGas()).willReturn(true);
        given(contractsConfig.maxGasPerSec()).willReturn(150L);

        // payer is not exempt
        given(transactionInfo.payerID())
                .willReturn(AccountID.newBuilder().accountNum(1234L).build());

        subject.applyGasConfig();

        assertTrue(subject.gasLimitThrottle().allow(TIME_INSTANT, 100));
        assertFalse(subject.gasLimitThrottle().allow(TIME_INSTANT, 100));

        subject.leakUnusedGasPreviouslyReserved(transactionInfo, 100L);

        assertTrue(subject.gasLimitThrottle().allow(TIME_INSTANT, 100));
        assertFalse(subject.gasLimitThrottle().allow(TIME_INSTANT, 100));

        // payer is exempt
        given(transactionInfo.payerID())
                .willReturn(AccountID.newBuilder().accountNum(1L).build());

        subject.leakUnusedGasPreviouslyReserved(transactionInfo, 100L);

        assertFalse(subject.gasLimitThrottle().allow(TIME_INSTANT, 100));
        assertFalse(subject.gasLimitThrottle().allow(TIME_INSTANT, 100));
    }

    @Test
    void alwaysThrottleNOfUnmanaged() throws IOException, ParseException {
        subject = new ThrottleAccumulator(
                () -> CAPACITY_SPLIT, configProvider, FRONTEND_THROTTLE, throttleMetrics, gasThrottle);
        final var defs = getThrottleDefs("bootstrap/throttles.json");

        subject.rebuildFor(defs);

        assertTrue(subject.shouldThrottleNOfUnscaled(2, TOKEN_BURN, TIME_INSTANT));
    }

    @Test
    void canThrottleNOfManaged() throws IOException, ParseException {
        subject = new ThrottleAccumulator(
                () -> CAPACITY_SPLIT, configProvider, FRONTEND_THROTTLE, throttleMetrics, gasThrottle);
        final var defs = getThrottleDefs("bootstrap/throttles.json");

        subject.rebuildFor(defs);

        assertFalse(subject.shouldThrottleNOfUnscaled(1, TOKEN_MINT, TIME_INSTANT));
        final var oneUsed = subject.activeThrottlesFor(TOKEN_MINT).get(0).used();
        assertFalse(subject.shouldThrottleNOfUnscaled(41, TOKEN_MINT, TIME_INSTANT));
        final var fortyTwoUsed = subject.activeThrottlesFor(TOKEN_MINT).get(0).used();
        assertEquals(42 * oneUsed, fortyTwoUsed);
    }

    @Test
    void whenThrottlesUsesNoCapacity() throws IOException, ParseException {
        subject = new ThrottleAccumulator(
                () -> CAPACITY_SPLIT, configProvider, FRONTEND_THROTTLE, throttleMetrics, gasThrottle);
        final var defs = getThrottleDefs("bootstrap/throttles.json");

        subject.rebuildFor(defs);

        assertTrue(subject.shouldThrottleNOfUnscaled(11, CONTRACT_CALL, TIME_INSTANT));
        final var used = subject.activeThrottlesFor(CONTRACT_CALL).get(0).used();
        assertEquals(0, used);
    }

    @Test
    void canLeakCapacityForNOfManaged() throws IOException, ParseException {
        subject = new ThrottleAccumulator(
                () -> CAPACITY_SPLIT, configProvider, FRONTEND_THROTTLE, throttleMetrics, gasThrottle);
        final var defs = getThrottleDefs("bootstrap/throttles.json");

        subject.rebuildFor(defs);

        subject.shouldThrottleNOfUnscaled(1, TOKEN_MINT, TIME_INSTANT);
        final var oneUsed = subject.activeThrottlesFor(TOKEN_MINT).get(0).used();
        subject.shouldThrottleNOfUnscaled(43, TOKEN_MINT, TIME_INSTANT);
        subject.leakCapacityForNOfUnscaled(2, TOKEN_MINT);
        final var fortyTwoUsed = subject.activeThrottlesFor(TOKEN_MINT).get(0).used();
        assertEquals(42 * oneUsed, fortyTwoUsed);
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
    void usesScheduleCreateThrottleForSubmitMessage(
            final ThrottleAccumulator.ThrottleType throttleType,
            final boolean longTermEnabled,
            final boolean waitForExpiry)
            throws IOException, ParseException {
        // given
        subject = new ThrottleAccumulator(
                () -> CAPACITY_SPLIT, configProvider, throttleType, throttleMetrics, gasThrottle);

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
        final boolean firstAns = subject.shouldThrottle(txnInfo, TIME_INSTANT, state);
        boolean subsequentAns = false;
        for (int i = 1; i <= 150; i++) {
            subsequentAns = subject.shouldThrottle(txnInfo, TIME_INSTANT.plusNanos(i), state);
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
            throws IOException, ParseException {
        // given
        subject = new ThrottleAccumulator(
                () -> CAPACITY_SPLIT, configProvider, throttleType, throttleMetrics, gasThrottle);

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
        final boolean firstAns = subject.shouldThrottle(txnInfo, TIME_INSTANT, state);
        boolean subsequentAns = false;
        for (int i = 1; i <= 150; i++) {
            subsequentAns = subject.shouldThrottle(txnInfo, TIME_INSTANT.plusNanos(i), state);
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
            throws IOException, ParseException {
        // given
        subject = new ThrottleAccumulator(
                () -> CAPACITY_SPLIT, configProvider, throttleType, throttleMetrics, gasThrottle);

        given(configProvider.getConfiguration()).willReturn(configuration);
        given(configuration.getConfigData(AccountsConfig.class)).willReturn(accountsConfig);
        given(accountsConfig.lastThrottleExempt()).willReturn(100L);
        given(configuration.getConfigData(ContractsConfig.class)).willReturn(contractsConfig);
        given(contractsConfig.throttleThrottleByGas()).willReturn(false);

        final var defs = getThrottleDefs("bootstrap/schedule-create-throttles.json");
        subject.rebuildFor(defs);

        // when
        final var txnInfo = scheduleCreate(SchedulableTransactionBody.DEFAULT, false, null);
        final boolean firstAns = subject.shouldThrottle(txnInfo, TIME_INSTANT, state);
        for (int i = 1; i <= 150; i++) {
            assertTrue(subject.shouldThrottle(txnInfo, TIME_INSTANT.plusNanos(i), state));
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
            final ThrottleAccumulator.ThrottleType throttleType, final boolean longTermEnabled)
            throws IOException, ParseException {
        // given
        subject = new ThrottleAccumulator(
                () -> CAPACITY_SPLIT, configProvider, throttleType, throttleMetrics, gasThrottle);

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

        given(state.getReadableStates(any())).willReturn(readableStates);

        final var scheduledTransferNoAliases = SchedulableTransactionBody.newBuilder()
                .cryptoTransfer(cryptoTransferWithImplicitCreations(0))
                .build();
        final var defs = getThrottleDefs("bootstrap/schedule-create-throttles.json");
        subject.rebuildFor(defs);

        // when
        final var txnInfo = scheduleCreate(scheduledTransferNoAliases, false, null);
        final boolean ans = subject.shouldThrottle(txnInfo, TIME_INSTANT, state);
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
            final ThrottleAccumulator.ThrottleType throttleType, final boolean longTermEnabled)
            throws IOException, ParseException {
        // given
        subject = new ThrottleAccumulator(
                () -> CAPACITY_SPLIT, configProvider, throttleType, throttleMetrics, gasThrottle);

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

        given(state.getReadableStates(any())).willReturn(readableStates);
        given(readableStates.get(ALIASES_KEY)).willReturn(aliases);
        ;

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
        final boolean ans = subject.shouldThrottle(txnInfo, TIME_INSTANT, state);
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
            throws IOException, ParseException {
        // given
        subject = new ThrottleAccumulator(
                () -> CAPACITY_SPLIT, configProvider, throttleType, throttleMetrics, gasThrottle);

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

        given(state.getReadableStates(any())).willReturn(readableStates);

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
        final boolean ans = subject.shouldThrottle(txnInfo, TIME_INSTANT, state);
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
            throws IOException, ParseException {
        // given
        subject = new ThrottleAccumulator(
                () -> CAPACITY_SPLIT, configProvider, throttleType, throttleMetrics, gasThrottle);

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
        final boolean ans = subject.shouldThrottle(txnInfo, TIME_INSTANT, state);
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
            final ThrottleAccumulator.ThrottleType throttleType, final boolean longTermEnabled)
            throws IOException, ParseException {
        // given
        subject = new ThrottleAccumulator(
                () -> CAPACITY_SPLIT, configProvider, throttleType, throttleMetrics, gasThrottle);

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

        given(state.getReadableStates(any())).willReturn(readableStates);
        given(readableStates.get(ALIASES_KEY)).willReturn(aliases);

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
        final boolean ans = subject.shouldThrottle(txnInfo, TIME_INSTANT, state);
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
            final ThrottleAccumulator.ThrottleType throttleType, final boolean longTermEnabled)
            throws IOException, ParseException {
        // given
        subject = new ThrottleAccumulator(
                () -> CAPACITY_SPLIT, configProvider, throttleType, throttleMetrics, gasThrottle);

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

        given(state.getReadableStates(any())).willReturn(readableStates);
        given(readableStates.get(ALIASES_KEY)).willReturn(aliases);

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
        final boolean ans = subject.shouldThrottle(txnInfo, TIME_INSTANT, state);
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
    void reclaimsAllUsagesOnThrottledShouldThrottleTxn() throws IOException, ParseException {
        // given
        subject = new ThrottleAccumulator(
                () -> CAPACITY_SPLIT, configProvider, FRONTEND_THROTTLE, throttleMetrics, gasThrottle);

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
        final boolean firstAns = subject.shouldThrottle(txnInfo, TIME_INSTANT, state);
        boolean subsequentAns = false;
        for (int i = 1; i <= 150; i++) {
            subsequentAns = subject.shouldThrottle(txnInfo, TIME_INSTANT.plusNanos(i), state);
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
            throws IOException, ParseException {
        // given
        subject = new ThrottleAccumulator(
                () -> CAPACITY_SPLIT, configProvider, throttleType, throttleMetrics, gasThrottle);

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

            given(state.getReadableStates(any())).willReturn(readableStates);
            given(readableStates.get(any())).willReturn(schedules);
            given(schedules.get(SCHEDULE_ID)).willReturn(schedule);
        }

        final var defs = getThrottleDefs("bootstrap/schedule-create-throttles.json");
        subject.rebuildFor(defs);

        // when
        final var txnInfo = scheduleSign(SCHEDULE_ID);
        final boolean firstAns = subject.shouldThrottle(txnInfo, TIME_INSTANT, state);
        boolean subsequentAns = false;
        for (int i = 1; i <= 150; i++) {
            subsequentAns = subject.shouldThrottle(txnInfo, TIME_INSTANT.plusNanos(i), state);
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
            throws IOException, ParseException {
        // given
        subject = new ThrottleAccumulator(
                () -> CAPACITY_SPLIT, configProvider, throttleType, throttleMetrics, gasThrottle);

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

            given(state.getReadableStates(any())).willReturn(readableStates);
            given(readableStates.get(any())).willReturn(schedules);
            given(schedules.get(SCHEDULE_ID)).willReturn(schedule);
        }

        final var defs = getThrottleDefs("bootstrap/schedule-create-throttles.json");
        subject.rebuildFor(defs);

        // when
        final var txnInfo = scheduleSign(SCHEDULE_ID);
        final boolean firstAns = subject.shouldThrottle(txnInfo, TIME_INSTANT, state);
        boolean subsequentAns = false;
        for (int i = 1; i <= 150; i++) {
            subsequentAns = subject.shouldThrottle(txnInfo, TIME_INSTANT.plusNanos(i), state);
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
    void scheduleSignAlwaysThrottledWhenNoBody() throws IOException, ParseException {
        // given
        subject = new ThrottleAccumulator(
                () -> CAPACITY_SPLIT, configProvider, FRONTEND_THROTTLE, throttleMetrics, gasThrottle);

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

        given(state.getReadableStates(any())).willReturn(readableStates);
        given(readableStates.get(any())).willReturn(schedules);
        given(schedules.get(SCHEDULE_ID)).willReturn(schedule);

        // when
        final var scheduleSignTxnInfo = scheduleSign(SCHEDULE_ID);
        final var firstAns = subject.shouldThrottle(scheduleSignTxnInfo, TIME_INSTANT, state);
        for (int i = 1; i <= 150; i++) {
            assertTrue(subject.shouldThrottle(scheduleSignTxnInfo, TIME_INSTANT.plusNanos(i), state));
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
    void scheduleSignAlwaysThrottledWhenNotExisting() throws IOException, ParseException {
        // given
        subject = new ThrottleAccumulator(
                () -> CAPACITY_SPLIT, configProvider, FRONTEND_THROTTLE, throttleMetrics, gasThrottle);

        given(configProvider.getConfiguration()).willReturn(configuration);
        given(configuration.getConfigData(AccountsConfig.class)).willReturn(accountsConfig);
        given(accountsConfig.lastThrottleExempt()).willReturn(100L);
        given(configuration.getConfigData(ContractsConfig.class)).willReturn(contractsConfig);
        given(contractsConfig.throttleThrottleByGas()).willReturn(false);
        given(configuration.getConfigData(SchedulingConfig.class)).willReturn(schedulingConfig);
        given(schedulingConfig.longTermEnabled()).willReturn(true);

        final var defs = getThrottleDefs("bootstrap/schedule-create-throttles.json");
        subject.rebuildFor(defs);

        given(state.getReadableStates(any())).willReturn(readableStates);
        given(readableStates.get(any())).willReturn(schedules);

        // when
        final var scheduleSignTxnInfo = scheduleSign(SCHEDULE_ID);
        final var firstAns = subject.shouldThrottle(scheduleSignTxnInfo, TIME_INSTANT, state);
        for (int i = 1; i <= 150; i++) {
            assertTrue(subject.shouldThrottle(scheduleSignTxnInfo, TIME_INSTANT.plusNanos(i), state));
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
            final ThrottleAccumulator.ThrottleType throttleType, final boolean longTermEnabled)
            throws IOException, ParseException {

        // given
        subject = new ThrottleAccumulator(
                () -> CAPACITY_SPLIT, configProvider, throttleType, throttleMetrics, gasThrottle);

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

        given(state.getReadableStates(any())).willReturn(readableStates);
        given(readableStates.get(ALIASES_KEY)).willReturn(aliases);

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
        final var ans = subject.shouldThrottle(scheduleSignTxnInfo, TIME_INSTANT, state);
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

    @Test
    void updateMetrics() {
        // given
        subject = new ThrottleAccumulator(() -> CAPACITY_SPLIT, configProvider, FRONTEND_THROTTLE, throttleMetrics);

        // when
        subject.updateAllMetrics();

        // then
        verify(throttleMetrics).updateAllMetrics();
    }

    @NotNull
    private static Bytes keyToBytes(Key key) throws IOException, ParseException {
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

    private ThrottleDefinitions getThrottleDefs(String testResource) throws IOException, ParseException {
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

    private void givenMintWith(int numNfts) {
        final List<ByteString> meta = new ArrayList<>();
        final var op = TokenMintTransactionBody.newBuilder();
        if (numNfts == 0) {
            op.amount(1_234_567L);
        } else {
            final var metadata = new ArrayList<Bytes>();
            while (numNfts-- > 0) {
                metadata.add(Bytes.wrap("metadata" + numNfts));
            }

            op.metadata(metadata);
        }
        final var txn = TransactionBody.newBuilder().tokenMint(op).build();

        given(transactionInfo.functionality()).willReturn(TOKEN_MINT);
        given(transactionInfo.txBody()).willReturn(txn);
    }

    private void givenTransferWithImplicitCreations(int numImplicitCreations) {
        var accountAmounts = new ArrayList<AccountAmount>();
        for (int i = 1; i <= numImplicitCreations; i++) {
            accountAmounts.add(AccountAmount.newBuilder()
                    .accountID(AccountID.newBuilder()
                            .alias(Bytes.wrap("abcdeabcdeabcdeabcde"))
                            .build())
                    .amount(i)
                    .build());
        }

        final var cryptoTransferBody = CryptoTransferTransactionBody.newBuilder()
                .transfers(
                        TransferList.newBuilder().accountAmounts(accountAmounts).build())
                .build();

        final var txn =
                TransactionBody.newBuilder().cryptoTransfer(cryptoTransferBody).build();
        given(transactionInfo.txBody()).willReturn(txn);
    }
}
