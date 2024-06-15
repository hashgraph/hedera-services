/*
 * Copyright (C) 2024 Hedera Hashgraph, LLC
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

package com.hedera.node.app.statedumpers.singleton;

import static com.hedera.node.app.service.mono.statedumpers.singleton.StakingInfoDumpUtils.reportOnStakingInfo;
import static com.swirlds.common.threading.manager.AdHocThreadManager.getStaticThreadManager;

import com.hedera.hapi.node.state.common.EntityNumber;
import com.hedera.hapi.node.state.token.StakingNodeInfo;
import com.hedera.node.app.service.mono.state.adapters.MerkleMapLike;
import com.hedera.node.app.service.mono.state.adapters.VirtualMapLike;
import com.hedera.node.app.service.mono.statedumpers.DumpCheckpoint;
import com.hedera.node.app.service.mono.statedumpers.nfts.BBMUniqueToken;
import com.hedera.node.app.service.mono.statedumpers.nfts.BBMUniqueTokenId;
import com.hedera.node.app.service.mono.statedumpers.singleton.BBMStakingInfo;
import com.hedera.node.app.service.mono.statedumpers.utils.Writer;
import com.swirlds.base.utility.Pair;
import com.swirlds.merkle.map.MerkleMap;
import com.swirlds.platform.state.merkle.disk.OnDiskKey;
import com.swirlds.platform.state.merkle.disk.OnDiskValue;
import com.swirlds.virtualmap.VirtualMap;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentLinkedQueue;

public class StakingInfoDumpUtils {

    public static void dumpModStakingInfo(
            @NonNull final Path path,
            @NonNull
                    final VirtualMap<OnDiskKey<EntityNumber>, OnDiskValue<StakingNodeInfo>> BBMStakingInfoVirtualMap,
            @NonNull final DumpCheckpoint checkpoint) {
        System.out.printf("=== %d staking info ===%n", BBMStakingInfoVirtualMap.size());

        final var allBBMStakingInfo = gatherBBMStakingInfoFromMod(BBMStakingInfoVirtualMap);

        int reportSize;
        try (@NonNull final var writer = new Writer(path)) {
            reportSummary(writer, allBBMStakingInfo);
            reportOnStakingInfo(writer, allBBMStakingInfo);
            reportSize = writer.getSize();
        }

        System.out.printf("=== staking info report is %d bytes %n", reportSize);
    }

    @NonNull
    static Map<Long, BBMStakingInfo> gatherBBMStakingInfoFromMod(
            @NonNull
                    final VirtualMap<OnDiskKey<EntityNumber>, OnDiskValue<StakingNodeInfo>> stakingInfo) {
        final var r = new HashMap<Long, BBMStakingInfo>();
        final var threadCount = 5;
        final var mappings = new ConcurrentLinkedQueue<Pair<Long, BBMStakingInfo>>();
        try {
            VirtualMapLike.from(stakingInfo)
                    .extractVirtualMapDataC(
                            getStaticThreadManager(),
                            p -> mappings.add(Pair.of(p.left().getKey().number(), fromMod(p.right().getValue()))),
                            threadCount);
        } catch (final InterruptedException ex) {
            System.err.println("*** Traversal of uniques virtual map interrupted!");
            Thread.currentThread().interrupt();
        }
        // Consider in the future: Use another thread to pull things off the queue as they're put on by the
        // virtual map traversal
        while (!mappings.isEmpty()) {
            final var mapping = mappings.poll();
            r.put(mapping.key(), mapping.value());
        }
        return r;
    }

    static void reportSummary(@NonNull Writer writer, @NonNull Map<Long, BBMStakingInfo> BBMStakingInfo) {
        writer.writeln("=== %7d: staking info".formatted(BBMStakingInfo.size()));
        writer.writeln("");
    }

    public static BBMStakingInfo fromMod(@NonNull final StakingNodeInfo BBMStakingInfo) {
        Objects.requireNonNull(BBMStakingInfo.rewardSumHistory(), "rewardSumHistory");
        return new BBMStakingInfo(
                Long.valueOf(BBMStakingInfo.nodeNumber()).intValue(),
                BBMStakingInfo.minStake(),
                BBMStakingInfo.maxStake(),
                BBMStakingInfo.stakeToReward(),
                BBMStakingInfo.stakeToNotReward(),
                BBMStakingInfo.stakeRewardStart(),
                BBMStakingInfo.unclaimedStakeRewardStart(),
                BBMStakingInfo.stake(),
                BBMStakingInfo.rewardSumHistory().stream()
                        .mapToLong(Long::longValue)
                        .toArray(),
                BBMStakingInfo.weight());
    }
}
