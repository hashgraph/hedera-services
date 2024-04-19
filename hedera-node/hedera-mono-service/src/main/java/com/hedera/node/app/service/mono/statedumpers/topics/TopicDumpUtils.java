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

package com.hedera.node.app.service.mono.statedumpers.topics;

import com.hedera.node.app.service.mono.state.adapters.MerkleMapLike;
import com.hedera.node.app.service.mono.state.merkle.MerkleTopic;
import com.hedera.node.app.service.mono.statedumpers.DumpCheckpoint;
import com.hedera.node.app.service.mono.statedumpers.utils.FieldBuilder;
import com.hedera.node.app.service.mono.statedumpers.utils.ThingsToStrings;
import com.hedera.node.app.service.mono.statedumpers.utils.Writer;
import com.hedera.node.app.service.mono.utils.EntityNum;
import com.swirlds.base.utility.Pair;
import com.swirlds.merkle.map.MerkleMap;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
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

    public static void dumpMonoTopics(
            @NonNull final Path path,
            @NonNull final MerkleMap<EntityNum, MerkleTopic> topics,
            @NonNull final DumpCheckpoint checkpoint) {
        try (@NonNull final var writer = new Writer(path)) {
            final var dumpableTopics = gatherTopics(MerkleMapLike.from(topics));
            reportOnTopics(writer, dumpableTopics);
            System.out.printf(
                    "=== mono topics report is %d bytes at checkpoint %s%n", writer.getSize(), checkpoint.name());
        }
    }

    private static Map<Long, BBMTopic> gatherTopics(@NonNull final MerkleMapLike<EntityNum, MerkleTopic> topicsStore) {
        final var allTopics = new TreeMap<Long, BBMTopic>();
        topicsStore.forEachNode((en, mt) -> allTopics.put(en.longValue(), new BBMTopic(mt)));
        return allTopics;
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
