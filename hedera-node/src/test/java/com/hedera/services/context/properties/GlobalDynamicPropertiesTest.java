package com.hedera.services.context.properties;

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

import com.hedera.services.config.HederaNumbers;
import com.hedera.services.fees.calculation.CongestionMultipliers;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.HederaFunctionality;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

class GlobalDynamicPropertiesTest {
	private static final String[] balanceExportPaths = new String[] {
			"/opt/hgcapp/accountBalances",
			"data/saved/accountBalances"
	};

	private PropertySource properties;

	private HederaNumbers numbers;
	private CongestionMultipliers oddCongestion = CongestionMultipliers.from("90,11x,95,27x,99,103x");
	private CongestionMultipliers evenCongestion = CongestionMultipliers.from("90,10x,95,25x,99,100x");
	private GlobalDynamicProperties subject;

	@BeforeEach
	void setup() {
		numbers = mock(HederaNumbers.class);
		given(numbers.shard()).willReturn(1L);
		given(numbers.realm()).willReturn(2L);
		properties = mock(PropertySource.class);
	}

	@Test
	void constructsFlagsAsExpected() {
		givenPropsWithSeed(1);

		// when:
		subject = new GlobalDynamicProperties(numbers, properties);

		// then:
		assertFalse(subject.shouldKeepRecordsInState());
		assertTrue(subject.shouldExportBalances());
		assertTrue(subject.shouldExportTokenBalances());
		assertTrue(subject.autoRenewEnabled());
		assertFalse(subject.areNftsEnabled());
	}

	@Test
	void nftPropertiesTest(){
		givenPropsWithSeed(1);
		subject = new GlobalDynamicProperties(numbers, properties);

		assertEquals(36, subject.maxNftTransfersLen());
		assertEquals(37, subject.maxBatchSizeBurn());
		assertEquals(38, subject.maxBatchSizeWipe());
		assertEquals(39, subject.maxBatchSizeMint());
		assertEquals(40, subject.maxNftQueryRange());
		assertEquals(41, subject.maxNftMetadataBytes());
		assertEquals(42, subject.maxTokenNameUtf8Bytes());
	}

	@Test
	void constructsIntsAsExpected() {
		givenPropsWithSeed(1);

		// when:
		subject = new GlobalDynamicProperties(numbers, properties);

		// then:
		assertEquals(1, subject.maxTokensPerAccount());
		assertEquals(2, subject.maxTokenSymbolUtf8Bytes());
		assertEquals(6, subject.maxFileSizeKb());
		assertEquals(8, subject.cacheRecordsTtl());
		assertEquals(9, subject.maxContractStorageKb());
		assertEquals(10, subject.ratesIntradayChangeLimitPercent());
		assertEquals(11, subject.balancesExportPeriodSecs());
		assertEquals(15, subject.maxTransferListSize());
		assertEquals(16, subject.maxTokenTransferListSize());
		assertEquals(17, subject.maxMemoUtf8Bytes());
		assertEquals(20, subject.minValidityBuffer());
		assertEquals(21, subject.maxGas());
		assertEquals(23, subject.feesTokenTransferUsageMultiplier());
		assertEquals(24, subject.maxAutoRenewDuration());
		assertEquals(25, subject.minAutoRenewDuration());
		assertEquals(26, subject.localCallEstRetBytes());
		assertEquals(27, subject.scheduledTxExpiryTimeSecs());
		assertEquals(28, subject.messageMaxBytesAllowed());
		assertEquals(29, subject.feesMinCongestionPeriod());
		assertEquals(31, subject.autoRenewNumberOfEntitiesToScan());
		assertEquals(32, subject.autoRenewMaxNumberOfEntitiesToRenewOrDelete());
		assertEquals(35, subject.maxCustomFeesAllowed());
	}

	@Test
	void constructsLongsAsExpected() {
		givenPropsWithSeed(1);

		// when:
		subject = new GlobalDynamicProperties(numbers, properties);

		// then:
		assertEquals(3L, subject.maxAccountNum());
		assertEquals(13L, subject.nodeBalanceWarningThreshold());
		assertEquals(18L, subject.maxTxnDuration());
		assertEquals(19L, subject.minTxnDuration());
		assertEquals(22L, subject.defaultContractLifetime());
		assertEquals(33L, subject.autoRenewGracePeriod());
		assertEquals(34L, subject.ratesMidnightCheckInterval());
	}

