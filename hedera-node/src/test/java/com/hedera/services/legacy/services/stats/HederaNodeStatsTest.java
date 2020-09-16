package com.hedera.services.legacy.services.stats;

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

import com.hedera.services.grpc.controllers.ConsensusController;
import com.hedera.services.grpc.controllers.CryptoController;
import com.hedera.services.grpc.controllers.FileController;
import com.hedera.test.utils.TxnUtils;
import com.hederahashgraph.api.proto.java.ConsensusSubmitMessageTransactionBody;
import com.hederahashgraph.api.proto.java.CryptoTransferTransactionBody;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.swirlds.common.Platform;
import com.swirlds.common.StatEntry;
import org.junit.Assert;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.platform.runner.JUnitPlatform;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import org.apache.logging.log4j.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

@RunWith(JUnitPlatform.class)
public class HederaNodeStatsTest {

	@Mock
	private Platform platform;
	@Mock
	private Logger log;

	private HederaNodeStats stats;

	@BeforeEach
	public void setUp() {
		MockitoAnnotations.initMocks(this);
		stats = new HederaNodeStats(platform, 0, log);
		verify(platform, times(213)).addAppStatEntry(any(StatEntry.class));
		verify(platform, times(1)).appStatInit();
	}

	@AfterEach
	public void tearDown() {
		stats = null;
	}

	@Test
	public void shouldThrowAnExceptionOnGettingUnsupportedCountStat() {
		try {
			stats.getCountStat("unsupported", HederaNodeStats.RECEIVED_SUFFIX);
			Assert.fail("No exception was thrown!");
		} catch (Throwable e) {
			if (e instanceof AssertionError) {
				throw e;
			}
			assertTrue(e instanceof IllegalArgumentException,
					"An exception other than IllegalArgumentException was thrown!");
			assertEquals("Count stat for unsupportedRcv is not supported", e.getMessage());
		}
	}

	@Test
	public void shouldLogAnErrorOnUpdatingUnsupportedCountStat() {
		stats.cryptoTransactionReceived("unsupportedTransaction");
		verify(log, times(1)).debug(
				"Stat for {} is not supported", "unsupportedTransaction");
	}

	@Test
	public void shouldIncreaseCryptoTransactionsReceived() {
		String statToTest = CryptoController.CRYPTO_CREATE_METRIC;
		assertEquals(0, stats.getCountStat(statToTest, HederaNodeStats.RECEIVED_SUFFIX));
		for (int i = 1; i <= 10; i++) {
			stats.cryptoTransactionReceived(statToTest);
			assertEquals(i, stats.getCountStat(statToTest, HederaNodeStats.RECEIVED_SUFFIX));
		}
	}

	@Test
	public void shouldIncreaseCryptoTransactionsSubmitted() {
		String statToTest = CryptoController.DELETE_LIVE_HASH_METRIC;
		assertEquals(0, stats.getCountStat(statToTest, HederaNodeStats.SUBMITTED_SUFFIX));
		for (int i = 1; i <= 10; i++) {
			stats.cryptoTransactionSubmitted(statToTest);
			assertEquals(i, stats.getCountStat(statToTest, HederaNodeStats.SUBMITTED_SUFFIX));
		}
	}

	@Test
	public void shouldIncreaseCryptoQueriesReceived() {
		String statToTest = CryptoController.GET_RECEIPT_METRIC;
		assertEquals(0, stats.getCountStat(statToTest, HederaNodeStats.RECEIVED_SUFFIX));
		for (int i = 1; i <= 10; i++) {
			stats.cryptoQueryReceived(statToTest);
			assertEquals(i, stats.getCountStat(statToTest, HederaNodeStats.RECEIVED_SUFFIX));
		}
	}

	@Test
	public void shouldIncreaseCryptoQueriesSubmitted() {
		String statToTest = CryptoController.GET_RECORD_METRIC;
		assertEquals(0, stats.getCountStat(statToTest, HederaNodeStats.SUBMITTED_SUFFIX));
		for (int i = 1; i <= 10; i++) {
			stats.cryptoQuerySubmitted(statToTest);
			assertEquals(i, stats.getCountStat(statToTest, HederaNodeStats.SUBMITTED_SUFFIX));
		}
	}

	@Test
	public void shouldIncreaseFileTransactionsReceived() {
		String statToTest = FileController.UPDATE_FILE_METRIC;
		assertEquals(0, stats.getCountStat(statToTest, HederaNodeStats.RECEIVED_SUFFIX));
		for (int i = 1; i <= 10; i++) {
			stats.fileTransactionReceived(statToTest);
			assertEquals(i, stats.getCountStat(statToTest, HederaNodeStats.RECEIVED_SUFFIX));
		}
	}

