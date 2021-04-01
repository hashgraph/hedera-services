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

import com.hedera.services.context.domain.trackers.IssEventInfo;
import com.hedera.services.context.domain.trackers.IssEventStatus;
import com.hedera.services.context.properties.PropertySource;
import com.hedera.services.fees.FeeMultiplierSource;
import com.hedera.services.fees.HbarCentExchange;
import com.hedera.services.state.initialization.SystemFilesManager;
import com.hedera.services.state.merkle.MerkleNetworkContext;
import com.hedera.services.state.submerkle.ExchangeRates;
import com.hedera.services.stats.HapiOpCounters;
import com.hedera.services.throttling.FunctionalityThrottling;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;

import static com.hederahashgraph.api.proto.java.HederaFunctionality.TokenMint;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.BDDMockito.*;

@ExtendWith(MockitoExtension.class)
class NetworkCtxManagerTest {
	int issResetPeriod = 5;
	Instant sometime = Instant.ofEpochSecond(1_234_567L);
	Instant sometimeSameDay = sometime.plusSeconds(issResetPeriod + 1L);
	Instant sometimeNextDay = sometime.plusSeconds(86_400L);

	@Mock
	IssEventInfo issInfo;
	@Mock
	PropertySource properties;
	@Mock
	HapiOpCounters opCounters;
	@Mock
	HbarCentExchange exchange;
	@Mock
	FeeMultiplierSource feeMultiplierSource;
	@Mock
	SystemFilesManager systemFilesManager;
	@Mock
	MerkleNetworkContext networkCtx;
	@Mock
	FunctionalityThrottling handleThrottling;

	NetworkCtxManager subject;

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
				networkCtx,
				handleThrottling);
	}

	@Test
	void doesntInitObservableSysFilesIfAlreadyLoaded() {
		given(systemFilesManager.areObservableFilesLoaded()).willReturn(true);

		// when:
		subject.initObservableSysFiles();

		// then:
		verify(systemFilesManager, never()).loadObservableSystemFiles();
		verify(networkCtx, never()).resetWithSavedSnapshots(handleThrottling);
		verify(networkCtx, never()).updateWithSavedCongestionStarts(feeMultiplierSource);
		verify(feeMultiplierSource, never()).resetExpectations();
	}

	@Test
	void initsSystemFilesAsExpected() {
		given(systemFilesManager.areObservableFilesLoaded()).willReturn(false);

		// when:
		subject.initObservableSysFiles();

		// then:
		verify(systemFilesManager).loadObservableSystemFiles();
		verify(networkCtx).resetWithSavedSnapshots(handleThrottling);
		verify(networkCtx).updateWithSavedCongestionStarts(feeMultiplierSource);
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
		given(networkCtx.consensusTimeOfLastHandledTxn()).willReturn(sometime);

		// when:
		subject.prepareForIncorporating(TokenMint);

		// then:
		verify(handleThrottling).shouldThrottle(TokenMint);
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
	void advancesClockAsExpectedWhenPassingMidnight() {
		// setup:
		var oldMidnightRates = new ExchangeRates(
				1, 12, 1_234_567L,
				1, 15, 2_345_678L);
		var curRates = new ExchangeRates(
				1, 120, 1_234_567L,
				1, 150, 2_345_678L);

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
	void advancesClockAsExpectedWhenNotPassingMidnight() {
		given(networkCtx.consensusTimeOfLastHandledTxn()).willReturn(sometime);

		// when:
		subject.advanceConsensusClockTo(sometimeSameDay);

		// then:
		verify(networkCtx, never()).midnightRates();
		verify(networkCtx).setConsensusTimeOfLastHandledTxn(sometimeSameDay);
	}
}
