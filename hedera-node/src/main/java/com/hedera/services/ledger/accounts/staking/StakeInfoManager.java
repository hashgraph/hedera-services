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
import java.util.function.Supplier;

public class StakeInfoManager {
	private final Supplier<MerkleMap<EntityNum, MerkleStakingInfo>> stakingInfo;

	@Inject
	public StakeInfoManager(final Supplier<MerkleMap<EntityNum, MerkleStakingInfo>> stakingInfo) {
		this.stakingInfo = stakingInfo;
	}

	public MerkleStakingInfo mutableStakeInfoFor(long nodeId) {
		return stakingInfo.get().getForModify(EntityNum.fromLong(nodeId));
	}

	public void clearRewardsHistory() {
		stakingInfo.get().forEach((entityNum, info) -> info.clearRewardSumHistory());
	}
}
