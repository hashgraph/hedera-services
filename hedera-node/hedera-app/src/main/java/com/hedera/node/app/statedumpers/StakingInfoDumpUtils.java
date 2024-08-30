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

package com.hedera.node.app.statedumpers;

import static com.swirlds.common.threading.manager.AdHocThreadManager.getStaticThreadManager;

import com.hedera.hapi.node.state.common.EntityNumber;
import com.hedera.hapi.node.state.token.StakingNodeInfo;
import com.swirlds.state.merkle.disk.OnDiskKey;
import com.swirlds.state.merkle.disk.OnDiskValue;
import com.swirlds.virtualmap.VirtualMap;
import com.swirlds.virtualmap.VirtualMapMigration;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;

public class StakingInfoDumpUtils {
    static final String FIELD_SEPARATOR = ";";
    public static void dumpModStakingInfo(
            @NonNull final Path path,
            @NonNull final VirtualMap<OnDiskKey<EntityNumber>, OnDiskValue<StakingNodeInfo>> BBMStakingInfoVirtualMap, final JsonWriter jsonWriter) {
        System.out.printf("=== %d staking info ===%n", BBMStakingInfoVirtualMap.size());

        final var allBBMStakingInfo = gatherBBMStakingInfoFromMod(BBMStakingInfoVirtualMap);
        jsonWriter.write(allBBMStakingInfo, path.toString());

        System.out.printf("=== staking info report is %d bytes %n", allBBMStakingInfo.size());
    }

    @NonNull
    static List<StakingNodeInfo> gatherBBMStakingInfoFromMod(
            @NonNull final VirtualMap<OnDiskKey<EntityNumber>, OnDiskValue<StakingNodeInfo>> stakingInfo) {
        final var r = new ArrayList<StakingNodeInfo>();
        final var threadCount = 5;
        final var mappings = new ConcurrentLinkedQueue<StakingNodeInfo>();
        try {
            VirtualMapMigration.extractVirtualMapDataC(
                    getStaticThreadManager(),
                    stakingInfo,
                    p -> mappings.add(p.right().getValue()),
                    threadCount);
        } catch (final InterruptedException ex) {
            System.err.println("*** Traversal of uniques virtual map interrupted!");
            Thread.currentThread().interrupt();
        }
        // Consider in the future: Use another thread to pull things off the queue as they're put on by the
        // virtual map traversal
        while (!mappings.isEmpty()) {
            final var mapping = mappings.poll();
            r.add(mapping);
        }
        return r;
    }
}
