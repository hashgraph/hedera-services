/*
 * Copyright (C) 2020-2023 Hedera Hashgraph, LLC
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

import static com.hedera.hapi.node.base.HederaFunctionality.CONTRACT_CALL;
import static com.hedera.hapi.node.base.HederaFunctionality.CONTRACT_CALL_LOCAL;
import static com.hedera.hapi.node.base.HederaFunctionality.CONTRACT_CREATE;
import static com.hedera.hapi.node.base.HederaFunctionality.CRYPTO_GET_ACCOUNT_BALANCE;
import static com.hedera.hapi.node.base.HederaFunctionality.CRYPTO_TRANSFER;
import static com.hedera.hapi.node.base.HederaFunctionality.ETHEREUM_TRANSACTION;
import static com.hedera.hapi.node.base.HederaFunctionality.GET_VERSION_INFO;
import static com.hedera.hapi.node.base.HederaFunctionality.TOKEN_BURN;
import static com.hedera.hapi.node.base.HederaFunctionality.TOKEN_MINT;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.collection.IsIterableContainingInOrder.contains;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.protobuf.ByteString;
import com.hedera.hapi.node.base.AccountAmount;
import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.TransferList;
import com.hedera.hapi.node.contract.ContractCallTransactionBody;
import com.hedera.hapi.node.contract.ContractCreateTransactionBody;
import com.hedera.hapi.node.contract.EthereumTransactionBody;
import com.hedera.hapi.node.token.CryptoTransferTransactionBody;
import com.hedera.hapi.node.token.TokenMintTransactionBody;
import com.hedera.hapi.node.transaction.Query;
import com.hedera.hapi.node.transaction.ThrottleDefinitions;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.node.app.hapi.utils.sysfiles.domain.throttling.ScaleFactor;
import com.hedera.node.app.hapi.utils.throttles.DeterministicThrottle;
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
import com.hedera.node.config.data.TokensConfig;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;

@ExtendWith({MockitoExtension.class, LogCaptureExtension.class})
class ThrottleAccumulatorTest {
    private static final Bytes ETH_LAZY_CREATE = Bytes.fromHex(
            "02f8ad82012a80a000000000000000000000000000000000000000000000000000000000000003e8a0000000000000000000000000000000000000000000000000000000746a528800831e848094fee687d5088faff48013a6767505c027e2742536880de0b6b3a764000080c080a0f5ddf2394311e634e2147bf38583a017af45f4326bdf5746cac3a1110f973e4fa025bad52d9a9f8b32eb983c9fb8959655258bd75e2826b2c6a48d4c26ec30d112");
    private final Instant consensusNow = Instant.ofEpochSecond(1_234_567L, 123);
    private static final int CAPACITY_SPLIT = 2;
    private final ScaleFactor nftScaleFactor = ScaleFactor.from("5:2");

    @LoggingSubject
    private ThrottleAccumulator subject;

    @Mock
    private ConfigProvider configProvider;

    @Mock
    private ContractsConfig contractsConfig;

    @Mock
    private AccountsConfig accountsConfig;

    @Mock
    private TokensConfig tokensConfig;

    @Mock
    private AutoCreationConfig autoCreationConfig;

    @Mock
    private LazyCreationConfig lazyCreationConfig;

    @Mock
    private VersionedConfiguration configuration;

    @Mock
    private Query query;

    @LoggingTarget
    private LogCaptor logCaptor;

    @Mock
    private TransactionInfo transactionInfo;

    @Mock
    private HederaState state;

    @Mock
    private ReadableStates readableStates;

    @Mock
    private ReadableKVState aliases;

    @BeforeEach
    void setUp() {
        subject = new ThrottleAccumulator(() -> CAPACITY_SPLIT, configProvider);

        given(configProvider.getConfiguration()).willReturn(configuration);
    }

    @Test
    void worksAsExpectedForKnownQueries() throws IOException {
        // given
        given(configuration.getConfigData(ContractsConfig.class)).willReturn(contractsConfig);
        given(contractsConfig.throttleThrottleByGas()).willReturn(false);

        final var defs = getThrottleDefs("bootstrap/throttles.json");
        subject.rebuildFor(defs);

        // when
        var noAns = subject.shouldThrottle(CRYPTO_GET_ACCOUNT_BALANCE, consensusNow, query);
        subject.shouldThrottle(GET_VERSION_INFO, consensusNow.plusNanos(1), query);
        var yesAns = subject.shouldThrottle(GET_VERSION_INFO, consensusNow.plusNanos(2), query);
        var throttlesNow = subject.activeThrottlesFor(CRYPTO_GET_ACCOUNT_BALANCE);
        var dNow = throttlesNow.get(0);

        // then
        assertFalse(noAns);
        assertTrue(yesAns);
        assertEquals(10999999990000L, dNow.used());
    }

    @Test
    void worksAsExpectedForUnknownQueries() throws IOException {
        // given
        given(configuration.getConfigData(ContractsConfig.class)).willReturn(contractsConfig);
        given(contractsConfig.throttleThrottleByGas()).willReturn(false);

        final var defs = getThrottleDefs("bootstrap/throttles.json");

        // when
        subject.rebuildFor(defs);

        // then
        assertTrue(subject.shouldThrottle(CONTRACT_CALL_LOCAL, consensusNow, query));
    }

    @Test
    void shouldThrottleByGasAndTotalAllowedGasPerSecNotSetOrZero() {
        // given
        given(configuration.getConfigData(ContractsConfig.class)).willReturn(contractsConfig);
        given(contractsConfig.throttleThrottleByGas()).willReturn(true);

        // when
        subject.applyGasConfig();

        // then
        assertNull(subject.gasLimitThrottle());
        assertThat(logCaptor.warnLogs(), contains("Consensus gas throttling enabled, but limited to 0 gas/sec"));
    }

    @Test
    void managerBehavesAsExpectedForFungibleMint() throws IOException {
        // given
        given(configuration.getConfigData(AccountsConfig.class)).willReturn(accountsConfig);
        given(accountsConfig.lastThrottleExempt()).willReturn(100L);
        given(configuration.getConfigData(ContractsConfig.class)).willReturn(contractsConfig);
        given(contractsConfig.throttleThrottleByGas()).willReturn(true);

        given(transactionInfo.payerID())
                .willReturn(AccountID.newBuilder().accountNum(1234L).build());

        final var defs = getThrottleDefs("bootstrap/throttles.json");

        givenMintWith(0);

        // when
        subject.rebuildFor(defs);
        // and
        var firstAns = subject.shouldThrottle(transactionInfo, consensusNow, state);
        boolean subsequentAns = false;
        for (int i = 1; i <= 3000; i++) {
            subsequentAns = subject.shouldThrottle(transactionInfo, consensusNow.plusNanos(i), state);
        }
        var throttlesNow = subject.activeThrottlesFor(TOKEN_MINT);
        var aNow = throttlesNow.get(0);

        // then
        assertFalse(firstAns);
        assertTrue(subsequentAns);
        assertEquals(29999955000000000L, aNow.used());
    }

    @Test
    void managerBehavesAsExpectedForNftMint() throws IOException {
        // given
        given(configuration.getConfigData(AccountsConfig.class)).willReturn(accountsConfig);
        given(accountsConfig.lastThrottleExempt()).willReturn(100L);
        given(configuration.getConfigData(ContractsConfig.class)).willReturn(contractsConfig);
        given(contractsConfig.throttleThrottleByGas()).willReturn(true);

        given(transactionInfo.payerID())
                .willReturn(AccountID.newBuilder().accountNum(1234L).build());

        final var numNfts = 3;
        final var defs = getThrottleDefs("bootstrap/throttles.json");

        givenMintWith(numNfts);
        given(configuration.getConfigData(TokensConfig.class)).willReturn(tokensConfig);
        given(tokensConfig.nftsMintThrottleScaleFactor()).willReturn(nftScaleFactor);
        // when
        subject.rebuildFor(defs);
        // and
        var firstAns = subject.shouldThrottle(transactionInfo, consensusNow, state);
        boolean subsequentAns = false;
        for (int i = 1; i <= 400; i++) {
            subsequentAns = subject.shouldThrottle(transactionInfo, consensusNow.plusNanos(i), state);
        }
        var throttlesNow = subject.activeThrottlesFor(TOKEN_MINT);
        // and
        var aNow = throttlesNow.get(0);

        // then
        assertFalse(firstAns);
        assertTrue(subsequentAns);
        assertEquals(29999994000000000L, aNow.used());
    }

    @Test
    void managerBehavesAsExpectedForMultiBucketOp() throws IOException {
        // given
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
        var firstAns = subject.shouldThrottle(transactionInfo, consensusNow, state);
        boolean subsequentAns = false;
        for (int i = 1; i <= 12; i++) {
            subsequentAns = subject.shouldThrottle(transactionInfo, consensusNow.plusNanos(i), state);
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

    @Test
    void handlesThrottleExemption() throws IOException {
        // given
        given(configuration.getConfigData(AccountsConfig.class)).willReturn(accountsConfig);
        given(accountsConfig.lastThrottleExempt()).willReturn(100L);

        final var defs = getThrottleDefs("bootstrap/throttles.json");
        given(transactionInfo.functionality()).willReturn(CONTRACT_CALL);
        given(transactionInfo.payerID())
                .willReturn(AccountID.newBuilder().accountNum(1L).build());

        // when:
        subject.rebuildFor(defs);
        // and:
        var firstAns = subject.shouldThrottle(transactionInfo, consensusNow, state);
        for (int i = 1; i <= 12; i++) {
            assertFalse(subject.shouldThrottle(transactionInfo, consensusNow.plusNanos(i), state));
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

    @Test
    @MockitoSettings(strictness = org.mockito.quality.Strictness.LENIENT)
    void alwaysThrottleNOfUnmanaged() throws IOException {
        final var defs = getThrottleDefs("bootstrap/throttles.json");

        subject.rebuildFor(defs);

        assertTrue(subject.shouldThrottleNOfUnscaled(2, TOKEN_BURN, consensusNow));
    }

    @Test
    @MockitoSettings(strictness = org.mockito.quality.Strictness.LENIENT)
    void canThrottleNOfManaged() throws IOException {
        final var defs = getThrottleDefs("bootstrap/throttles.json");

        subject.rebuildFor(defs);

        assertFalse(subject.shouldThrottleNOfUnscaled(1, TOKEN_MINT, consensusNow));
        final var oneUsed = subject.activeThrottlesFor(TOKEN_MINT).get(0).used();
        assertFalse(subject.shouldThrottleNOfUnscaled(41, TOKEN_MINT, consensusNow));
        final var fortyTwoUsed = subject.activeThrottlesFor(TOKEN_MINT).get(0).used();
        assertEquals(42 * oneUsed, fortyTwoUsed);
    }

    @Test
    @MockitoSettings(strictness = org.mockito.quality.Strictness.LENIENT)
    void whenThrottlesUsesNoCapacity() throws IOException {
        final var defs = getThrottleDefs("bootstrap/throttles.json");

        subject.rebuildFor(defs);

        assertTrue(subject.shouldThrottleNOfUnscaled(11, CONTRACT_CALL, consensusNow));
        final var used = subject.activeThrottlesFor(CONTRACT_CALL).get(0).used();
        assertEquals(0, used);
    }

    @Test
    void computesNumImplicitCreationsIfNotAlreadyKnown() throws IOException {
        // given
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

        given(state.createReadableStates(any())).willReturn(readableStates);

        given(configuration.getConfigData(AutoCreationConfig.class)).willReturn(autoCreationConfig);
        given(autoCreationConfig.enabled()).willReturn(true);
        given(configuration.getConfigData(LazyCreationConfig.class)).willReturn(lazyCreationConfig);
        given(lazyCreationConfig.enabled()).willReturn(false);

        // when
        subject.rebuildFor(defs);
        var ans = subject.shouldThrottle(transactionInfo, consensusNow, state);

        // then
        assertFalse(ans);
    }

    @Test
    void ifLazyCreationEnabledComputesNumImplicitCreationsIfNotAlreadyKnown() throws IOException {
        // given
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

        given(state.createReadableStates(any())).willReturn(readableStates);

        given(configuration.getConfigData(AutoCreationConfig.class)).willReturn(autoCreationConfig);
        given(autoCreationConfig.enabled()).willReturn(false);
        given(configuration.getConfigData(LazyCreationConfig.class)).willReturn(lazyCreationConfig);
        given(lazyCreationConfig.enabled()).willReturn(true);

        // when
        subject.rebuildFor(defs);
        var ans = subject.shouldThrottle(transactionInfo, consensusNow, state);

        // then
        assertFalse(ans);
    }

    @Test
    void cryptoTransfersWithNoAutoAccountCreationsAreThrottledAsExpected() throws IOException {
        // given
        given(configuration.getConfigData(AccountsConfig.class)).willReturn(accountsConfig);
        given(accountsConfig.lastThrottleExempt()).willReturn(100L);
        given(configuration.getConfigData(ContractsConfig.class)).willReturn(contractsConfig);
        given(contractsConfig.throttleThrottleByGas()).willReturn(false);

        given(transactionInfo.payerID())
                .willReturn(AccountID.newBuilder().accountNum(1234L).build());

        final var defs = getThrottleDefs("bootstrap/throttles.json");

        given(transactionInfo.functionality()).willReturn(CRYPTO_TRANSFER);
        givenTransferWithImplicitCreations(0);

        given(state.createReadableStates(any())).willReturn(readableStates);

        given(configuration.getConfigData(AutoCreationConfig.class)).willReturn(autoCreationConfig);
        given(autoCreationConfig.enabled()).willReturn(false);
        given(configuration.getConfigData(LazyCreationConfig.class)).willReturn(lazyCreationConfig);
        given(lazyCreationConfig.enabled()).willReturn(false);

        // when
        subject.rebuildFor(defs);
        var ans = subject.shouldThrottle(transactionInfo, consensusNow, state);

        // then
        assertFalse(ans);
    }

    @Test
    void managerRejectsCryptoTransfersWithAutoAccountCreationsAsExpected() throws IOException {
        // given
        given(configuration.getConfigData(AccountsConfig.class)).willReturn(accountsConfig);
        given(accountsConfig.lastThrottleExempt()).willReturn(100L);
        given(configuration.getConfigData(ContractsConfig.class)).willReturn(contractsConfig);
        given(contractsConfig.throttleThrottleByGas()).willReturn(false);

        given(transactionInfo.payerID())
                .willReturn(AccountID.newBuilder().accountNum(1234L).build());

        final var defs = getThrottleDefs("bootstrap/throttles.json");

        given(transactionInfo.functionality()).willReturn(CRYPTO_TRANSFER);
        givenTransferWithImplicitCreations(10);
        given(state.createReadableStates(any())).willReturn(readableStates);
        given(readableStates.get(any())).willReturn(aliases);

        given(configuration.getConfigData(AutoCreationConfig.class)).willReturn(autoCreationConfig);
        given(autoCreationConfig.enabled()).willReturn(true);
        given(configuration.getConfigData(LazyCreationConfig.class)).willReturn(lazyCreationConfig);
        given(lazyCreationConfig.enabled()).willReturn(false);

        // when
        subject.rebuildFor(defs);
        var ans = subject.shouldThrottle(transactionInfo, consensusNow, state);

        // then
        assertTrue(ans);
    }

    @Test
    void managerRejectsCryptoTransfersWithMissingCryptoCreateThrottle() throws IOException {
        // given
        given(configuration.getConfigData(AccountsConfig.class)).willReturn(accountsConfig);
        given(accountsConfig.lastThrottleExempt()).willReturn(100L);
        given(configuration.getConfigData(ContractsConfig.class)).willReturn(contractsConfig);
        given(contractsConfig.throttleThrottleByGas()).willReturn(false);

        given(transactionInfo.payerID())
                .willReturn(AccountID.newBuilder().accountNum(1234L).build());

        final var defs = getThrottleDefs("bootstrap/throttles-sans-creation.json");

        given(transactionInfo.functionality()).willReturn(CRYPTO_TRANSFER);
        givenTransferWithImplicitCreations(1);
        given(state.createReadableStates(any())).willReturn(readableStates);
        given(readableStates.get(any())).willReturn(aliases);

        given(configuration.getConfigData(AutoCreationConfig.class)).willReturn(autoCreationConfig);
        given(autoCreationConfig.enabled()).willReturn(true);
        given(configuration.getConfigData(LazyCreationConfig.class)).willReturn(lazyCreationConfig);
        given(lazyCreationConfig.enabled()).willReturn(false);

        // when
        subject.rebuildFor(defs);
        var ans = subject.shouldThrottle(transactionInfo, consensusNow, state);

        // then
        assertTrue(ans);
    }

    @Test
    void ethereumTransactionWithNoAutoAccountCreationsAreThrottledAsExpected() throws IOException {
        // given
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

        given(state.createReadableStates(any())).willReturn(readableStates);
        given(readableStates.get(any())).willReturn(aliases);

        given(configuration.getConfigData(AutoCreationConfig.class)).willReturn(autoCreationConfig);
        given(autoCreationConfig.enabled()).willReturn(false);
        given(configuration.getConfigData(LazyCreationConfig.class)).willReturn(lazyCreationConfig);
        given(lazyCreationConfig.enabled()).willReturn(false);

        // when
        subject.rebuildFor(defs);
        var ans = subject.shouldThrottle(transactionInfo, consensusNow, state);

        // then
        assertFalse(ans);
    }

    @Test
    void ethereumTransactionWithAutoAccountCreationsButNoLazyCreationsAreThrottledAsExpected() throws IOException {
        // given
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

        given(state.createReadableStates(any())).willReturn(readableStates);
        given(readableStates.get(any())).willReturn(aliases);

        given(configuration.getConfigData(AutoCreationConfig.class)).willReturn(autoCreationConfig);
        given(autoCreationConfig.enabled()).willReturn(true);
        given(configuration.getConfigData(LazyCreationConfig.class)).willReturn(lazyCreationConfig);
        given(lazyCreationConfig.enabled()).willReturn(false);

        // when
        subject.rebuildFor(defs);
        var ans = subject.shouldThrottle(transactionInfo, consensusNow, state);

        // then
        assertFalse(ans);
    }

    @Test
    void managerAllowsEthereumTransactionWithAutoAccountCreationsAsExpected() throws IOException {
        // given
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

        given(state.createReadableStates(any())).willReturn(readableStates);
        given(readableStates.get(any())).willReturn(aliases);

        given(configuration.getConfigData(AutoCreationConfig.class)).willReturn(autoCreationConfig);
        given(autoCreationConfig.enabled()).willReturn(true);
        given(configuration.getConfigData(LazyCreationConfig.class)).willReturn(lazyCreationConfig);
        given(lazyCreationConfig.enabled()).willReturn(true);

        // when
        subject.rebuildFor(defs);
        var ans = subject.shouldThrottle(transactionInfo, consensusNow, state);

        // then
        assertFalse(ans);
    }

    @Test
    void managerRejectsEthereumTransactionWithMissingCryptoCreateThrottle() throws IOException {
        // given
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

        given(state.createReadableStates(any())).willReturn(readableStates);
        given(readableStates.get(any())).willReturn(aliases);

        given(configuration.getConfigData(AutoCreationConfig.class)).willReturn(autoCreationConfig);
        given(autoCreationConfig.enabled()).willReturn(true);
        given(configuration.getConfigData(LazyCreationConfig.class)).willReturn(lazyCreationConfig);
        given(lazyCreationConfig.enabled()).willReturn(true);

        // when
        subject.rebuildFor(defs);
        var ans = subject.shouldThrottle(transactionInfo, consensusNow, state);

        // then
        assertTrue(ans);
    }

    @Test
    void alwaysThrottlesContractCallWhenGasThrottleIsNotDefined() {
        // given
        given(configuration.getConfigData(AccountsConfig.class)).willReturn(accountsConfig);
        given(accountsConfig.lastThrottleExempt()).willReturn(100L);
        given(configuration.getConfigData(ContractsConfig.class)).willReturn(contractsConfig);
        given(contractsConfig.throttleThrottleByGas()).willReturn(true);
        given(contractsConfig.maxGasPerSec()).willReturn(0L);

        given(transactionInfo.payerID())
                .willReturn(AccountID.newBuilder().accountNum(1234L).build());

        given(transactionInfo.functionality()).willReturn(CONTRACT_CALL);

        // when
        subject.applyGasConfig();

        // then
        assertTrue(subject.shouldThrottle(transactionInfo, consensusNow, state));
    }

    @Test
    void alwaysThrottlesContractCallWhenGasThrottleReturnsTrue() {
        // given
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
        assertTrue(subject.shouldThrottle(transactionInfo, consensusNow, state));
    }

    @Test
    void alwaysThrottlesContractCreateWhenGasThrottleIsNotDefined() {
        // given
        given(configuration.getConfigData(AccountsConfig.class)).willReturn(accountsConfig);
        given(accountsConfig.lastThrottleExempt()).willReturn(100L);
        given(configuration.getConfigData(ContractsConfig.class)).willReturn(contractsConfig);
        given(contractsConfig.throttleThrottleByGas()).willReturn(true);
        given(contractsConfig.maxGasPerSec()).willReturn(0L);

        given(transactionInfo.payerID())
                .willReturn(AccountID.newBuilder().accountNum(1234L).build());

        given(transactionInfo.functionality()).willReturn(CONTRACT_CREATE);

        // when
        subject.applyGasConfig();

        // then
        assertTrue(subject.shouldThrottle(transactionInfo, consensusNow, state));
    }

    @Test
    void alwaysThrottlesContractCreateWhenGasThrottleReturnsTrue() {
        // given
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
        assertTrue(subject.shouldThrottle(transactionInfo, consensusNow, state));
        assertTrue(subject.wasLastTxnGasThrottled());

        given(transactionInfo.functionality()).willReturn(TOKEN_BURN);
        subject.shouldThrottle(transactionInfo, consensusNow.plusSeconds(1), state);
        assertFalse(subject.wasLastTxnGasThrottled());
    }

    @Test
    void alwaysThrottlesEthereumTxnWhenGasThrottleIsNotDefined() {
        // given
        given(configuration.getConfigData(AccountsConfig.class)).willReturn(accountsConfig);
        given(accountsConfig.lastThrottleExempt()).willReturn(100L);
        given(configuration.getConfigData(ContractsConfig.class)).willReturn(contractsConfig);
        given(contractsConfig.throttleThrottleByGas()).willReturn(true);
        given(contractsConfig.maxGasPerSec()).willReturn(0L);

        given(transactionInfo.payerID())
                .willReturn(AccountID.newBuilder().accountNum(1234L).build());

        given(transactionInfo.functionality()).willReturn(ETHEREUM_TRANSACTION);

        // when
        subject.applyGasConfig();

        // then
        assertTrue(subject.shouldThrottle(transactionInfo, consensusNow, state));
    }

    @Test
    void alwaysThrottlesEthereumTxnWhenGasThrottleReturnsTrue() {
        // given
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
        assertTrue(subject.shouldThrottle(transactionInfo, consensusNow, state));
        assertTrue(subject.wasLastTxnGasThrottled());

        given(transactionInfo.functionality()).willReturn(TOKEN_BURN);
        subject.shouldThrottle(transactionInfo, consensusNow.plusSeconds(1), state);
        assertFalse(subject.wasLastTxnGasThrottled());
    }

    @Test
    void gasLimitThrottleReturnsCorrectObject() {
        // given
        given(configuration.getConfigData(ContractsConfig.class)).willReturn(contractsConfig);
        given(contractsConfig.throttleThrottleByGas()).willReturn(true);
        var capacity = 10L;
        given(contractsConfig.maxGasPerSec()).willReturn(capacity);

        // when
        subject.applyGasConfig();

        // then
        assertEquals(capacity, subject.gasLimitThrottle().capacity());
    }

    @Test
    @MockitoSettings(strictness = org.mockito.quality.Strictness.LENIENT)
    void constructsExpectedBucketsFromTestResource() throws IOException {
        // given
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
    }

    @Test
    void alwaysRejectsIfNoThrottle() {
        given(configuration.getConfigData(AccountsConfig.class)).willReturn(accountsConfig);
        given(accountsConfig.lastThrottleExempt()).willReturn(100L);
        given(configuration.getConfigData(ContractsConfig.class)).willReturn(contractsConfig);
        given(contractsConfig.throttleThrottleByGas()).willReturn(false);
        given(transactionInfo.payerID())
                .willReturn(AccountID.newBuilder().accountNum(1234L).build());

        given(transactionInfo.functionality()).willReturn(CONTRACT_CALL);

        assertTrue(subject.shouldThrottle(transactionInfo, consensusNow, state));
        Assertions.assertSame(Collections.emptyList(), subject.activeThrottlesFor(CONTRACT_CALL));
    }

    @Test
    void verifyLeakUnusedGas() {
        given(configuration.getConfigData(AccountsConfig.class)).willReturn(accountsConfig);
        given(accountsConfig.lastThrottleExempt()).willReturn(100L);
        given(configuration.getConfigData(ContractsConfig.class)).willReturn(contractsConfig);
        given(contractsConfig.throttleThrottleByGas()).willReturn(true);
        given(contractsConfig.maxGasPerSec()).willReturn(150L);

        // payer is not exempt
        given(transactionInfo.payerID())
                .willReturn(AccountID.newBuilder().accountNum(1234L).build());

        subject.applyGasConfig();

        assertTrue(subject.gasLimitThrottle().allow(consensusNow, 100));
        assertFalse(subject.gasLimitThrottle().allow(consensusNow, 100));

        subject.leakUnusedGasPreviouslyReserved(transactionInfo, 100L);

        assertTrue(subject.gasLimitThrottle().allow(consensusNow, 100));
        assertFalse(subject.gasLimitThrottle().allow(consensusNow, 100));

        // payer is exempt
        given(transactionInfo.payerID())
                .willReturn(AccountID.newBuilder().accountNum(1L).build());

        subject.leakUnusedGasPreviouslyReserved(transactionInfo, 100L);

        assertFalse(subject.gasLimitThrottle().allow(consensusNow, 100));
        assertFalse(subject.gasLimitThrottle().allow(consensusNow, 100));
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
