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
		verify(platform, times(208)).addAppStatEntry(any(StatEntry.class));
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
		String statToTest = "createAccount";
		assertEquals(0, stats.getCountStat(statToTest, HederaNodeStats.RECEIVED_SUFFIX));
		for (int i = 1; i <= 10; i++) {
			stats.cryptoTransactionReceived(statToTest);
			assertEquals(i, stats.getCountStat(statToTest, HederaNodeStats.RECEIVED_SUFFIX));
		}
	}

	@Test
	public void shouldIncreaseCryptoTransactionsSubmitted() {
		String statToTest = "deleteClaim";
		assertEquals(0, stats.getCountStat(statToTest, HederaNodeStats.SUBMITTED_SUFFIX));
		for (int i = 1; i <= 10; i++) {
			stats.cryptoTransactionSubmitted(statToTest);
			assertEquals(i, stats.getCountStat(statToTest, HederaNodeStats.SUBMITTED_SUFFIX));
		}
	}

	@Test
	public void shouldIncreaseCryptoQueriesReceived() {
		String statToTest = "getTransactionReceipts";
		assertEquals(0, stats.getCountStat(statToTest, HederaNodeStats.RECEIVED_SUFFIX));
		for (int i = 1; i <= 10; i++) {
			stats.cryptoQueryReceived(statToTest);
			assertEquals(i, stats.getCountStat(statToTest, HederaNodeStats.RECEIVED_SUFFIX));
		}
	}

	@Test
	public void shouldIncreaseCryptoQueriesSubmitted() {
		String statToTest = "getTxRecordByTxID";
		assertEquals(0, stats.getCountStat(statToTest, HederaNodeStats.SUBMITTED_SUFFIX));
		for (int i = 1; i <= 10; i++) {
			stats.cryptoQuerySubmitted(statToTest);
			assertEquals(i, stats.getCountStat(statToTest, HederaNodeStats.SUBMITTED_SUFFIX));
		}
	}

	@Test
	public void shouldIncreaseFileTransactionsReceived() {
		String statToTest = "updateFile";
		assertEquals(0, stats.getCountStat(statToTest, HederaNodeStats.RECEIVED_SUFFIX));
		for (int i = 1; i <= 10; i++) {
			stats.fileTransactionReceived(statToTest);
			assertEquals(i, stats.getCountStat(statToTest, HederaNodeStats.RECEIVED_SUFFIX));
		}
	}

	@Test
	public void shouldIncreaseFileTransactionsSubmitted() {
		String statToTest = "appendContent";
		assertEquals(0, stats.getCountStat(statToTest, HederaNodeStats.SUBMITTED_SUFFIX));
		for (int i = 1; i <= 10; i++) {
			stats.fileTransactionSubmitted(statToTest);
			assertEquals(i, stats.getCountStat(statToTest, HederaNodeStats.SUBMITTED_SUFFIX));
		}
	}

	@Test
	public void shouldIncreaseFileQueriesReceived() {
		String statToTest = "getFileContent";
		assertEquals(0, stats.getCountStat(statToTest, HederaNodeStats.RECEIVED_SUFFIX));
		for (int i = 1; i <= 10; i++) {
			stats.fileQueryReceived(statToTest);
			assertEquals(i, stats.getCountStat(statToTest, HederaNodeStats.RECEIVED_SUFFIX));
		}
	}

	@Test
	public void shouldIncreaseFileQueriesSubmitted() {
		String statToTest = "getFileInfo";
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
		String statToTest = "cryptoTransfer";
		assertEquals(0, stats.getCountStat(statToTest, HederaNodeStats.HANDLED_SUFFIX));
		for (int i = 1; i <= 10; i++) {
			stats.transactionHandled(statToTest, i);
			assertEquals(i, stats.getCountStat(statToTest, HederaNodeStats.HANDLED_SUFFIX));
			assertTrue(stats.getAvgHdlTxSize() > 0.0);
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
		String statToTest = "cryptoTransfer";
		assertEquals(0.0, stats.getSpeedometerStat(statToTest, HederaNodeStats.RECEIVED_SUFFIX));
		for (int i = 1; i <= 10; i++) {
			stats.cryptoTransactionReceived(statToTest);
		}
		Thread.sleep(HederaNodeStats.UPDATE_PERIOD + 500);
		assertTrue(stats.getSpeedometerStat(statToTest, HederaNodeStats.RECEIVED_SUFFIX) > 0.0);
	}

	@Test
	public void shouldUpdateOtherSpeedometers() throws InterruptedException {
		String cryptoQuerySubStat = "getAccountInfo";
		assertEquals(0.0, stats.getSpeedometerStat(cryptoQuerySubStat, HederaNodeStats.SUBMITTED_SUFFIX));
		for (int i = 1; i <= 10; i++) {
			stats.cryptoQuerySubmitted(cryptoQuerySubStat);
		}
		String fileTransactionHdlStat = "createFile";
		assertEquals(0.0, stats.getSpeedometerStat(fileTransactionHdlStat, HederaNodeStats.HANDLED_SUFFIX));
		for (int i = 1; i <= 20; i++) {
			stats.transactionHandled(fileTransactionHdlStat, 5678);
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
		assertEquals(5678.0, stats.getAvgHdlTxSize());
	}


	@Test
	public void dumpHederaNodeStatsShouldNotBeEmptyTest() throws Exception {
		assertNotNull(stats.dumpHederaNodeStats());
	}

}
