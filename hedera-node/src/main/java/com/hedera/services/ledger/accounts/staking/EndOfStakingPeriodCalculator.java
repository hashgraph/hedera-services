package com.hedera.services.ledger.accounts.staking;

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
import com.swirlds.common.stream.LinkedObjectStreamUtilities;
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
			final @CompositeProps PropertySource properties
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
		// --- BEGIN DEBUG-ONLY CODE ---
		final var thisPeriod = LinkedObjectStreamUtilities.getPeriod(consensusTime, 60_000);
		final var lastPeriod = thisPeriod - 1;
		System.out.println("Processing end of period " + lastPeriod + ", beginning " + thisPeriod);
		// --- END DEBUG-ONLY CODE ---

		final var stakingInfos = stakingInfoSupplier.get();
		final var merkleNetworkContext = merkleNetworkContextSupplier.get();

		// skip end of staking period calculations if the rewards are not yet activated.
		if (!merkleNetworkContext.areRewardsActivated()) {
			return;
		}

		long updatedTotalStakedStart = 0L;
		long updatedTotalStakedRewardStart = 0L;

		// total tinybars of reward earned by all stakers for the staking period now ending
		final var rewardRate = effectiveRateForCurrentPeriod();

		final var totalStakedRewardStart = merkleNetworkContext.getTotalStakedRewardStart();
		System.out.println("  - totalStakedRewardStart for ending period: " + totalStakedRewardStart);

		// The tinybars earned per hbar for stakers who are staked to a node whose total
		// stakedRewarded is in the range [minStake, maxStake]
		final var perHbarRate = totalStakedRewardStart < HBARS_TO_TINYBARS ? 0 :
				rewardRate / (totalStakedRewardStart / HBARS_TO_TINYBARS);

		final List<NodeStake> nodeStakingInfos = new ArrayList<>();
		for (final var nodeNum : stakingInfos.keySet().stream().sorted().toList()) {
			final var stakingInfo = stakingInfos.getForModify(nodeNum);

			// The return value is the reward rate (tinybars-per-hbar-staked-to-reward) that will be paid to all
			// accounts who had staked-to-reward for this node long enough to be eligible in the just-finished period
			final var nodeRewardRate = stakingInfo.updateRewardSumHistory(perHbarRate);
			System.out.println("  (A) Node0 lastPeriodStakedRewardStart was: " + stakingInfo.getStakeRewardStart());
			final var newStakeRewardStart = stakingInfo.reviewElectionsAndRecomputeStakes();
			System.out.println("  (B) Node0 curPeriodStakedRewardStart was : " + newStakeRewardStart);

			final var pendingRewardHbars = stakingInfo.stakeRewardStartWithPendingRewards() / HBARS_TO_TINYBARS;
			final var nodePendingRewards = pendingRewardHbars * nodeRewardRate;
			merkleNetworkContext.increasePendingRewards(nodePendingRewards);

			updatedTotalStakedRewardStart += newStakeRewardStart;
			updatedTotalStakedStart += stakingInfo.getStake();
			nodeStakingInfos.add(NodeStake.newBuilder()
					.setNodeId(nodeNum.longValue())
					.setStake(stakingInfo.getStake())
					.setRewardRate(nodePendingRewards)
					.setStakeRewarded(stakingInfo.getStakeToReward())
					.build());
		}
		merkleNetworkContext.setTotalStakedRewardStart(updatedTotalStakedRewardStart);
		merkleNetworkContext.setTotalStakedStart(updatedTotalStakedStart);

		// Create a synthetic txn with this computed data
		final var syntheticNodeStakeUpdateTxn =
				syntheticTxnFactory.nodeStakeUpdate(getMidnightTime(consensusTime), nodeStakingInfos);
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

	@VisibleForTesting
	Timestamp getMidnightTime(final Instant consensusTime) {
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
