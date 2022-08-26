/*
 * Copyright (C) 2021-2022 Hedera Hashgraph, LLC
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
package com.hedera.services.state.logic;

import static com.hedera.services.context.properties.PropertyNames.STAKING_PERIOD_MINS;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.ContractCall;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.ContractCreate;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.TokenMint;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.never;
import static org.mockito.BDDMockito.verify;
import static org.mockito.Mockito.times;

import com.hedera.services.config.MockGlobalDynamicProps;
import com.hedera.services.context.TransactionContext;
import com.hedera.services.context.domain.trackers.IssEventInfo;
import com.hedera.services.context.domain.trackers.IssEventStatus;
import com.hedera.services.context.properties.NodeLocalProperties;
import com.hedera.services.context.properties.PropertySource;
import com.hedera.services.fees.FeeMultiplierSource;
import com.hedera.services.fees.HbarCentExchange;
import com.hedera.services.ledger.accounts.staking.EndOfStakingPeriodCalculator;
import com.hedera.services.state.initialization.SystemFilesManager;
import com.hedera.services.state.merkle.MerkleNetworkContext;
import com.hedera.services.state.submerkle.ExchangeRates;
import com.hedera.services.stats.HapiOpCounters;
import com.hedera.services.stats.MiscRunningAvgs;
import com.hedera.services.throttling.ExpiryThrottle;
import com.hedera.services.throttling.FunctionalityThrottling;
import com.hedera.services.utils.accessors.SignedTxnAccessor;
import java.time.Instant;
import java.util.Optional;
import java.util.function.BiPredicate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class NetworkCtxManagerTest {
    private final int issResetPeriod = 5;
    private final long someGasUsage = 8910L;
    private final Instant sometime = Instant.ofEpochSecond(1_234_567L);
    private final Instant sometimeSameDay = sometime.plusSeconds(issResetPeriod + 1L);
    private final Instant sometimeNextDay = sometime.plusSeconds(86_400L);
    private final MockGlobalDynamicProps mockDynamicProps = new MockGlobalDynamicProps();

    @Mock private IssEventInfo issInfo;
    @Mock private NodeLocalProperties nodeLocalProperties;
    @Mock private HapiOpCounters opCounters;
    @Mock private HbarCentExchange exchange;
    @Mock private FeeMultiplierSource feeMultiplierSource;
    @Mock private SystemFilesManager systemFilesManager;
    @Mock private MerkleNetworkContext networkCtx;
    @Mock private FunctionalityThrottling handleThrottling;
    @Mock private BiPredicate<Instant, Instant> shouldUpdateMidnightRates;
    @Mock private TransactionContext txnCtx;
    @Mock private SignedTxnAccessor txnAccessor;
    @Mock private MiscRunningAvgs runningAvgs;
    @Mock private EndOfStakingPeriodCalculator endOfStakingPeriodCalculator;
    @Mock private PropertySource propertySource;
    @Mock private ExpiryThrottle expiryThrottle;

    private NetworkCtxManager subject;

    @BeforeEach
    void setUp() {
        given(propertySource.getLongProperty(STAKING_PERIOD_MINS)).willReturn(1440L);
        given(nodeLocalProperties.issResetPeriod()).willReturn(issResetPeriod);

        subject =
                new NetworkCtxManager(
                        issInfo,
                        expiryThrottle,
                        nodeLocalProperties,
                        opCounters,
                        exchange,
                        systemFilesManager,
                        feeMultiplierSource,
                        mockDynamicProps,
                        handleThrottling,
                        () -> networkCtx,
                        txnCtx,
                        runningAvgs,
                        endOfStakingPeriodCalculator,
                        propertySource);
    }

    @Test
    void recordsGasUsedWhenFirstTxnFinishedInConsSecond() {
        subject.setGasUsedThisConsSec(someGasUsage);
        subject.setConsensusSecondJustChanged(true);

        subject.finishIncorporating(TokenMint);

        verify(runningAvgs).recordGasPerConsSec(someGasUsage);
        assertEquals(0, subject.getGasUsedThisConsSec());
    }

    @Test
    void updatesGasUsedForContractOperations() {
        given(txnCtx.getGasUsedForContractTxn()).willReturn(someGasUsage);
        given(txnCtx.hasContractResult()).willReturn(true);

        subject.finishIncorporating(ContractCall);

        assertEquals(someGasUsage, subject.getGasUsedThisConsSec());
    }

    @Test
    void doesntInitObservableSysFilesIfAlreadyLoaded() {
        given(systemFilesManager.areObservableFilesLoaded()).willReturn(true);

        // when:
        subject.loadObservableSysFilesIfNeeded();

        // then:
        verify(systemFilesManager, never()).loadObservableSystemFiles();
        verify(networkCtx, never()).resetThrottlingFromSavedSnapshots(handleThrottling);
        verify(networkCtx, never())
                .resetMultiplierSourceFromSavedCongestionStarts(feeMultiplierSource);
        verify(feeMultiplierSource, never()).resetExpectations();
    }

    @Test
    void initsSystemFilesAsExpected() {
        given(systemFilesManager.areObservableFilesLoaded()).willReturn(false);

        // when:
        subject.loadObservableSysFilesIfNeeded();

        // then:
        verify(systemFilesManager).loadObservableSystemFiles();
        verify(networkCtx).resetThrottlingFromSavedSnapshots(handleThrottling);
        verify(networkCtx).resetExpiryThrottleFromSavedSnapshot(expiryThrottle);
        verify(networkCtx).resetMultiplierSourceFromSavedCongestionStarts(feeMultiplierSource);
        verify(feeMultiplierSource).resetExpectations();
    }

    @Test
    void finalizesContextAsExpected() {
        // when:
        subject.finishIncorporating(TokenMint);

        // then:
        verify(opCounters).countHandled(TokenMint);
        verify(networkCtx).syncThrottling(handleThrottling);
        verify(networkCtx).syncMultiplierSource(feeMultiplierSource);
        verify(handleThrottling, times(0)).leakUnusedGasPreviouslyReserved(any(), anyLong());
    }

    @Test
    void whenFinishingContractCallUnusedGasIsLeaked() {
        // setup:
        given(txnAccessor.getGasLimitForContractTx()).willReturn(10_000L);
        given(txnCtx.accessor()).willReturn(txnAccessor);
        given(txnCtx.hasContractResult()).willReturn(true);
        given(txnCtx.getGasUsedForContractTxn()).willReturn(1000L);

        mockDynamicProps.setThrottleByGas(true);

        // when:
        subject.finishIncorporating(ContractCall);

        // then:
        verify(opCounters).countHandled(ContractCall);
        verify(handleThrottling).leakUnusedGasPreviouslyReserved(txnAccessor, 9_000L);
        verify(networkCtx).syncThrottling(handleThrottling);
        verify(networkCtx).syncMultiplierSource(feeMultiplierSource);
    }

    @Test
    void whenFinishingContractCallUnusedGasIsNotLeakedIfGasThrottlingIsTurnedOff() {
        // setup:
        mockDynamicProps.setThrottleByGas(false);

        // when:
        subject.finishIncorporating(ContractCall);

        // then:
        verify(opCounters).countHandled(ContractCall);
        verify(networkCtx).syncThrottling(handleThrottling);
        verify(networkCtx).syncMultiplierSource(feeMultiplierSource);
        verify(handleThrottling, never()).leakUnusedGasPreviouslyReserved(any(), anyLong());
    }

    @Test
    void whenFinishingContractCallUnusedGasIsNotLeakedForUnsuccessfulTxn() {
        // setup:
        given(txnCtx.hasContractResult()).willReturn(false);
        mockDynamicProps.setThrottleByGas(true);

        // when:
        subject.finishIncorporating(ContractCall);

        // then:
        verify(opCounters).countHandled(ContractCall);
        verify(networkCtx).syncThrottling(handleThrottling);
        verify(networkCtx).syncMultiplierSource(feeMultiplierSource);
        verify(handleThrottling, never()).leakUnusedGasPreviouslyReserved(any(), anyLong());
    }

    @Test
    void whenFinishingContractCreateUnusedGasIsLeaked() {
        // setup:
        given(txnAccessor.getGasLimitForContractTx()).willReturn(10_000L);
        given(txnCtx.accessor()).willReturn(txnAccessor);
        given(txnCtx.hasContractResult()).willReturn(true);
        given(txnCtx.getGasUsedForContractTxn()).willReturn(1000L);
        mockDynamicProps.setThrottleByGas(true);

        // when:
        subject.finishIncorporating(ContractCreate);

        // then:
        verify(opCounters).countHandled(ContractCreate);
        verify(handleThrottling).leakUnusedGasPreviouslyReserved(txnAccessor, 9_000L);
        verify(networkCtx).syncThrottling(handleThrottling);
        verify(networkCtx).syncMultiplierSource(feeMultiplierSource);
    }

    @Test
    void whenFinishingContractCreateUnusedGasIsNotLeakedForUnsuccessfulTX() {
        // setup:
        given(txnCtx.hasContractResult()).willReturn(false);
        mockDynamicProps.setThrottleByGas(true);

        // when:
        subject.finishIncorporating(ContractCreate);

        // then:
        verify(opCounters).countHandled(ContractCreate);
        verify(networkCtx).syncThrottling(handleThrottling);
        verify(networkCtx).syncMultiplierSource(feeMultiplierSource);
        verify(handleThrottling, never()).leakUnusedGasPreviouslyReserved(any(), anyLong());
    }

    @Test
    void whenFinishingContractCreateUnusedGasIsNotLeakedIfThrottleByGasIsTurnedOff() {
        // setup:
        mockDynamicProps.setThrottleByGas(false);

        // when:
        subject.finishIncorporating(ContractCreate);

        // then:
        verify(opCounters).countHandled(ContractCreate);
        verify(handleThrottling, never()).leakUnusedGasPreviouslyReserved(any(), anyLong());
        verify(networkCtx).syncThrottling(handleThrottling);
        verify(networkCtx).syncMultiplierSource(feeMultiplierSource);
    }

    @Test
    void relaxesIssInfoIfPastResetPeriod() {
        given(networkCtx.consensusTimeOfLastHandledTxn()).willReturn(sometime);
        given(issInfo.status()).willReturn(IssEventStatus.ONGOING_ISS);
        given(issInfo.consensusTimeOfRecentAlert()).willReturn(Optional.of(sometime));

        // when:
        subject.advanceConsensusClockTo(sometimeSameDay);

        // then:
        verify(issInfo).relax();
    }

    @Test
    void relaxesIssInfoIfConsensusTimeOfRecentAlertIsEmpty() {
        var oldMidnightRates = new ExchangeRates(1, 12, 1_234_567L, 1, 15, 2_345_678L);
        var curRates = new ExchangeRates(1, 120, 1_234_567L, 1, 150, 2_345_678L);

        given(exchange.activeRates()).willReturn(curRates.toGrpc());
        given(networkCtx.midnightRates()).willReturn(oldMidnightRates);
        given(issInfo.status()).willReturn(IssEventStatus.ONGOING_ISS);
        given(issInfo.consensusTimeOfRecentAlert()).willReturn(Optional.empty());

        // when:
        subject.advanceConsensusClockTo(sometimeSameDay);

        // then:
        verify(issInfo).relax();
    }

    @Test
    void doesNothingWithIssInfoIfNotOngoing() {
        // setup:
        var oldMidnightRates = new ExchangeRates(1, 12, 1_234_567L, 1, 15, 2_345_678L);
        var curRates = new ExchangeRates(1, 120, 1_234_567L, 1, 150, 2_345_678L);

        given(exchange.activeRates()).willReturn(curRates.toGrpc());
        given(networkCtx.midnightRates()).willReturn(oldMidnightRates);

        // when:
        subject.advanceConsensusClockTo(sometime);

        // then:
        assertEquals(issResetPeriod, subject.getIssResetPeriod());
        // and:
        verify(issInfo, never()).relax();
    }

    @Test
    void advancesClockAsExpectedWhenFirstTxn() { // setup:
        var oldMidnightRates = new ExchangeRates(1, 12, 1_234_567L, 1, 15, 2_345_678L);
        var curRates = new ExchangeRates(1, 120, 1_234_567L, 1, 150, 2_345_678L);
        given(networkCtx.consensusTimeOfLastHandledTxn()).willReturn(null);

        given(exchange.activeRates()).willReturn(curRates.toGrpc());
        given(networkCtx.midnightRates()).willReturn(oldMidnightRates);

        // when:
        subject.advanceConsensusClockTo(sometimeNextDay);

        // then:
        verify(networkCtx).setConsensusTimeOfLastHandledTxn(sometimeNextDay);
        assertEquals(oldMidnightRates, curRates);
    }

    @Test
    void
            advancesClockAsExpectedWhenPassingMidnightAfterBoundaryCheckIntervalElapsedFromLastCheck() {
        // setup:
        var oldMidnightRates = new ExchangeRates(1, 12, 1_234_567L, 1, 15, 2_345_678L);
        var curRates = new ExchangeRates(1, 120, 1_234_567L, 1, 150, 2_345_678L);
        // and:
        subject.setIsNextDay(shouldUpdateMidnightRates);

        given(shouldUpdateMidnightRates.test(sometime, sometimeNextDay)).willReturn(true);
        given(exchange.activeRates()).willReturn(curRates.toGrpc());
        given(networkCtx.midnightRates()).willReturn(oldMidnightRates);
        given(networkCtx.consensusTimeOfLastHandledTxn()).willReturn(sometime);

        // when:
        subject.advanceConsensusClockTo(sometimeNextDay);

        // then:
        verify(networkCtx).setConsensusTimeOfLastHandledTxn(sometimeNextDay);
        assertEquals(oldMidnightRates, curRates);
    }

    @Test
    void doesntUpdateRatesIfTestDoesntSayTooButDoesUpdateLastMidnightCheck() {
        // setup:
        subject.setIsNextDay(shouldUpdateMidnightRates);

        given(networkCtx.consensusTimeOfLastHandledTxn()).willReturn(sometime);

        // when:
        subject.advanceConsensusClockTo(sometimeNextDay);

        // then:
        verify(networkCtx).setConsensusTimeOfLastHandledTxn(sometimeNextDay);
    }

    @Test
    void recognizesWhenTxnStillInSameConsensusSecond() {
        // setup:
        final var sometimePlusSomeNanos = sometime.plusNanos(1_234);
        final var sometimePlusSomeMoreNanos = sometimePlusSomeNanos.plusNanos(1_234);

        given(networkCtx.consensusTimeOfLastHandledTxn()).willReturn(sometimePlusSomeNanos);

        // when:
        subject.advanceConsensusClockTo(sometimePlusSomeMoreNanos);

        // then:
        assertFalse(subject.currentTxnIsFirstInConsensusSecond());
    }

    @Test
    void recognizesWhenTxnSecondChanges() {
        // setup:
        final var sometimePlusSomeNanos = sometime.plusNanos(1_234);
        final var sometimePlusOneSecond = sometime.plusSeconds(1);

        given(networkCtx.consensusTimeOfLastHandledTxn()).willReturn(sometimePlusSomeNanos);

        // when:
        subject.advanceConsensusClockTo(sometimePlusOneSecond);

        // then:
        assertTrue(subject.currentTxnIsFirstInConsensusSecond());
    }

    @Test
    void recognizesFirstTxnMustBeFirstInSecond() {
        // setup:
        var oldMidnightRates = new ExchangeRates(1, 12, 1_234_567L, 1, 15, 2_345_678L);
        var curRates = new ExchangeRates(1, 120, 1_234_567L, 1, 150, 2_345_678L);

        given(exchange.activeRates()).willReturn(curRates.toGrpc());
        given(networkCtx.midnightRates()).willReturn(oldMidnightRates);

        // when:
        subject.advanceConsensusClockTo(sometimeNextDay);

        // then:
        assertTrue(subject.currentTxnIsFirstInConsensusSecond());
    }

    @Test
    void advancesClockAsExpectedWhenNotPassingMidnight() {
        given(networkCtx.consensusTimeOfLastHandledTxn()).willReturn(sometime);

        // when:
        subject.advanceConsensusClockTo(sometimeSameDay);

        // then:
        verify(networkCtx, never()).midnightRates();
        verify(networkCtx).setConsensusTimeOfLastHandledTxn(sometimeSameDay);
    }

    @Test
    void delegatesNotLoaded() {
        // when:
        subject.setObservableFilesNotLoaded();

        // then:
        verify(systemFilesManager).setObservableFilesNotLoaded();
    }

    @Test
    void defaultShouldUpdateOnlyTrueOnDifferentUtcDays() {
        // setup:
        final var now = Instant.parse("2021-06-07T23:59:59.369613Z");
        final var thenSameDay = Instant.parse("2021-06-07T23:59:59.99999Z");
        final var thenNextDay = Instant.parse("2021-06-08T00:00:00.00000Z");

        // given:
        final var updateTest = subject.getIsNextDay();

        // then:
        assertFalse(updateTest.test(now, thenSameDay));
        assertTrue(updateTest.test(now, thenNextDay));
    }

    @Test
    void nonDefaultPeriodShouldUpdateOnlyTrueOnDifferentMinutes() {
        // setup:
        final var now = Instant.parse("2021-06-07T23:59:58.369613Z");
        final var thenSameMinute = now.plusSeconds(1);
        final var thenNextMinute = now.plusSeconds(61);
        given(propertySource.getLongProperty(STAKING_PERIOD_MINS)).willReturn(1L);
        given(nodeLocalProperties.issResetPeriod()).willReturn(issResetPeriod);

        subject =
                new NetworkCtxManager(
                        issInfo,
                        expiryThrottle,
                        nodeLocalProperties,
                        opCounters,
                        exchange,
                        systemFilesManager,
                        feeMultiplierSource,
                        mockDynamicProps,
                        handleThrottling,
                        () -> networkCtx,
                        txnCtx,
                        runningAvgs,
                        endOfStakingPeriodCalculator,
                        propertySource);

        final BiPredicate<Instant, Instant> updateTest = subject::isNextPeriod;

        assertFalse(updateTest.test(now, thenSameMinute));
        assertTrue(updateTest.test(now, thenNextMinute));
    }

    @Test
    void isSameDayUTCTest() {
        Instant instant1_1 = Instant.parse("2019-08-14T23:59:59.0Z");
        Instant instant1_2 = Instant.parse("2019-08-14T23:59:59.99999Z");
        Instant instant2_1 = Instant.parse("2019-08-14T24:00:00.0Z");
        Instant instant2_2 = Instant.parse("2019-08-15T00:00:00.0Z");
        Instant instant2_3 = Instant.parse("2019-08-15T00:00:00.00001Z");

        assertTrue(NetworkCtxManager.inSameUtcDay(instant1_1, instant1_2));

        assertFalse(NetworkCtxManager.inSameUtcDay(instant1_1, instant2_1));
        assertFalse(NetworkCtxManager.inSameUtcDay(instant1_2, instant2_1));

        assertTrue(NetworkCtxManager.inSameUtcDay(instant2_1, instant2_2));
        assertTrue(NetworkCtxManager.inSameUtcDay(instant2_2, instant2_3));
    }
}