	@Test
	void constructsMiscAsExpected() {
		givenPropsWithSeed(1);

		// when:
		subject = new GlobalDynamicProperties(numbers, properties);

		// expect:
		assertEquals(accountWith(1L, 2L, 7L), subject.fundingAccount());
		assertEquals(balanceExportPaths[1], subject.pathToBalancesExportDir());
		assertEquals(Set.of(HederaFunctionality.CryptoTransfer), subject.schedulingWhitelist());
		assertEquals(oddCongestion, subject.congestionMultipliers());
	}

	@Test
	void reloadsFlagsAsExpected() {
		givenPropsWithSeed(2);

		// when:
		subject = new GlobalDynamicProperties(numbers, properties);

		// then:
		assertTrue(subject.shouldKeepRecordsInState());
		assertFalse(subject.shouldExportBalances());
		assertFalse(subject.shouldExportTokenBalances());
		assertFalse(subject.autoRenewEnabled());
		assertTrue(subject.areNftsEnabled());
	}

	@Test
	void reloadsIntsAsExpected() {
		givenPropsWithSeed(2);

		// when:
		subject = new GlobalDynamicProperties(numbers, properties);

		// then:
		assertEquals(2, subject.maxTokensPerAccount());
		assertEquals(3, subject.maxTokenSymbolUtf8Bytes());
		assertEquals(7, subject.maxFileSizeKb());
		assertEquals(9, subject.cacheRecordsTtl());
		assertEquals(10, subject.maxContractStorageKb());
		assertEquals(11, subject.ratesIntradayChangeLimitPercent());
		assertEquals(12, subject.balancesExportPeriodSecs());
		assertEquals(16, subject.maxTransferListSize());
		assertEquals(17, subject.maxTokenTransferListSize());
		assertEquals(18, subject.maxMemoUtf8Bytes());
		assertEquals(21, subject.minValidityBuffer());
		assertEquals(22, subject.maxGas());
		assertEquals(24, subject.feesTokenTransferUsageMultiplier());
		assertEquals(25, subject.maxAutoRenewDuration());
		assertEquals(26, subject.minAutoRenewDuration());
		assertEquals(27, subject.localCallEstRetBytes());
		assertEquals(28, subject.scheduledTxExpiryTimeSecs());
		assertEquals(29, subject.messageMaxBytesAllowed());
		assertEquals(30, subject.feesMinCongestionPeriod());
		assertEquals(32, subject.autoRenewNumberOfEntitiesToScan());
		assertEquals(33, subject.autoRenewMaxNumberOfEntitiesToRenewOrDelete());
		assertEquals(36, subject.maxCustomFeesAllowed());
	}

	@Test
	void reloadsLongsAsExpected() {
		givenPropsWithSeed(2);

		// when:
		subject = new GlobalDynamicProperties(numbers, properties);

		// then:
		assertEquals(4L, subject.maxAccountNum());
		assertEquals(14L, subject.nodeBalanceWarningThreshold());
		assertEquals(19L, subject.maxTxnDuration());
		assertEquals(20L, subject.minTxnDuration());
		assertEquals(23L, subject.defaultContractLifetime());
		assertEquals(34L, subject.autoRenewGracePeriod());
		assertEquals(35L, subject.ratesMidnightCheckInterval());
	}

	@Test
	void reloadsMiscAsExpected() {
		givenPropsWithSeed(2);

		// when:
		subject = new GlobalDynamicProperties(numbers, properties);

		// expect:
		assertEquals(accountWith(1L, 2L, 8L), subject.fundingAccount());
		assertEquals(balanceExportPaths[0], subject.pathToBalancesExportDir());
		assertEquals(Set.of(HederaFunctionality.CryptoCreate), subject.schedulingWhitelist());
		assertEquals(evenCongestion, subject.congestionMultipliers());
	}

