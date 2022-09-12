/*
 * Copyright (C) 2022 Hedera Hashgraph, LLC
 *
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
 */
package com.hedera.services.ledger.accounts.staking;

import com.google.common.annotations.VisibleForTesting;
import com.hedera.services.state.merkle.MerkleStakingInfo;
import com.hedera.services.utils.EntityNum;
import com.swirlds.merkle.map.MerkleMap;
import java.util.Arrays;
import java.util.function.Supplier;
import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class StakeInfoManager {
    private final Supplier<MerkleMap<EntityNum, MerkleStakingInfo>> stakingInfos;

    private MerkleStakingInfo[] cache;
    private MerkleMap<EntityNum, MerkleStakingInfo> prevStakingInfos;

    @Inject
    public StakeInfoManager(final Supplier<MerkleMap<EntityNum, MerkleStakingInfo>> stakingInfo) {
        this.stakingInfos = stakingInfo;
    }

    public void unclaimRewardsForStakeStart(final long nodeId, final long amount) {
        final var info = mutableStakeInfoFor(nodeId);
        info.increaseUnclaimedStakeRewardStart(amount);
    }

    public MerkleStakingInfo mutableStakeInfoFor(final long nodeId) {
        // Current node ids are 0, 1, 2, ..., n---if this changes, an array ofc will no longer be a
        // good cache
        final var curStakingInfos = stakingInfos.get();
        if (cache == null) {
            cache = new MerkleStakingInfo[stakingInfos.get().size()];
        }
        final var i = (int) nodeId;
        if (cache[i] == null && curStakingInfos == prevStakingInfos) {
            cache[i] = curStakingInfos.getForModify(EntityNum.fromLong(nodeId));
        } else if (curStakingInfos != prevStakingInfos) {
            clearCache();
            prevStakingInfos = curStakingInfos;
            cache[i] = curStakingInfos.getForModify(EntityNum.fromLong(nodeId));
        }
        return cache[i];
    }

    public void clearAllRewardHistory() {
        final var mutableStakingInfo = stakingInfos.get();
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
    void setPrevStakingInfos(final MerkleMap<EntityNum, MerkleStakingInfo> prevStakingInfos) {
        this.prevStakingInfos = prevStakingInfos;
    }

    @VisibleForTesting
    void setCache(final MerkleStakingInfo[] cache) {
        this.cache = cache;
    }
}
