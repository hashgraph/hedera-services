package com.hedera.services.stats;

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

import com.hedera.services.context.TransactionContext;
import com.hedera.services.utils.accessors.PlatformTxnAccessor;
import com.hedera.services.utils.accessors.SignedTxnAccessor;
import com.hederahashgraph.api.proto.java.HederaFunctionality;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.swirlds.common.system.Platform;
import com.swirlds.common.statistics.StatEntry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

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

class HapiOpCountersTest {
	private Platform platform;
	private CounterFactory factory;
	private MiscRunningAvgs runningAvgs;
	private TransactionContext txnCtx;
	private Function<HederaFunctionality, String> statNameFn;

	private HapiOpCounters subject;

	@BeforeEach
	void setup() {
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
	void cleanup() {
		HapiOpCounters.allFunctions = HederaFunctionality.class::getEnumConstants;
	}

	@Test
	void beginsRationally() {
		assertTrue(subject.receivedOps.containsKey(CryptoTransfer));
		assertTrue(subject.submittedTxns.containsKey(CryptoTransfer));
		assertTrue(subject.handledTxns.containsKey(CryptoTransfer));
		assertEquals(0, subject.receivedDeprecatedTxns.get());
		assertFalse(subject.answeredQueries.containsKey(CryptoTransfer));

		assertTrue(subject.receivedOps.containsKey(TokenGetInfo));
		assertTrue(subject.answeredQueries.containsKey(TokenGetInfo));
		assertFalse(subject.submittedTxns.containsKey(TokenGetInfo));
		assertFalse(subject.handledTxns.containsKey(TokenGetInfo));

		assertFalse(subject.receivedOps.containsKey(NONE));
		assertFalse(subject.submittedTxns.containsKey(NONE));
		assertFalse(subject.answeredQueries.containsKey(NONE));
		assertFalse(subject.handledTxns.containsKey(NONE));
	}

	@Test
	void registersExpectedStatEntries() {
		final var transferRcv = mock(StatEntry.class);
		final var transferSub = mock(StatEntry.class);
		final var transferHdl = mock(StatEntry.class);
		final var transferDeprecatedRcv = mock(StatEntry.class);
		final var tokenInfoRcv = mock(StatEntry.class);
		final var tokenInfoAns = mock(StatEntry.class);
		final var xferRcvName = String.format(ServicesStatsConfig.COUNTER_RECEIVED_NAME_TPL, "CryptoTransfer");
		final var xferSubName = String.format(ServicesStatsConfig.COUNTER_SUBMITTED_NAME_TPL, "CryptoTransfer");
		final var xferHdlName = String.format(ServicesStatsConfig.COUNTER_HANDLED_NAME_TPL, "CryptoTransfer");
		final var xferRcvDeprecatedName = String.format(ServicesStatsConfig.COUNTER_RECEIVED_DEPRECATED_NAME_TPL);
		final var xferRcvDesc = String.format(ServicesStatsConfig.COUNTER_RECEIVED_DESC_TPL, "CryptoTransfer");
		final var xferSubDesc = String.format(ServicesStatsConfig.COUNTER_SUBMITTED_DESC_TPL, "CryptoTransfer");
		final var xferHdlDesc = String.format(ServicesStatsConfig.COUNTER_HANDLED_DESC_TPL, "CryptoTransfer");
		final var xferRcvDeprecatedDesc = String.format(ServicesStatsConfig.COUNTER_RECEIVED_DEPRECATED_DESC_TPL);
		final var infoRcvName = String.format(ServicesStatsConfig.COUNTER_RECEIVED_NAME_TPL, "TokenGetInfo");
		final var infoAnsName = String.format(ServicesStatsConfig.COUNTER_ANSWERED_NAME_TPL, "TokenGetInfo");
		final var infoRcvDesc = String.format(ServicesStatsConfig.COUNTER_RECEIVED_DESC_TPL, "TokenGetInfo");
		final var infoAnsDesc = String.format(ServicesStatsConfig.COUNTER_ANSWERED_DESC_TPL, "TokenGetInfo");
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
		given(factory.from(
				argThat(xferRcvDeprecatedName::equals),
				argThat(xferRcvDeprecatedDesc::equals),
				any())).willReturn(transferDeprecatedRcv);
		given(factory.from(
				argThat(infoRcvName::equals),
				argThat(infoRcvDesc::equals),
				any())).willReturn(tokenInfoRcv);
		given(factory.from(
				argThat(infoAnsName::equals),
				argThat(infoAnsDesc::equals),
				any())).willReturn(tokenInfoAns);

		subject.registerWith(platform);

		verify(platform).addAppStatEntry(transferRcv);
		verify(platform).addAppStatEntry(transferSub);
		verify(platform).addAppStatEntry(transferHdl);
		verify(platform).addAppStatEntry(transferDeprecatedRcv);
		verify(platform).addAppStatEntry(tokenInfoRcv);
		verify(platform).addAppStatEntry(tokenInfoAns);
	}

	@Test
	void updatesAvgSubmitMessageHdlSizeForHandled() {
		final var expectedSize = 12345;
		final var txn = mock(TransactionBody.class);
		final var accessor = mock(SignedTxnAccessor.class);
		given(txn.getSerializedSize()).willReturn(expectedSize);
		given(accessor.getTxn()).willReturn(txn);
		given(txnCtx.accessor()).willReturn(accessor);

		subject.countHandled(ConsensusSubmitMessage);

		verify(runningAvgs).recordHandledSubmitMessageSize(expectedSize);
	}

	@Test
	void doesntUpdateAvgSubmitMessageHdlSizeForCountReceivedOrSubmitted() {
		final var expectedSize = 12345;
		final var txn = mock(TransactionBody.class);
		final var accessor = mock(PlatformTxnAccessor.class);
		given(txn.getSerializedSize()).willReturn(expectedSize);
		given(accessor.getTxn()).willReturn(txn);
		given(txnCtx.swirldsTxnAccessor()).willReturn(accessor);

		subject.countReceived(ConsensusSubmitMessage);
		subject.countSubmitted(ConsensusSubmitMessage);

		verify(runningAvgs, never()).recordHandledSubmitMessageSize(expectedSize);
	}

	@Test
	void updatesExpectedEntries() {
		subject.countReceived(CryptoTransfer);
		subject.countReceived(CryptoTransfer);
		subject.countReceived(CryptoTransfer);
		subject.countSubmitted(CryptoTransfer);
		subject.countSubmitted(CryptoTransfer);
		subject.countHandled(CryptoTransfer);
		subject.countDeprecatedTxnReceived();
		subject.countReceived(TokenGetInfo);
		subject.countReceived(TokenGetInfo);
		subject.countReceived(TokenGetInfo);
		subject.countAnswered(TokenGetInfo);
		subject.countAnswered(TokenGetInfo);

		assertEquals(3L, subject.receivedSoFar(CryptoTransfer));
		assertEquals(2L, subject.submittedSoFar(CryptoTransfer));
		assertEquals(1L, subject.handledSoFar(CryptoTransfer));
		assertEquals(1L, subject.receivedDeprecatedTxnSoFar());
		assertEquals(3L, subject.receivedSoFar(TokenGetInfo));
		assertEquals(2L, subject.answeredSoFar(TokenGetInfo));
	}

	@Test
	void ignoredOpsAreNoops() {
		assertDoesNotThrow(() -> subject.countReceived(NONE));
		assertDoesNotThrow(() -> subject.countSubmitted(NONE));
		assertDoesNotThrow(() -> subject.countHandled(NONE));
		assertDoesNotThrow(() -> subject.countAnswered(NONE));

		assertEquals(0L, subject.receivedSoFar(NONE));
		assertEquals(0L, subject.submittedSoFar(NONE));
		assertEquals(0L, subject.handledSoFar(NONE));
		assertEquals(0L, subject.answeredSoFar(NONE));
	}

	@Test
	void deprecatedTxnsCountIncrementsByOne() {
		subject.countDeprecatedTxnReceived();
		assertEquals(1L, subject.receivedDeprecatedTxnSoFar());
	}
}