	@Test
	public void shouldIncreaseFileTransactionsSubmitted() {
		String statToTest = FileController.FILE_APPEND_METRIC;
		assertEquals(0, stats.getCountStat(statToTest, HederaNodeStats.SUBMITTED_SUFFIX));
		for (int i = 1; i <= 10; i++) {
			stats.fileTransactionSubmitted(statToTest);
			assertEquals(i, stats.getCountStat(statToTest, HederaNodeStats.SUBMITTED_SUFFIX));
		}
	}

	@Test
	public void shouldIncreaseFileQueriesReceived() {
		String statToTest = FileController.GET_FILE_CONTENT_METRIC;
		assertEquals(0, stats.getCountStat(statToTest, HederaNodeStats.RECEIVED_SUFFIX));
		for (int i = 1; i <= 10; i++) {
			stats.fileQueryReceived(statToTest);
			assertEquals(i, stats.getCountStat(statToTest, HederaNodeStats.RECEIVED_SUFFIX));
		}
	}

	@Test
	public void shouldIncreaseFileQueriesSubmitted() {
		String statToTest = FileController.GET_FILE_INFO_METRIC;
		assertEquals(0, stats.getCountStat(statToTest, HederaNodeStats.SUBMITTED_SUFFIX));
		for (int i = 1; i <= 10; i++) {
			stats.fileQuerySubmitted(statToTest);
			assertEquals(i, stats.getCountStat(statToTest, HederaNodeStats.SUBMITTED_SUFFIX));
		}
	}

	@Test
	public void shouldIncreaseSmartContractTransactionsReceived() {
		String statToTest = "createContract";
		assertEquals(0, stats.getCountStat(statToTest, HederaNodeStats.RECEIVED_SUFFIX));
		for (int i = 1; i <= 10; i++) {
			stats.smartContractTransactionReceived(statToTest);
			assertEquals(i, stats.getCountStat(statToTest, HederaNodeStats.RECEIVED_SUFFIX));
		}
	}

	@Test
	public void shouldIncreaseSmartContractTransactionsSubmitted() {
		String statToTest = "updateContract";
		assertEquals(0, stats.getCountStat(statToTest, HederaNodeStats.SUBMITTED_SUFFIX));
		for (int i = 1; i <= 10; i++) {
			stats.smartContractTransactionSubmitted(statToTest);
			assertEquals(i, stats.getCountStat(statToTest, HederaNodeStats.SUBMITTED_SUFFIX));
		}
	}

	@Test
	public void shouldIncreaseSmartContractQueriesReceived() {
		String statToTest = "getContractInfo";
		assertEquals(0, stats.getCountStat(statToTest, HederaNodeStats.RECEIVED_SUFFIX));
		for (int i = 1; i <= 10; i++) {
			stats.smartContractQueryReceived(statToTest);
			assertEquals(i, stats.getCountStat(statToTest, HederaNodeStats.RECEIVED_SUFFIX));
		}
	}

	@Test
	public void shouldIncreaseSmartContractQueriesSubmitted() {
		String statToTest = "getTxRecordByContractID";
		assertEquals(0, stats.getCountStat(statToTest, HederaNodeStats.SUBMITTED_SUFFIX));
		for (int i = 1; i <= 10; i++) {
			stats.smartContractQuerySubmitted(statToTest);
			assertEquals(i, stats.getCountStat(statToTest, HederaNodeStats.SUBMITTED_SUFFIX));
		}
	}

	@Test
	public void shouldIncreaseTransactionHandled() {
		var cryptoTransferTxnBody = CryptoTransferTransactionBody.newBuilder().build();
		var transaction = TransactionBody.newBuilder().setCryptoTransfer(cryptoTransferTxnBody).build();
		String statToTest = CryptoController.CRYPTO_TRANSFER_METRIC;
		assertEquals(0, stats.getCountStat(statToTest, HederaNodeStats.HANDLED_SUFFIX));
		for (int i = 1; i <= 10; i++) {
			stats.transactionHandled(transaction);
			assertEquals(i, stats.getCountStat(statToTest, HederaNodeStats.HANDLED_SUFFIX));
			assertEquals(0.0, stats.getAvgHdlSubMsgSize());
		}
	}

	@Test
	public void shouldThrowAnExceptionOnGettingUnsupportedSpeedometerStat() {
		try {
			stats.getSpeedometerStat("unsupported", HederaNodeStats.SUBMITTED_SUFFIX);
			Assert.fail("No exception was thrown!");
		} catch (Throwable e) {
			if (e instanceof AssertionError) {
				throw e;
			}
			assertTrue(e instanceof IllegalArgumentException,
					"An exception other than IllegalArgumentException was thrown!");
			assertEquals("Speedometer stat for unsupportedSub is not supported", e.getMessage());
		}
	}

