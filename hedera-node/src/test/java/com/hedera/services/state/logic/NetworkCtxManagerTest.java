package com.hedera.services.state.logic;

/*-
 * ‌
 * Hedera Services Node
 * ​
 * Copyright (C) 2018 - 2021 Hedera Hashgraph, LLC
 * ​
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
 * ‍
 */

import com.hedera.services.config.MockGlobalDynamicProps;
import com.hedera.services.context.domain.trackers.IssEventInfo;
import com.hedera.services.context.domain.trackers.IssEventStatus;
import com.hedera.services.context.properties.GlobalDynamicProperties;
import com.hedera.services.context.properties.PropertySource;
import com.hedera.services.fees.FeeMultiplierSource;
import com.hedera.services.fees.HbarCentExchange;
import com.hedera.services.state.initialization.SystemFilesManager;
import com.hedera.services.state.merkle.MerkleNetworkContext;
import com.hedera.services.state.submerkle.ExchangeRates;
import com.hedera.services.stats.HapiOpCounters;
import com.hedera.services.throttling.FunctionalityThrottling;
import com.hedera.services.utils.SignedTxnAccessor;
import com.hederahashgraph.api.proto.java.Transaction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;
import java.util.function.BiPredicate;

import static com.hederahashgraph.api.proto.java.HederaFunctionality.TokenMint;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.never;
import static org.mockito.BDDMockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class NetworkCtxManagerTest {
	private int issResetPeriod = 5;
	private Instant sometime = Instant.ofEpochSecond(1_234_567L);
	private Instant sometimeSameDay = sometime.plusSeconds(issResetPeriod + 1L);
	private Instant sometimeNextDay = sometime.plusSeconds(86_400L);
	private final GlobalDynamicProperties mockDynamicProps = new MockGlobalDynamicProps();

	@Mock
	private IssEventInfo issInfo;
	@Mock
	private PropertySource properties;
	@Mock
	private HapiOpCounters opCounters;
	@Mock
	private HbarCentExchange exchange;
	@Mock
	private FeeMultiplierSource feeMultiplierSource;
	@Mock
	private SystemFilesManager systemFilesManager;
	@Mock
	private MerkleNetworkContext networkCtx;
	@Mock
	private FunctionalityThrottling handleThrottling;
	@Mock
	private BiPredicate<Instant, Instant> shouldUpdateMidnightRates;

	private NetworkCtxManager subject;

	@BeforeEach
	void setUp() {
		given(properties.getIntProperty("iss.resetPeriod")).willReturn(issResetPeriod);

		subject = new NetworkCtxManager(
				issInfo,
				properties,
				opCounters,
				exchange,
				systemFilesManager,
				feeMultiplierSource,
				mockDynamicProps,
				handleThrottling,
				() -> networkCtx);
	}

	@Test
	void doesntInitObservableSysFilesIfAlreadyLoaded() {
		given(systemFilesManager.areObservableFilesLoaded()).willReturn(true);

		// when:
		subject.loadObservableSysFilesIfNeeded();

		// then:
		verify(systemFilesManager, never()).loadObservableSystemFiles();
		verify(networkCtx, never()).resetThrottlingFromSavedSnapshots(handleThrottling);
		verify(networkCtx, never()).resetMultiplierSourceFromSavedCongestionStarts(feeMultiplierSource);
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
		verify(networkCtx).resetMultiplierSourceFromSavedCongestionStarts(feeMultiplierSource);
		verify(feeMultiplierSource).resetExpectations();
	}

	@Test
	void finalizesContextAsExpected() {
		// when:
		subject.finishIncorporating(TokenMint);

		// then:
		verify(opCounters).countHandled(TokenMint);
		verify(networkCtx).updateSnapshotsFrom(handleThrottling);
		verify(networkCtx).updateCongestionStartsFrom(feeMultiplierSource);
	}

	@Test
	void preparesContextAsExpected() {
		// setup:
		final var accessor = SignedTxnAccessor.uncheckedFrom(Transaction.getDefaultInstance());

		given(networkCtx.consensusTimeOfLastHandledTxn()).willReturn(sometime);

		// when:
		subject.prepareForIncorporating(accessor);

		// then:
		verify(handleThrottling).shouldThrottleTxn(accessor);
		verify(feeMultiplierSource).updateMultiplier(sometime);
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
		given(issInfo.status()).willReturn(IssEventStatus.ONGOING_ISS);
		given(issInfo.consensusTimeOfRecentAlert()).willReturn(Optional.empty());

		// when:
		subject.advanceConsensusClockTo(sometimeSameDay);

		// then:
		verify(issInfo).relax();
	}

	@Test
	void doesNothingWithIssInfoIfNotOngoing() {
		// when:
		subject.advanceConsensusClockTo(sometime);

		// then:
		assertEquals(issResetPeriod, subject.getIssResetPeriod());
		// and:
		verify(issInfo, never()).relax();
	}

	@Test
	void advancesClockAsExpectedWhenFirstTxn() {
		given(networkCtx.consensusTimeOfLastHandledTxn()).willReturn(null);

		// when:
		subject.advanceConsensusClockTo(sometimeNextDay);

		// then:
		verify(networkCtx).setConsensusTimeOfLastHandledTxn(sometimeNextDay);
	}

	@Test
	void advancesClockAsExpectedWhenPassingMidnightAfterBoundaryCheckIntervalElapsedFromLastCheck() {
		// setup:
		var oldMidnightRates = new ExchangeRates(
				1, 12, 1_234_567L,
				1, 15, 2_345_678L);
		var curRates = new ExchangeRates(
				1, 120, 1_234_567L,
				1, 150, 2_345_678L);
		// and:
		subject.setShouldUpdateMidnightRates(shouldUpdateMidnightRates);
		Instant lastBoundaryCheck = sometimeNextDay.minusSeconds(mockDynamicProps.ratesMidnightCheckInterval());

		given(shouldUpdateMidnightRates.test(lastBoundaryCheck, sometimeNextDay)).willReturn(true);
		given(exchange.activeRates()).willReturn(curRates.toGrpc());
		given(networkCtx.lastMidnightBoundaryCheck()).willReturn(lastBoundaryCheck);
		given(networkCtx.midnightRates()).willReturn(oldMidnightRates);
		given(networkCtx.consensusTimeOfLastHandledTxn()).willReturn(sometime);

		// when:
		subject.advanceConsensusClockTo(sometimeNextDay);

		// then:
		verify(networkCtx).setConsensusTimeOfLastHandledTxn(sometimeNextDay);
		verify(networkCtx).setLastMidnightBoundaryCheck(sometimeNextDay);
		assertEquals(oldMidnightRates, curRates);
	}

	@Test
	void doesntUpdateRatesIfTestDoesntSayTooButDoesUpdateLastMidnightCheck() {
		// setup:
		subject.setShouldUpdateMidnightRates(shouldUpdateMidnightRates);

		given(networkCtx.lastMidnightBoundaryCheck())
				.willReturn(sometimeNextDay.minusSeconds((mockDynamicProps.ratesMidnightCheckInterval())));
		given(networkCtx.consensusTimeOfLastHandledTxn()).willReturn(sometime);

		// when:
		subject.advanceConsensusClockTo(sometimeNextDay);

		// then:
		verify(networkCtx).setConsensusTimeOfLastHandledTxn(sometimeNextDay);
		verify(networkCtx).setLastMidnightBoundaryCheck(sometimeNextDay);
	}

	@Test
	void doesntPerformMidnightCheckIfNotInInterval() {
		// setup:
		subject.setShouldUpdateMidnightRates(shouldUpdateMidnightRates);

		given(networkCtx.lastMidnightBoundaryCheck())
				.willReturn(sometimeNextDay.minusSeconds((mockDynamicProps.ratesMidnightCheckInterval() - 1)));
		given(networkCtx.consensusTimeOfLastHandledTxn()).willReturn(sometime);

		// when:
		subject.advanceConsensusClockTo(sometimeNextDay);

		// then:
		verify(networkCtx).setConsensusTimeOfLastHandledTxn(sometimeNextDay);
		verify(networkCtx, never()).setLastMidnightBoundaryCheck(sometimeNextDay);
		verifyNoInteractions(shouldUpdateMidnightRates);
	}

	@Test
	void justUpdatesLastBoundaryCheckWhenItIsNull() {
		// setup:
		subject.setShouldUpdateMidnightRates(shouldUpdateMidnightRates);

		given(networkCtx.lastMidnightBoundaryCheck()).willReturn(null);
		given(networkCtx.consensusTimeOfLastHandledTxn()).willReturn(sometime);

		// when:
		subject.advanceConsensusClockTo(sometimeNextDay);

		// then:
		verify(networkCtx).setConsensusTimeOfLastHandledTxn(sometimeNextDay);
		verify(networkCtx).setLastMidnightBoundaryCheck(sometimeNextDay);
		verifyNoInteractions(shouldUpdateMidnightRates);
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
		final var updateTest = subject.getShouldUpdateMidnightRates();

		// then:
		assertFalse(updateTest.test(now, thenSameDay));
		assertTrue(updateTest.test(now, thenNextDay));
	}
}
