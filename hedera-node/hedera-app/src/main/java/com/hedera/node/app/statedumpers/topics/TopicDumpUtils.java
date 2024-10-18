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

import static com.hedera.node.app.statedumpers.utils.LegacyTypeUtils.fromPbjKey;
import static com.swirlds.common.threading.manager.AdHocThreadManager.getStaticThreadManager;

import com.hedera.hapi.node.base.TopicID;
import com.hedera.hapi.node.state.consensus.Topic;
import com.hedera.node.app.statedumpers.DumpCheckpoint;
import com.hedera.node.app.statedumpers.legacy.EntityId;
import com.hedera.node.app.statedumpers.legacy.JKey;
import com.hedera.node.app.statedumpers.utils.FieldBuilder;
import com.hedera.node.app.statedumpers.utils.ThingsToStrings;
import com.hedera.node.app.statedumpers.utils.Writer;
import com.swirlds.base.utility.Pair;
import com.swirlds.state.merkle.vmapsupport.OnDiskKey;
import com.swirlds.state.merkle.vmapsupport.OnDiskValue;
import com.swirlds.virtualmap.VirtualMap;
import com.swirlds.virtualmap.VirtualMapMigration;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.stream.Collectors;

public class TopicDumpUtils {
    private static final String FIELD_SEPARATOR = ";";
    private static final Function<Boolean, String> booleanFormatter = b -> b ? "T" : "";
    private static final Function<String, String> csvQuote = s -> ThingsToStrings.quoteForCsv(FIELD_SEPARATOR, s);

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
            VirtualMapMigration.extractVirtualMapDataC(
                    getStaticThreadManager(),
                    topicsStore,
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
                topic.hasAutoRenewAccountId()
                        ? EntityId.fromNum(topic.autoRenewAccountId().accountNum())
                        : null);
    }

    public static void reportOnTopics(@NonNull Writer writer, @NonNull Map<Long, BBMTopic> topics) {
        writer.writeln(formatBBMTopicHeader());
        topics.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(e -> formatTopic(writer, e.getValue()));
        writer.writeln("");
    }

    @NonNull
    public static String formatBBMTopicHeader() {
        return bbmTopicFieldFormatters.stream().map(Pair::left).collect(Collectors.joining(FIELD_SEPARATOR));
    }

    @NonNull
    public static List<Pair<String, BiConsumer<FieldBuilder, BBMTopic>>> bbmTopicFieldFormatters = List.of(
            Pair.of("number", getFieldFormatter(BBMTopic::number, Object::toString)),
            Pair.of("memo", getFieldFormatter(BBMTopic::memo, csvQuote)),
            Pair.of("expiry", getFieldFormatter(BBMTopic::expirationSeconds, Object::toString)),
            Pair.of("deleted", getFieldFormatter(BBMTopic::deleted, booleanFormatter)),
            Pair.of(
                    "adminKey",
                    getFieldFormatter(BBMTopic::adminKey, getNullableFormatter(ThingsToStrings::toStringOfJKey))),
            Pair.of(
                    "submitKey",
                    getFieldFormatter(BBMTopic::submitKey, getNullableFormatter(ThingsToStrings::toStringOfJKey))),
            Pair.of(
                    "runningHash",
                    getFieldFormatter(
                            BBMTopic::runningHash, ThingsToStrings.getMaybeStringifyByteString(FIELD_SEPARATOR))),
            Pair.of("sequenceNumber", getFieldFormatter(BBMTopic::sequenceNumber, Object::toString)),
            Pair.of("autoRenewSecs", getFieldFormatter(BBMTopic::autoRenewDurationSeconds, Object::toString)),
            Pair.of(
                    "autoRenewAccount",
                    getFieldFormatter(
                            BBMTopic::autoRenewAccountId, getNullableFormatter(ThingsToStrings::toStringOfEntityId))));

    private static <T> BiConsumer<FieldBuilder, BBMTopic> getFieldFormatter(
            @NonNull final Function<BBMTopic, T> fun, @NonNull final Function<T, String> formatter) {
        return (fb, t) -> formatField(fb, t, fun, formatter);
    }

    private static <T> void formatField(
            @NonNull final FieldBuilder fb,
            @NonNull final BBMTopic topic,
            @NonNull final Function<BBMTopic, T> fun,
            @NonNull final Function<T, String> formatter) {
        fb.append(formatter.apply(fun.apply(topic)));
    }

    private static <T> Function<T, String> getNullableFormatter(@NonNull final Function<T, String> formatter) {
        return t -> null != t ? formatter.apply(t) : "";
    }

    public static void formatTopic(@NonNull final Writer writer, @NonNull final BBMTopic topic) {
        final var fb = new FieldBuilder(FIELD_SEPARATOR);
        bbmTopicFieldFormatters.stream().map(Pair::right).forEach(ff -> ff.accept(fb, topic));
        writer.writeln(fb);
    }
}
