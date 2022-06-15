package com.hedera.services.state.migration;

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

import com.hedera.services.context.properties.BootstrapProperties;
import com.hedera.services.state.merkle.MerkleStakingInfo;
import com.hedera.services.utils.EntityNum;
import com.swirlds.common.system.AddressBook;
import com.swirlds.merkle.map.MerkleMap;

import static com.hedera.services.ledger.accounts.staking.StakePeriodManager.DEFAULT_STAKING_PERIOD_MINS;

public final class ReleaseTwentySevenMigration {
	private ReleaseTwentySevenMigration() {
		throw new UnsupportedOperationException("Utility class");
	}

	public static MerkleMap<EntityNum, MerkleStakingInfo> buildStakingInfoMap(
			final AddressBook addressBook,
			final BootstrapProperties bootstrapProperties
	) {
		final MerkleMap<EntityNum, MerkleStakingInfo> stakingInfos = new MerkleMap<>();

		final var numberOfNodes = addressBook.getSize();
		long maxStakePerNode = bootstrapProperties.getLongProperty("ledger.totalTinyBarFloat") / numberOfNodes;
		long minStakePerNode = maxStakePerNode / 2;
		final long stakingPeriod = bootstrapProperties.getLongProperty("staking.periodMins");
		if (stakingPeriod != DEFAULT_STAKING_PERIOD_MINS) {
			minStakePerNode = 0L;
		}
		for (int i = 0; i < numberOfNodes; i++) {
			final var nodeNum = EntityNum.fromLong(addressBook.getAddress(i).getId());
			final var info = new MerkleStakingInfo(bootstrapProperties);
			info.setMinStake(minStakePerNode);
			info.setMaxStake(maxStakePerNode);
			stakingInfos.put(nodeNum, info);
		}

		return stakingInfos;
	}
}
