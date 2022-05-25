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


import com.hedera.services.state.merkle.MerkleStakingInfo;
import com.hedera.services.utils.EntityNum;
import com.swirlds.merkle.map.MerkleMap;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.function.Supplier;

@Singleton
public class StakeInfoManager {
	private final Supplier<MerkleMap<EntityNum, MerkleStakingInfo>> stakingInfo;
	private MerkleStakingInfo[] currentStakingInfos;
	private MerkleMap<EntityNum, MerkleStakingInfo> oldStakingInfo;

	@Inject
	public StakeInfoManager(final Supplier<MerkleMap<EntityNum, MerkleStakingInfo>> stakingInfo) {
		this.stakingInfo = stakingInfo;
		currentStakingInfos = new MerkleStakingInfo[stakingInfo.get().size()];
		oldStakingInfo = stakingInfo.get();
	}

	public MerkleStakingInfo mutableStakeInfoFor(long nodeId) {
		final var currentStakingInfo = stakingInfo.get();

		if (currentStakingInfos[(int) nodeId] == null && oldStakingInfo == currentStakingInfo) {
			currentStakingInfos[(int) nodeId] = currentStakingInfo.getForModify(EntityNum.fromLong(nodeId));
		} else if (currentStakingInfos[(int) nodeId] != null && oldStakingInfo != currentStakingInfo) {
			clearCurrentStakingInfos(currentStakingInfos);
			oldStakingInfo = stakingInfo.get();
			currentStakingInfos[(int) nodeId] = currentStakingInfo.getForModify(EntityNum.fromLong(nodeId));
		}
		return currentStakingInfos[(int) nodeId];
	}

	private void clearCurrentStakingInfos(final MerkleStakingInfo[] currentStakingInfos) {
		for (int i = 0; i < currentStakingInfos.length; i++) {
			currentStakingInfos[i] = null;
		}
	}

	public void clearRewardsHistory() {
		stakingInfo.get().forEach((entityNum, info) -> info.clearRewardSumHistory());
	}
}
