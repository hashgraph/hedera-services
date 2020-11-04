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

import com.hedera.services.context.TransactionContext;
import com.hedera.services.utils.PlatformTxnAccessor;
import com.hederahashgraph.api.proto.java.HederaFunctionality;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.swirlds.common.Platform;
import com.swirlds.common.StatEntry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.platform.runner.JUnitPlatform;
import org.junit.runner.RunWith;

import java.util.function.Function;

import static com.hederahashgraph.api.proto.java.HederaFunctionality.ConsensusSubmitMessage;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.CryptoTransfer;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.NONE;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.TokenGetInfo;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.BDDMockito.any;
import static org.mockito.BDDMockito.argThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.verify;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;

@RunWith(JUnitPlatform.class)
class HapiOpCountersTest {
	Platform platform;
	CounterFactory factory;
	MiscRunningAvgs runningAvgs;
	TransactionContext txnCtx;
	Function<HederaFunctionality, String> statNameFn;

	HapiOpCounters subject;

	@BeforeEach
	public void setup() throws Exception {
		HapiOpCounters.allFunctions = () -> new HederaFunctionality[] {
				CryptoTransfer,
				TokenGetInfo,
				ConsensusSubmitMessage,
				NONE
		};

		txnCtx = mock(TransactionContext.class);
		platform = mock(Platform.class);
		factory = mock(CounterFactory.class);
		statNameFn = HederaFunctionality::toString;
		runningAvgs = mock(MiscRunningAvgs.class);

		subject = new HapiOpCounters(factory, runningAvgs, txnCtx, statNameFn);
	}

	@AfterEach
	public void cleanup() {
		HapiOpCounters.allFunctions = HederaFunctionality.class::getEnumConstants;
	}

	@Test
	public void beginsRationally() {
		// expect:
		assertTrue(subject.receivedOps.containsKey(CryptoTransfer));
		assertTrue(subject.submittedTxns.containsKey(CryptoTransfer));
		assertTrue(subject.handledTxns.containsKey(CryptoTransfer));
		assertFalse(subject.answeredQueries.containsKey(CryptoTransfer));
		// and:
		assertTrue(subject.receivedOps.containsKey(TokenGetInfo));
		assertTrue(subject.answeredQueries.containsKey(TokenGetInfo));
		assertFalse(subject.submittedTxns.containsKey(TokenGetInfo));
		assertFalse(subject.handledTxns.containsKey(TokenGetInfo));
		// and:
		assertFalse(subject.receivedOps.containsKey(NONE));
		assertFalse(subject.submittedTxns.containsKey(NONE));
		assertFalse(subject.answeredQueries.containsKey(NONE));
		assertFalse(subject.handledTxns.containsKey(NONE));
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
		var xferRcvName = String.format(ServicesStatsConfig.COUNTER_RECEIVED_NAME_TPL, "CryptoTransfer");
		var xferSubName = String.format(ServicesStatsConfig.COUNTER_SUBMITTED_NAME_TPL, "CryptoTransfer");
		var xferHdlName = String.format(ServicesStatsConfig.COUNTER_HANDLED_NAME_TPL, "CryptoTransfer");
		// and:
		var xferRcvDesc = String.format(ServicesStatsConfig.COUNTER_RECEIVED_DESC_TPL, "CryptoTransfer");
		var xferSubDesc = String.format(ServicesStatsConfig.COUNTER_SUBMITTED_DESC_TPL, "CryptoTransfer");
		var xferHdlDesc = String.format(ServicesStatsConfig.COUNTER_HANDLED_DESC_TPL, "CryptoTransfer");
		// and:
		var infoRcvName = String.format(ServicesStatsConfig.COUNTER_RECEIVED_NAME_TPL, "TokenGetInfo");
		var infoAnsName = String.format(ServicesStatsConfig.COUNTER_ANSWERED_NAME_TPL, "TokenGetInfo");
		// and:
		var infoRcvDesc = String.format(ServicesStatsConfig.COUNTER_RECEIVED_DESC_TPL, "TokenGetInfo");
		var infoAnsDesc = String.format(ServicesStatsConfig.COUNTER_ANSWERED_DESC_TPL, "TokenGetInfo");

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
	public void updatesAvgSubmitMessageHdlSizeForHandled() {
		// setup:
		int expectedSize = 12345;
		TransactionBody txn = mock(TransactionBody.class);
		PlatformTxnAccessor accessor = mock(PlatformTxnAccessor.class);

		given(txn.getSerializedSize()).willReturn(expectedSize);
		given(accessor.getTxn()).willReturn(txn);
		given(txnCtx.accessor()).willReturn(accessor);

		// when:
		subject.countHandled(ConsensusSubmitMessage);

		// then
		verify(runningAvgs).recordHandledSubmitMessageSize(expectedSize);
	}

	@Test
	public void doesntUpdateAvgSubmitMessageHdlSizeForCountReceivedOrSubmitted() {
		// setup:
		int expectedSize = 12345;
		TransactionBody txn = mock(TransactionBody.class);
		PlatformTxnAccessor accessor = mock(PlatformTxnAccessor.class);

		given(txn.getSerializedSize()).willReturn(expectedSize);
		given(accessor.getTxn()).willReturn(txn);
		given(txnCtx.accessor()).willReturn(accessor);

		// when:
		subject.countReceived(ConsensusSubmitMessage);
		subject.countSubmitted(ConsensusSubmitMessage);

		// then
		verify(runningAvgs, never()).recordHandledSubmitMessageSize(expectedSize);
	}

	@Test
	public void updatesExpectedEntries() {
		// when:
		subject.countReceived(CryptoTransfer);
		subject.countReceived(CryptoTransfer);
		subject.countReceived(CryptoTransfer);
		subject.countSubmitted(CryptoTransfer);
		subject.countSubmitted(CryptoTransfer);
		subject.countHandled(CryptoTransfer);
		// and:
		subject.countReceived(TokenGetInfo);
		subject.countReceived(TokenGetInfo);
		subject.countReceived(TokenGetInfo);
		subject.countAnswered(TokenGetInfo);
		subject.countAnswered(TokenGetInfo);

		// then
		assertEquals(3L, subject.receivedSoFar(CryptoTransfer));
		assertEquals(2L, subject.submittedSoFar(CryptoTransfer));
		assertEquals(1L, subject.handledSoFar(CryptoTransfer));
		// and:
		assertEquals(3L, subject.receivedSoFar(TokenGetInfo));
		assertEquals(2L, subject.answeredSoFar(TokenGetInfo));
	}

	@Test
	public void ignoredOpsAreNoops() {
		// expect:
		assertDoesNotThrow(() -> subject.countReceived(NONE));
		assertDoesNotThrow(() -> subject.countSubmitted(NONE));
		assertDoesNotThrow(() -> subject.countHandled(NONE));
		assertDoesNotThrow(() -> subject.countAnswered(NONE));
		// and:
		assertEquals(0L, subject.receivedSoFar(NONE));
		assertEquals(0L, subject.submittedSoFar(NONE));
		assertEquals(0L, subject.handledSoFar(NONE));
		assertEquals(0L, subject.answeredSoFar(NONE));
	}
}