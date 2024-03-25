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

package com.hedera.node.app.statedumpers.topics;

import static com.hedera.node.app.service.mono.pbj.PbjConverter.fromPbjKey;
import static com.hedera.node.app.service.mono.statedumpers.topics.TopicDumpUtils.reportOnTopics;
import static com.swirlds.common.threading.manager.AdHocThreadManager.getStaticThreadManager;

import com.hedera.hapi.node.base.TopicID;
import com.hedera.hapi.node.state.consensus.Topic;
import com.hedera.node.app.service.mono.legacy.core.jproto.JKey;
import com.hedera.node.app.service.mono.state.adapters.VirtualMapLike;
import com.hedera.node.app.service.mono.state.submerkle.EntityId;
import com.hedera.node.app.service.mono.statedumpers.DumpCheckpoint;
import com.hedera.node.app.service.mono.statedumpers.topics.BBMTopic;
import com.hedera.node.app.service.mono.statedumpers.utils.Writer;
import com.hedera.node.app.state.merkle.disk.OnDiskKey;
import com.hedera.node.app.state.merkle.disk.OnDiskValue;
import com.swirlds.base.utility.Pair;
import com.swirlds.virtualmap.VirtualMap;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.nio.file.Path;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentLinkedQueue;

public class TopicDumpUtils {
    private TopicDumpUtils() {
        // Utility class
    }

    public static void dumpModTopics(
            @NonNull final Path path,
            @NonNull final VirtualMap<OnDiskKey<TopicID>, OnDiskValue<Topic>> topics,
            @NonNull final DumpCheckpoint checkpoint) {
        try (@NonNull final var writer = new Writer(path)) {
            final var dumpableTopics = gatherTopics(topics);
            reportOnTopics(writer, dumpableTopics);
            System.out.printf(
                    "=== mod topics report is %d bytes at checkpoint %s%n", writer.getSize(), checkpoint.name());
        }
    }

    private static Map<Long, BBMTopic> gatherTopics(
            @NonNull final VirtualMap<OnDiskKey<TopicID>, OnDiskValue<Topic>> topicsStore) {
        final var r = new TreeMap<Long, BBMTopic>();
        final var threadCount = 8;
        final var mappings = new ConcurrentLinkedQueue<Pair<Long, BBMTopic>>();
        try {
            VirtualMapLike.from(topicsStore)
                    .extractVirtualMapDataC(
                            getStaticThreadManager(),
                            p -> {
                                try {
                                    mappings.add(Pair.of(
                                            Long.valueOf(p.left().getKey().topicNum()),
                                            fromMod(p.right().getValue())));
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

    static BBMTopic fromMod(@NonNull final com.hedera.hapi.node.state.consensus.Topic topic) {
        return new BBMTopic(
                (int) topic.topicId().topicNum(),
                topic.memo(),
                topic.expirationSecond(),
                topic.deleted(),
                (JKey) fromPbjKey(topic.adminKey()).orElse(null),
                (JKey) fromPbjKey(topic.submitKey()).orElse(null),
                topic.runningHash().toByteArray(),
                topic.sequenceNumber(),
                topic.autoRenewPeriod(),
                EntityId.fromNum(topic.autoRenewAccountId().accountNum()));
    }
}
