package com.hedera.services.ledger.interceptors;

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

import com.google.common.annotations.VisibleForTesting;
import com.hedera.services.context.SideEffectsTracker;
import com.hedera.services.context.annotations.CompositeProps;
import com.hedera.services.context.properties.PropertySource;
import com.hedera.services.records.RecordsHistorian;
import com.hedera.services.state.EntityCreator;
import com.hedera.services.state.merkle.MerkleAccount;
import com.hedera.services.state.merkle.MerkleNetworkContext;
import com.hedera.services.state.merkle.MerkleStakingInfo;
import com.hedera.services.store.contracts.precompile.SyntheticTxnFactory;
import com.hedera.services.utils.EntityNum;
import com.hederahashgraph.api.proto.java.NodeStake;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.swirlds.merkle.map.MerkleMap;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

import static com.hedera.services.records.TxnAwareRecordsHistorian.DEFAULT_SOURCE_ID;
import static com.hedera.services.state.EntityCreator.NO_CUSTOM_FEES;
import static com.hedera.services.utils.Units.HBARS_TO_TINYBARS;

@Singleton
public class EndOfStakingPeriodCalculator {
	public static final String END_OF_STAKING_PERIOD_CALCULATIONS_MEMO = "End of Staking Period Calculation record";
	private static final SideEffectsTracker NO_OTHER_SIDE_EFFECTS = new SideEffectsTracker();

	private final Supplier<MerkleMap<EntityNum, MerkleAccount>> accounts;
	private final Supplier<MerkleMap<EntityNum, MerkleStakingInfo>> stakingInfoSupplier;
	private final Supplier<MerkleNetworkContext> merkleNetworkContextSupplier;
	private final SyntheticTxnFactory syntheticTxnFactory;
	private final RecordsHistorian recordsHistorian;
	private final EntityCreator creator;
	private final PropertySource properties;

	@Inject
	public EndOfStakingPeriodCalculator(
			final Supplier<MerkleMap<EntityNum, MerkleAccount>> accounts,
			final Supplier<MerkleMap<EntityNum, MerkleStakingInfo>> stakingInfoSupplier,
			final Supplier<MerkleNetworkContext> merkleNetworkContextSupplier,
			final SyntheticTxnFactory syntheticTxnFactory,
			final RecordsHistorian recordsHistorian,
			final EntityCreator creator,
			@CompositeProps PropertySource properties
	) {
		this.accounts = accounts;
		this.stakingInfoSupplier = stakingInfoSupplier;
		this.merkleNetworkContextSupplier = merkleNetworkContextSupplier;
		this.syntheticTxnFactory = syntheticTxnFactory;
		this.recordsHistorian = recordsHistorian;
		this.creator = creator;
		this.properties = properties;
	}

	public void updateNodes(final Instant consensusTime) {
		final var stakingInfo = stakingInfoSupplier.get();
		final var merkleNetworkContext = merkleNetworkContextSupplier.get();

		// skip end of staking period calculations if the rewards are not yet activated.
		if (!merkleNetworkContext.areRewardsActivated()) {
			return;
		}

		long updatedTotalStakedStart = 0L;
		long updatedTotalStakedRewardStart = 0L;

		final var rewardRate = effectiveRateForCurrentPeriod();
		final var totalStakedRewardStart = merkleNetworkContext.getTotalStakedRewardStart();
		final List<NodeStake> nodeStakingInfos = new ArrayList<>();
		for (final var nodeNum : stakingInfo.keySet().stream().sorted().toList()) {
			final var merkleStakingInfo = stakingInfo.getForModify(nodeNum);
			// The return value is the reward rate (tinybars-per-hbar-staked-to-reward) that will be paid to all
			// accounts who had staked-to-reward for this node long enough to be eligible in the just-finished period
			final var lastPeriodRewardRate =
					merkleStakingInfo.updateRewardSumHistory(rewardRate, totalStakedRewardStart);
			System.out.println("Nodes reward start is : " + merkleStakingInfo.getStakeRewardStart());
			final var rewardsOwedForLastPeriod =
					(merkleStakingInfo.getStakeRewardStart() / HBARS_TO_TINYBARS) * lastPeriodRewardRate;
			merkleNetworkContext.increasePendingRewards(rewardsOwedForLastPeriod);
			final var nextPeriodStakeRewardStart =
					merkleStakingInfo.reviewElectionsFromLastPeriodAndRecomputeStakes();

			updatedTotalStakedRewardStart += nextPeriodStakeRewardStart;
			updatedTotalStakedStart += merkleStakingInfo.getStake();

			nodeStakingInfos.add(NodeStake.newBuilder()
					.setNodeId(nodeNum.longValue())
					.setStake(merkleStakingInfo.getStake())
					.setStakeRewarded(merkleStakingInfo.getStakeToReward())
					.build());
		}
		merkleNetworkContext.setTotalStakedRewardStart(updatedTotalStakedRewardStart);
		merkleNetworkContext.setTotalStakedStart(updatedTotalStakedStart);

		// create a synthetic txn with this computed data
		final var syntheticNodeStakeUpdateTxn =
				syntheticTxnFactory.nodeStakeUpdate(
						getMidnightTime(consensusTime),
						rewardRate,
						nodeStakingInfos);

		recordsHistorian.trackPrecedingChildRecord(
				DEFAULT_SOURCE_ID, syntheticNodeStakeUpdateTxn,
				creator.createSuccessfulSyntheticRecord(
						NO_CUSTOM_FEES,
						NO_OTHER_SIDE_EFFECTS,
						END_OF_STAKING_PERIOD_CALCULATIONS_MEMO));
	}

	@VisibleForTesting
	long effectiveRateForCurrentPeriod() {
		return Math.max(0, Math.min(
				stakingRewardsAccountBalance() - merkleNetworkContextSupplier.get().getPendingRewards(),
				properties.getLongProperty("staking.rewardRate")));
	}

	Timestamp getMidnightTime(Instant consensusTime) {
		final var justBeforeMidNightTime = LocalDate.ofInstant(consensusTime, ZoneId.of("UTC"))
				.atStartOfDay()
				.minusNanos(1); // give out the timestamp that is just before midnight

		return Timestamp.newBuilder()
				.setSeconds(justBeforeMidNightTime.toEpochSecond(ZoneOffset.UTC))
				.setNanos(justBeforeMidNightTime.getNano())
				.build();
	}

	private long stakingRewardsAccountBalance() {
		return accounts.get()
				.get(EntityNum.fromLong(properties.getLongProperty("accounts.stakingRewardAccount")))
				.getBalance();
	}
}
