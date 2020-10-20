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
		var xferRcvName = String.format(StatsNamingConventions.COUNTER_RECEIVED_NAME_TPL, "CryptoTransfer") + "/sec";
		var xferSubName = String.format(StatsNamingConventions.COUNTER_SUBMITTED_NAME_TPL, "CryptoTransfer") + "/sec";
		var xferHdlName = String.format(StatsNamingConventions.COUNTER_HANDLED_NAME_TPL, "CryptoTransfer") + "/sec";
		// and:
		var infoRcvName = String.format(StatsNamingConventions.COUNTER_RECEIVED_NAME_TPL, "TokenGetInfo") + "/sec";
		var infoAnsName = String.format(StatsNamingConventions.COUNTER_ANSWERED_NAME_TPL, "TokenGetInfo") + "/sec";

		given(factory.from(
				argThat(xferRcvName::equals),
				argThat("number of CryptoTransfer received per second"::equals),
				any())).willReturn(transferRcv);
		given(factory.from(
				argThat(xferSubName::equals),
				argThat("number of CryptoTransfer submitted per second"::equals),
				any())).willReturn(transferSub);
		given(factory.from(
				argThat(xferHdlName::equals),
				argThat("number of CryptoTransfer handled per second"::equals),
				any())).willReturn(transferHdl);
		// and:
		given(factory.from(
				argThat(infoRcvName::equals),
				argThat("number of TokenGetInfo received per second"::equals),
				any())).willReturn(tokenInfoRcv);
		given(factory.from(
				argThat(infoAnsName::equals),
				argThat("number of TokenGetInfo answered per second"::equals),
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
}