	@Test
	public void shouldInitializeSpeedometerStats() {
		String statToTest = "cryptoTransfer";
		assertEquals(0.0, stats.getSpeedometerStat(statToTest, HederaNodeStats.RECEIVED_SUFFIX));
		assertEquals(0.0, stats.getSpeedometerStat(statToTest, HederaNodeStats.SUBMITTED_SUFFIX));
		assertEquals(0.0, stats.getSpeedometerStat(statToTest, HederaNodeStats.HANDLED_SUFFIX));
	}

	@Test
	public void shouldUpdateCryptoTransferSpeedometer() throws InterruptedException {
		String statToTest = CryptoController.CRYPTO_TRANSFER_METRIC;
		assertEquals(0.0, stats.getSpeedometerStat(statToTest, HederaNodeStats.RECEIVED_SUFFIX));
		for (int i = 1; i <= 10; i++) {
			stats.cryptoTransactionReceived(statToTest);
		}
		Thread.sleep(HederaNodeStats.UPDATE_PERIOD + 500);
		assertTrue(stats.getSpeedometerStat(statToTest, HederaNodeStats.RECEIVED_SUFFIX) > 0.0);
	}

	@Test
	public void shouldUpdateOtherSpeedometers() throws InterruptedException {
		String cryptoQuerySubStat = CryptoController.GET_ACCOUNT_INFO_METRIC;
		assertEquals(0.0, stats.getSpeedometerStat(cryptoQuerySubStat, HederaNodeStats.SUBMITTED_SUFFIX));
		for (int i = 1; i <= 10; i++) {
			stats.cryptoQuerySubmitted(cryptoQuerySubStat);
		}
		String fileTransactionHdlStat = FileController.CREATE_FILE_METRIC;
		assertEquals(0.0, stats.getSpeedometerStat(fileTransactionHdlStat, HederaNodeStats.HANDLED_SUFFIX));
		for (int i = 1; i <= 20; i++) {
			stats.transactionHandled(fileTransactionHdlStat);
		}
		String smartContractTransactionRcvStat = "contractCallMethod";
		assertEquals(0.0, stats.getSpeedometerStat(smartContractTransactionRcvStat, HederaNodeStats.RECEIVED_SUFFIX));
		for (int i = 1; i <= 30; i++) {
			stats.smartContractTransactionReceived(smartContractTransactionRcvStat);
		}
		Thread.sleep(HederaNodeStats.UPDATE_PERIOD + 500);
		assertTrue(stats.getSpeedometerStat(cryptoQuerySubStat, HederaNodeStats.SUBMITTED_SUFFIX) > 0.0);
		assertTrue(stats.getSpeedometerStat(fileTransactionHdlStat, HederaNodeStats.HANDLED_SUFFIX) > 0.0);
		assertTrue(stats.getSpeedometerStat(smartContractTransactionRcvStat, HederaNodeStats.RECEIVED_SUFFIX) > 0.0);
		assertEquals(0.0, stats.getAvgHdlSubMsgSize());
	}

	@Test
	public void shouldUpdateAvgHdlSubMsgSize() throws InterruptedException {
		var subMsgTxnBody = ConsensusSubmitMessageTransactionBody.newBuilder().setMessage(
				TxnUtils.randomUtf8ByteString(5120)
		).build();
		var transaction = TransactionBody.newBuilder().setConsensusSubmitMessage(subMsgTxnBody).build();
		String statToTest = ConsensusController.SUBMIT_MESSAGE_METRIC;
		assertEquals(0, stats.getCountStat(statToTest, HederaNodeStats.HANDLED_SUFFIX));
		assertEquals(0.0, stats.getSpeedometerStat(statToTest, HederaNodeStats.HANDLED_SUFFIX));
		for (int i = 1; i <= 10; i++) {
			stats.transactionHandled(transaction);
			assertEquals(i, stats.getCountStat(statToTest, HederaNodeStats.HANDLED_SUFFIX));
			assertEquals(5127.0, stats.getAvgHdlSubMsgSize());
		}
		Thread.sleep(HederaNodeStats.UPDATE_PERIOD + 500);
		assertTrue(stats.getSpeedometerStat(statToTest, HederaNodeStats.HANDLED_SUFFIX) > 0.0);
	}

	@Test
	public void shouldUpdateRecordStreamQueueSize() {
		assertEquals(0, stats.getRecordStreamQueueSize());
		stats.updateRecordStreamQueueSize(4567);
		assertEquals(4567, stats.getRecordStreamQueueSize());
	}

	@Test
	public void shouldUpdatePlatformTxnNotCreatedPerSecond() {
		assertEquals(0.0, stats.getPlatformTxnNotCreatedPerSecond());
		for (int i = 1; i <= 25; i++) {
			stats.platformTxnNotCreated();
			assertTrue(stats.getPlatformTxnNotCreatedPerSecond() > 0.0);
		}
	}

	@Test
	public void dumpHederaNodeStatsShouldNotBeEmptyTest() throws Exception {
		assertNotNull(stats.dumpHederaNodeStats());
	}

}
