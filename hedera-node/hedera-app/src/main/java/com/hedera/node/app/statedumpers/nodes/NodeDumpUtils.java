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

package com.hedera.node.app.statedumpers.nodes;

import static com.swirlds.common.threading.manager.AdHocThreadManager.getStaticThreadManager;

import com.hedera.hapi.node.state.addressbook.Node;
import com.hedera.hapi.node.state.common.EntityNumber;
import com.hedera.node.app.statedumpers.utils.Writer;
import com.swirlds.state.merkle.disk.OnDiskKey;
import com.swirlds.state.merkle.disk.OnDiskValue;
import com.swirlds.virtualmap.VirtualMap;
import com.swirlds.virtualmap.VirtualMapMigration;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;

public class NodeDumpUtils {
    private NodeDumpUtils() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * Dumps the nodes from the given virtual map to a file at the given path.
     * @param path the path to the file to write to
     * @param nodes the virtual map to dump
     */
    public static void dumpNodes(
            @NonNull final Path path, @NonNull final VirtualMap<OnDiskKey<EntityNumber>, OnDiskValue<Node>> nodes) {
        final var allNodes = gatherNodes(nodes);
        try (@NonNull final var writer = new Writer(path)) {
            writer.writeln("[");
            for (int i = 0, n = allNodes.size(); i < n; i++) {
                final var node = allNodes.get(i);
                writer.writeln(Node.JSON.toJSON(node));
                if (i < n - 1) {
                    writer.writeln(",");
                }
            }
            writer.writeln("]");
        }
    }

    private static List<Node> gatherNodes(@NonNull final VirtualMap<OnDiskKey<EntityNumber>, OnDiskValue<Node>> nodes) {
        final var nodesToReturn = new ConcurrentLinkedQueue<Node>();
        final var threadCount = 8;
        final var processed = new AtomicInteger();
        try {
            VirtualMapMigration.extractVirtualMapData(
                    getStaticThreadManager(),
                    nodes,
                    p -> {
                        processed.incrementAndGet();
                        nodesToReturn.add(p.right().getValue());
                    },
                    threadCount);
        } catch (final InterruptedException ex) {
            System.err.println("*** Traversal of contracts virtual map interrupted!");
            Thread.currentThread().interrupt();
        }

        final List<Node> answer = new ArrayList<>(nodesToReturn);
        answer.sort(Comparator.comparingLong(Node::nodeId));
        System.out.printf("=== %d nodes iterated over%n", answer.size());
        return answer;
    }
}
