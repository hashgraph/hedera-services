package com.hedera.services.stats;

/*-
 * ‌
 * Hedera Services Node
 * ​
 * Copyright (C) 2018 - 2020 Hedera Hashgraph, LLC
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

import com.hedera.services.context.properties.NodeLocalProperties;
import com.hederahashgraph.api.proto.java.HederaFunctionality;
import com.swirlds.common.Platform;
import com.swirlds.common.StatEntry;
import com.swirlds.platform.StatsSpeedometer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.platform.runner.JUnitPlatform;
import org.junit.runner.RunWith;

import java.util.function.Function;

import static com.hederahashgraph.api.proto.java.HederaFunctionality.CryptoTransfer;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.TokenGetInfo;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.BDDMockito.any;
import static org.mockito.BDDMockito.argThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.verify;
import static org.mockito.Mockito.mock;

@RunWith(JUnitPlatform.class)
class HapiOpSpeedometersTest {
	double halfLife = 10.0;

	Platform platform;
	HapiOpCounters counters;
	SpeedometerFactory factory;
	NodeLocalProperties properties;
	Function<HederaFunctionality, String> statNameFn;

	HapiOpSpeedometers subject;

	@BeforeEach
	public void setup() throws Exception {
		HapiOpSpeedometers.allFunctions = () -> new HederaFunctionality[] {
				CryptoTransfer,
				TokenGetInfo
		};

		counters = mock(HapiOpCounters.class);
		platform = mock(Platform.class);
		factory = mock(SpeedometerFactory.class);
		statNameFn = HederaFunctionality::toString;

		properties = mock(NodeLocalProperties.class);

		subject = new HapiOpSpeedometers(counters, factory, properties, statNameFn);
	}

	@AfterEach
	public void cleanup() {
		HapiOpSpeedometers.allFunctions = HederaFunctionality.class::getEnumConstants;
	}

	@Test
	public void beginsRationally() {
		// expect:
		assertTrue(subject.receivedOps.containsKey(CryptoTransfer));
		assertTrue(subject.submittedTxns.containsKey(CryptoTransfer));
		assertTrue(subject.handledTxns.containsKey(CryptoTransfer));
		assertFalse(subject.answeredQueries.containsKey(CryptoTransfer));
		// and:
		assertTrue(subject.lastReceivedOpsCount.containsKey(CryptoTransfer));
		assertTrue(subject.lastSubmittedTxnsCount.containsKey(CryptoTransfer));
		assertTrue(subject.lastHandledTxnsCount.containsKey(CryptoTransfer));
		assertFalse(subject.lastAnsweredQueriesCount.containsKey(CryptoTransfer));
		// and:
		assertTrue(subject.receivedOps.containsKey(TokenGetInfo));
		assertTrue(subject.answeredQueries.containsKey(TokenGetInfo));
		assertFalse(subject.submittedTxns.containsKey(TokenGetInfo));
		assertFalse(subject.handledTxns.containsKey(TokenGetInfo));
		// and:
		assertTrue(subject.lastReceivedOpsCount.containsKey(TokenGetInfo));
		assertTrue(subject.lastAnsweredQueriesCount.containsKey(TokenGetInfo));
		assertFalse(subject.lastSubmittedTxnsCount.containsKey(TokenGetInfo));
		assertFalse(subject.lastHandledTxnsCount.containsKey(TokenGetInfo));
	}

	@Test
	public void registersExpectedStatEntries() {
		// setup:
		StatEntry transferRcv = mock(StatEntry.class);
		StatEntry transferSub = mock(StatEntry.class);
		StatEntry transferHdl = mock(StatEntry.class);
		StatEntry tokenInfoRcv = mock(StatEntry.class);
		StatEntry tokenInfoAns = mock(StatEntry.class);
		// and:
		var xferRcvName = String.format(ServicesStatsConfig.SPEEDOMETER_RECEIVED_NAME_TPL, "CryptoTransfer");
		var xferSubName = String.format(ServicesStatsConfig.SPEEDOMETER_SUBMITTED_NAME_TPL, "CryptoTransfer");
		var xferHdlName = String.format(ServicesStatsConfig.SPEEDOMETER_HANDLED_NAME_TPL, "CryptoTransfer");
		// and:
		var xferRcvDesc = String.format(ServicesStatsConfig.SPEEDOMETER_RECEIVED_DESC_TPL, "CryptoTransfer");
		var xferSubDesc = String.format(ServicesStatsConfig.SPEEDOMETER_SUBMITTED_DESC_TPL, "CryptoTransfer");
		var xferHdlDesc = String.format(ServicesStatsConfig.SPEEDOMETER_HANDLED_DESC_TPL, "CryptoTransfer");
		// and:
		var infoRcvName = String.format(ServicesStatsConfig.SPEEDOMETER_RECEIVED_NAME_TPL, "TokenGetInfo");
		var infoAnsName = String.format(ServicesStatsConfig.SPEEDOMETER_ANSWERED_NAME_TPL, "TokenGetInfo");
		// and:
		var infoRcvDesc = String.format(ServicesStatsConfig.SPEEDOMETER_RECEIVED_DESC_TPL, "TokenGetInfo");
		var infoAnsDesc = String.format(ServicesStatsConfig.SPEEDOMETER_ANSWERED_DESC_TPL, "TokenGetInfo");

		given(factory.from(
				argThat(xferRcvName::equals),
				argThat(xferRcvDesc::equals),
				any())).willReturn(transferRcv);
		given(factory.from(
				argThat(xferSubName::equals),
				argThat(xferSubDesc::equals),
				any())).willReturn(transferSub);
		given(factory.from(
				argThat(xferHdlName::equals),
				argThat(xferHdlDesc::equals),
				any())).willReturn(transferHdl);
		// and:
		given(factory.from(
				argThat(infoRcvName::equals),
				argThat(infoRcvDesc::equals),
				any())).willReturn(tokenInfoRcv);
		given(factory.from(
				argThat(infoAnsName::equals),
				argThat(infoAnsDesc::equals),
				any())).willReturn(tokenInfoAns);

		// when:
		subject.registerWith(platform);

		// then:
		verify(platform).addAppStatEntry(transferRcv);
		verify(platform).addAppStatEntry(transferSub);
		verify(platform).addAppStatEntry(transferHdl);
		verify(platform).addAppStatEntry(tokenInfoRcv);
		verify(platform).addAppStatEntry(tokenInfoAns);
	}

	@Test
	void updatesSpeedometersAsExpected() {
		// setup:
		subject.lastReceivedOpsCount.put(CryptoTransfer, 1L);
		subject.lastSubmittedTxnsCount.put(CryptoTransfer, 2L);
		subject.lastHandledTxnsCount.put(CryptoTransfer, 3L);
		// and:
		subject.lastReceivedOpsCount.put(TokenGetInfo, 4L);
		subject.lastAnsweredQueriesCount.put(TokenGetInfo, 5L);
		// and:
		StatsSpeedometer xferReceived = mock(StatsSpeedometer.class);
		StatsSpeedometer xferSubmitted = mock(StatsSpeedometer.class);
		StatsSpeedometer xferHandled = mock(StatsSpeedometer.class);
		StatsSpeedometer infoReceived = mock(StatsSpeedometer.class);
		StatsSpeedometer infoAnswered = mock(StatsSpeedometer.class);

		given(counters.receivedSoFar(CryptoTransfer)).willReturn(2L);
		given(counters.submittedSoFar(CryptoTransfer)).willReturn(4L);
		given(counters.handledSoFar(CryptoTransfer)).willReturn(6L);
		given(counters.receivedSoFar(TokenGetInfo)).willReturn(8L);
		given(counters.answeredSoFar(TokenGetInfo)).willReturn(10L);
		// and:
		subject.receivedOps.put(CryptoTransfer, xferReceived);
		subject.submittedTxns.put(CryptoTransfer, xferSubmitted);
		subject.handledTxns.put(CryptoTransfer, xferHandled);
		subject.receivedOps.put(TokenGetInfo, infoReceived);
		subject.answeredQueries.put(TokenGetInfo, infoAnswered);

		// when:
		subject.updateAll();

		// then:
		assertEquals(2L, subject.lastReceivedOpsCount.get(CryptoTransfer));
		assertEquals(4L, subject.lastSubmittedTxnsCount.get(CryptoTransfer));
		assertEquals(6L, subject.lastHandledTxnsCount.get(CryptoTransfer));
		assertEquals(8L, subject.lastReceivedOpsCount.get(TokenGetInfo));
		assertEquals(10L, subject.lastAnsweredQueriesCount.get(TokenGetInfo));
		// and:
		verify(xferReceived).update(1);
		verify(xferSubmitted).update(2);
		verify(xferHandled).update(3);
		verify(infoReceived).update(4);
		verify(infoAnswered).update(5);
	}
}