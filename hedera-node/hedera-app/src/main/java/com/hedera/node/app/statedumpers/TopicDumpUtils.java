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

import com.hedera.hapi.node.base.TopicID;
import com.hedera.hapi.node.state.consensus.Topic;
import com.swirlds.base.utility.Pair;
import com.swirlds.state.merkle.disk.OnDiskKey;
import com.swirlds.state.merkle.disk.OnDiskValue;
import com.swirlds.virtualmap.VirtualMap;
import com.swirlds.virtualmap.VirtualMapMigration;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.nio.file.Path;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentLinkedQueue;

public class TopicDumpUtils {

    public static void dumpModTopics(
            @NonNull final Path path,
            @NonNull final VirtualMap<OnDiskKey<TopicID>, OnDiskValue<Topic>> topics,
            @NonNull final DumpCheckpoint checkpoint, final JsonWriter jsonWriter) {
        final var dumpableTopics = gatherTopics(topics);
        jsonWriter.write(dumpableTopics, path.toString());
        System.out.printf(
                    "=== mod topics report is %d bytes at checkpoint %s%n", dumpableTopics.size(), checkpoint.name());
    }

    private static Map<Long, Topic> gatherTopics(
            @NonNull final VirtualMap<OnDiskKey<TopicID>, OnDiskValue<Topic>> topicsStore) {
        final var r = new TreeMap<Long, Topic>();
        final var threadCount = 8;
        final var mappings = new ConcurrentLinkedQueue<Pair<Long, Topic>>();
        try {
            VirtualMapMigration.extractVirtualMapDataC(
                    getStaticThreadManager(),
                    topicsStore,
                    p -> {
                        try {
                            mappings.add(Pair.of(
                                    Long.valueOf(p.left().getKey().topicNum()),
                                    p.right().getValue()));
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    },
                    threadCount);
        } catch (final InterruptedException ex) {
            System.err.println("*** Traversal of topics virtual map interrupted!");
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
}
