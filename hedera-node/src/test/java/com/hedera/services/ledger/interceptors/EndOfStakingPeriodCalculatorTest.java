package com.hedera.services.ledger.interceptors;

import com.hedera.services.context.properties.PropertySource;
import com.hedera.services.records.RecordsHistorian;
import com.hedera.services.state.EntityCreator;
import com.hedera.services.state.merkle.MerkleAccount;
import com.hedera.services.state.merkle.MerkleNetworkContext;
import com.hedera.services.state.merkle.MerkleStakingInfo;
import com.hedera.services.store.contracts.precompile.SyntheticTxnFactory;
import com.hedera.services.utils.EntityNum;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.swirlds.merkle.map.MerkleMap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/*-
 * ‌
 * Hedera Services Node
 * ​
 * Copyright (C) 2018 - 2022 Hedera Hashgraph, LLC
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

@ExtendWith(MockitoExtension.class)
class EndOfStakingPeriodCalculatorTest {
	@Mock
	MerkleMap<EntityNum, MerkleAccount> accounts;
	@Mock
	MerkleMap<EntityNum, MerkleStakingInfo> stakingInfos;
	@Mock
	MerkleNetworkContext merkleNetworkContext;
	@Mock
	SyntheticTxnFactory syntheticTxnFactory;
	@Mock
	RecordsHistorian recordsHistorian;
	@Mock
	EntityCreator creator;
	@Mock
	PropertySource properties;

	private EndOfStakingPeriodCalculator subject;

	@BeforeEach
	void setup() {
		subject = new EndOfStakingPeriodCalculator(
				() -> accounts,
				() -> stakingInfos,
				() -> merkleNetworkContext,
				syntheticTxnFactory,
				recordsHistorian,
				creator,
				properties
		);
	}

	@Test
	void skipsEndOfStakingPeriodCalcsIfRewardsAreNotActivated() {
		final var consensusTime = Instant.now();
		given(merkleNetworkContext.areRewardsActivated()).willReturn(false);

		subject.updateNodes(consensusTime);

		verify(merkleNetworkContext, never()).setTotalStakedRewardStart(anyLong());
		verify(merkleNetworkContext, never()).setTotalStakedStart(anyLong());
		verify(syntheticTxnFactory, never()).nodeStakeUpdate(any(), anyLong(), anyList());
	}

	@Test
	void calculatesNewTotalStakesAsExpected() {
		final var consensusTime = Instant.now();
		final var balance_800 = 100_000_000_000L;
		final var account_800 = mock(MerkleAccount.class);

		given(merkleNetworkContext.areRewardsActivated()).willReturn(true);
		given(properties.getLongProperty("staking.rewardRate")).willReturn(10_000_000_000L);
		given(properties.getLongProperty("accounts.stakingRewardAccount")).willReturn(stakingRewardAccount);
		given(accounts.get(EntityNum.fromInt(800))).willReturn(account_800);
		given(account_800.getBalance()).willReturn(balance_800);
		given(stakingInfos.keySet()).willReturn(Set.of(nodeNum1, nodeNum2, nodeNum3));
		given(stakingInfos.getForModify(nodeNum1)).willReturn(stakingInfo1);
		given(stakingInfos.getForModify(nodeNum2)).willReturn(stakingInfo2);
		given(stakingInfos.getForModify(nodeNum3)).willReturn(stakingInfo3);
		given(merkleNetworkContext.getTotalStakedRewardStart()).willReturn(10_000L);

		subject.updateNodes(consensusTime);

		verify(merkleNetworkContext).setTotalStakedRewardStart(1000L);
		verify(merkleNetworkContext).setTotalStakedStart(1300L);
		assertEquals(800L, stakingInfo1.getStake());
		assertEquals(500L, stakingInfo2.getStake());
		assertEquals(0L, stakingInfo3.getStake());
		assertArrayEquals(new long[]{16,6,5}, stakingInfo1.getRewardSumHistory());
		assertArrayEquals(new long[]{8,1,1}, stakingInfo2.getRewardSumHistory());
		assertArrayEquals(new long[]{103,3,1}, stakingInfo3.getRewardSumHistory());
	}

	@Test
	void calculatesMidnightTimeCorrectly() {
		final var consensusSecs = 1653660350L;
		final var consensusNanos = 12345L;
		final var expectedNanos = 999_999_999;
		final var consensusTime = Instant.ofEpochSecond(consensusSecs, consensusNanos);
		final var expectedMidnightTime = Timestamp.newBuilder()
				.setSeconds(1653609599L)
				.setNanos(expectedNanos)
				.build();

		assertEquals(expectedMidnightTime, subject.getMidnightTime(consensusTime));
	}

	final long stakingRewardAccount = 800L;
	final long minStake = 100L;
	final long maxStake = 800L;
	final long stakeToReward1 = 700L;
	final long stakeToReward2 = 300L;
	final long stakeToReward3 = 30L;
	final long stakeToNotReward1 = 300L;
	final long stakeToNotReward2 = 200L;
	final long stakeToNotReward3 = 20L;
	final long stakedRewardStart1 = 1_000L;
	final long stakedRewardStart2 = 700L;
	final long stakedRewardStart3 = 10_000L;
	final long stake1 = 2_000L;
	final long stake2 = 750L;
	final long stake3 = 75L;
	final long[] rewardSumHistory1 = new long[]{8,7,2};
	final long[] rewardSumHistory2 = new long[]{5,5,4};
	final long[] rewardSumHistory3 = new long[]{4,2,1};
	final EntityNum nodeNum1 = EntityNum.fromInt(0);
	final EntityNum nodeNum2 = EntityNum.fromInt(1);
	final EntityNum nodeNum3 = EntityNum.fromInt(2);
	final MerkleStakingInfo stakingInfo1 = new MerkleStakingInfo(
			minStake, maxStake, stakeToReward1, stakeToNotReward1, stakedRewardStart1, stake1, rewardSumHistory1);
	final MerkleStakingInfo stakingInfo2 = new MerkleStakingInfo(
			minStake, maxStake, stakeToReward2, stakeToNotReward2, stakedRewardStart2, stake2, rewardSumHistory2);
	final MerkleStakingInfo stakingInfo3 = new MerkleStakingInfo(
			minStake, maxStake, stakeToReward3, stakeToNotReward3, stakedRewardStart3, stake3, rewardSumHistory3);
}
