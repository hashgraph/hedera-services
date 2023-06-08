/*
 * Copyright (C) 2022-2023 Hedera Hashgraph, LLC
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

package com.hedera.node.app.service.mono.ledger.accounts.staking;

import com.google.common.annotations.VisibleForTesting;
import com.hedera.node.app.service.mono.state.adapters.MerkleMapLike;
import com.hedera.node.app.service.mono.state.merkle.MerkleStakingInfo;
import com.hedera.node.app.service.mono.utils.EntityNum;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.util.Arrays;
import java.util.List;
import java.util.function.Supplier;
import java.util.stream.LongStream;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Singleton
public class StakeInfoManager {
    private static final Logger log = LogManager.getLogger(StakeInfoManager.class);
    private final Supplier<MerkleMapLike<EntityNum, MerkleStakingInfo>> stakingInfos;

    // Used to improve performance when node ids are sequential whole numbers (0, 1, 2, ...)
    private MerkleStakingInfo[] cache;
    private MerkleMapLike<EntityNum, MerkleStakingInfo> prevStakingInfos;

    @Inject
    public StakeInfoManager(final Supplier<MerkleMapLike<EntityNum, MerkleStakingInfo>> stakingInfo) {
        this.stakingInfos = stakingInfo;
    }

    public void unclaimRewardsForStakeStart(final long nodeId, final long amount) {
        final var info = mutableStakeInfoFor(nodeId);
        if (info != null) {
            info.increaseUnclaimedStakeRewardStart(amount);
        }
    }

    public void prepForManaging(final List<Long> nodeIds) {
        final var numNodes = nodeIds.size();
        final var orderedIds = nodeIds.stream().sorted().toList();
        if (orderedIds.equals(LongStream.range(0, numNodes).boxed().toList())) {
            cache = new MerkleStakingInfo[numNodes];
        } else {
            cache = null;
        }
    }

    @Nullable
    public MerkleStakingInfo mutableStakeInfoFor(final long nodeId) {
        if (cache == null) {
            return stakingInfos.get().getForModify(EntityNum.fromLong(nodeId));
        } else {
            if (nodeId < 0) {
                log.warn("Stake info requested for negative node id {}", nodeId);
                return null;
            }
            if (nodeId >= cache.length) {
                log.warn("Stake info requested for node id {} beyond cache size {}", nodeId, cache.length);
                return null;
            }
            return getFromCache(nodeId);
        }
    }

    public void clearAllRewardHistory() {
        final var mutableStakingInfo = stakingInfos.get();
        for (var key : mutableStakingInfo.keySet()) {
            final var info = mutableStakingInfo.getForModify(key);
            info.clearRewardSumHistory();
        }
    }

    private MerkleStakingInfo getFromCache(final long nodeId) {
        final var curStakingInfos = stakingInfos.get();
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

    private void clearCache() {
        Arrays.fill(cache, null);
    }

    @VisibleForTesting
    MerkleStakingInfo[] getCache() {
        return cache;
    }

    @VisibleForTesting
    void setPrevStakingInfos(final MerkleMapLike<EntityNum, MerkleStakingInfo> prevStakingInfos) {
        this.prevStakingInfos = prevStakingInfos;
    }

    @VisibleForTesting
    void setCache(final MerkleStakingInfo[] cache) {
        this.cache = cache;
    }
}
