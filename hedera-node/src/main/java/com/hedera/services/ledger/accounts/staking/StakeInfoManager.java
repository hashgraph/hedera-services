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
import com.hedera.services.state.merkle.MerkleStakingInfo;
import com.hedera.services.utils.EntityNum;
import com.swirlds.merkle.map.MerkleMap;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.Arrays;
import java.util.function.Supplier;

@Singleton
public class StakeInfoManager {
	private final Supplier<MerkleMap<EntityNum, MerkleStakingInfo>> stakingInfo;

	private MerkleStakingInfo[] cache;
	private MerkleMap<EntityNum, MerkleStakingInfo> prevStakingInfo;

	@Inject
	public StakeInfoManager(final Supplier<MerkleMap<EntityNum, MerkleStakingInfo>> stakingInfo) {
		this.stakingInfo = stakingInfo;
	}

	public MerkleStakingInfo mutableStakeInfoFor(long nodeId) {
		final var curStakingInfo = stakingInfo.get();
		if (cache == null) {
			cache = new MerkleStakingInfo[stakingInfo.get().size()];
		}
		final var i = (int) nodeId;
		if (cache[i] == null && curStakingInfo == prevStakingInfo) {
			cache[i] = curStakingInfo.getForModify(EntityNum.fromLong(nodeId));
		} else if (curStakingInfo != prevStakingInfo) {
			clearCache();
			prevStakingInfo = curStakingInfo;
			cache[i] = curStakingInfo.getForModify(EntityNum.fromLong(nodeId));
		}
		return cache[i];
	}

	public void clearAllRewardHistory() {
		final var mutableStakingInfo = stakingInfo.get();
		for (var key : mutableStakingInfo.keySet()) {
			final var info = mutableStakingInfo.getForModify(key);
			info.clearRewardSumHistory();
		}
	}

	private void clearCache() {
		Arrays.fill(cache, null);
	}

	@VisibleForTesting
	MerkleStakingInfo[] getCache() {
		return cache;
	}

	@VisibleForTesting
	void setPrevStakingInfo(final MerkleMap<EntityNum, MerkleStakingInfo> prevStakingInfo) {
		this.prevStakingInfo = prevStakingInfo;
	}

	@VisibleForTesting
	void setCache(final MerkleStakingInfo[] cache) {
		this.cache = cache;
	}
}