	private void givenPropsWithSeed(int i) {
		given(properties.getIntProperty("tokens.maxPerAccount")).willReturn(i);
		given(properties.getIntProperty("tokens.maxSymbolUtf8Bytes")).willReturn(i + 1);
		given(properties.getBooleanProperty("ledger.keepRecordsInState")).willReturn((i % 2) == 0);
		given(properties.getLongProperty("ledger.maxAccountNum")).willReturn((long)i + 2);
		given(properties.getIntProperty("files.maxSizeKb")).willReturn(i + 5);
		given(properties.getLongProperty("ledger.fundingAccount")).willReturn((long)i + 6);
		given(properties.getIntProperty("cache.records.ttl")).willReturn(i + 7);
		given(properties.getIntProperty("contracts.maxStorageKb")).willReturn(i + 8);
		given(properties.getIntProperty("rates.intradayChangeLimitPercent")).willReturn(i + 9);
		given(properties.getIntProperty("balances.exportPeriodSecs")).willReturn(i + 10);
		given(properties.getBooleanProperty("balances.exportEnabled")).willReturn((i + 11) % 2 == 0);
		given(properties.getLongProperty("balances.nodeBalanceWarningThreshold")).willReturn(i + 12L);
		given(properties.getStringProperty("balances.exportDir.path")).willReturn(balanceExportPaths[i % 2]);
		given(properties.getBooleanProperty("balances.exportTokenBalances")).willReturn((i + 13) % 2 == 0);
		given(properties.getIntProperty("ledger.transfers.maxLen")).willReturn(i + 14);
		given(properties.getIntProperty("ledger.tokenTransfers.maxLen")).willReturn(i + 15);
		given(properties.getIntProperty("hedera.transaction.maxMemoUtf8Bytes")).willReturn(i + 16);
		given(properties.getLongProperty("hedera.transaction.maxValidDuration")).willReturn(i + 17L);
		given(properties.getLongProperty("hedera.transaction.minValidDuration")).willReturn(i + 18L);
		given(properties.getIntProperty("hedera.transaction.minValidityBufferSecs")).willReturn(i + 19);
		given(properties.getIntProperty("contracts.maxGas")).willReturn(i + 20);
		given(properties.getLongProperty("contracts.defaultLifetime")).willReturn(i + 21L);
		given(properties.getIntProperty("fees.tokenTransferUsageMultiplier")).willReturn(i + 22);
		given(properties.getLongProperty("ledger.autoRenewPeriod.maxDuration")).willReturn(i + 23L);
		given(properties.getLongProperty("ledger.autoRenewPeriod.minDuration")).willReturn(i + 24L);
		given(properties.getIntProperty("contracts.localCall.estRetBytes")).willReturn(i + 25);
		given(properties.getIntProperty("ledger.schedule.txExpiryTimeSecs")).willReturn(i + 26);
		given(properties.getIntProperty("consensus.message.maxBytesAllowed")).willReturn(i + 27);
		given(properties.getFunctionsProperty("scheduling.whitelist")).willReturn(i % 2 == 0
				? Set.of(HederaFunctionality.CryptoCreate)
				: Set.of(HederaFunctionality.CryptoTransfer));
		given(properties.getCongestionMultiplierProperty("fees.percentCongestionMultipliers"))
				.willReturn(i % 2 == 0 ? evenCongestion : oddCongestion);
		given(properties.getIntProperty("fees.minCongestionPeriod")).willReturn(i + 28);
		given(properties.getBooleanProperty("autorenew.isEnabled")).willReturn((i + 29) % 2 == 0);
		given(properties.getIntProperty("autorenew.numberOfEntitiesToScan")).willReturn(i + 30);
		given(properties.getIntProperty("autorenew.maxNumberOfEntitiesToRenewOrDelete")).willReturn(i + 31);
		given(properties.getLongProperty("autorenew.gracePeriod")).willReturn(i + 32L);
		given(properties.getLongProperty("rates.midnightCheckInterval")).willReturn(i + 33L);
		given(properties.getIntProperty("tokens.maxCustomFeesAllowed")).willReturn(i + 34);

		given(properties.getIntProperty("ledger.nftTransfers.maxLen")).willReturn(i + 35);
		given(properties.getIntProperty("tokens.nfts.maxBatchSizeBurn")).willReturn(i + 36);
		given(properties.getIntProperty("tokens.nfts.maxBatchSizeWipe")).willReturn(i + 37);
		given(properties.getIntProperty("tokens.nfts.maxBatchSizeMint")).willReturn(i + 38);
		given(properties.getLongProperty("tokens.nfts.maxQueryRange")).willReturn(i + 39L);
		given(properties.getIntProperty("tokens.nfts.maxMetadataBytes")).willReturn(i + 40);
		given(properties.getIntProperty("tokens.maxTokenNameUtf8Bytes")).willReturn(i + 41);
		given(properties.getBooleanProperty("tokens.nfts.areEnabled")).willReturn((i + 42) % 2 == 0);
	}

	private AccountID accountWith(long shard, long realm, long num) {
		return AccountID.newBuilder()
				.setShardNum(shard)
				.setRealmNum(realm)
				.setAccountNum(num)
				.build();
	}
}
