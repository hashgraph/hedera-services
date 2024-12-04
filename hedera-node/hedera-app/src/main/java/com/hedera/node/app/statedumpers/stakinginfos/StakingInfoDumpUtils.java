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

package com.hedera.node.app.statedumpers.stakinginfos;

import static com.swirlds.common.threading.manager.AdHocThreadManager.getStaticThreadManager;

import com.hedera.hapi.node.state.common.EntityNumber;
import com.hedera.hapi.node.state.token.StakingNodeInfo;
import com.hedera.node.app.statedumpers.utils.Writer;
import com.swirlds.state.merkle.vmapsupport.OnDiskKey;
import com.swirlds.state.merkle.vmapsupport.OnDiskValue;
import com.swirlds.virtualmap.VirtualMap;
import com.swirlds.virtualmap.VirtualMapMigration;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;

public class StakingInfoDumpUtils {
    private StakingInfoDumpUtils() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * Dumps the nodes from the given virtual map to a file at the given path.
     * @param path the path to the file to write to
     * @param nodes the virtual map to dump
     */
    public static void dumpStakingInfos(
            @NonNull final Path path,
            @NonNull final VirtualMap<OnDiskKey<EntityNumber>, OnDiskValue<StakingNodeInfo>> nodes) {
        final var allStakingInfos = gatherStakingInfos(nodes);
        try (@NonNull final var writer = new Writer(path)) {
            writer.writeln("[");
            for (int i = 0, n = allStakingInfos.size(); i < n; i++) {
                final var info = allStakingInfos.get(i);
                writer.writeln(StakingNodeInfo.JSON.toJSON(info));
                if (i < n - 1) {
                    writer.writeln(",");
                }
            }
            writer.writeln("]");
        }
    }

    private static List<StakingNodeInfo> gatherStakingInfos(
            @NonNull final VirtualMap<OnDiskKey<EntityNumber>, OnDiskValue<StakingNodeInfo>> infos) {
        final var infosToReturn = new ConcurrentLinkedQueue<StakingNodeInfo>();
        final var threadCount = 8;
        final var processed = new AtomicInteger();
        try {
            VirtualMapMigration.extractVirtualMapData(
                    getStaticThreadManager(),
                    infos,
                    p -> {
                        processed.incrementAndGet();
                        infosToReturn.add(p.right().getValue());
                    },
                    threadCount);
        } catch (final InterruptedException ex) {
            System.err.println("*** Traversal of contracts virtual map interrupted!");
            Thread.currentThread().interrupt();
        }

        final List<StakingNodeInfo> answer = new ArrayList<>(infosToReturn);
        answer.sort(Comparator.comparingLong(StakingNodeInfo::nodeNumber));
        System.out.printf("=== %d nodes iterated over%n", answer.size());
        return answer;
    }
}
