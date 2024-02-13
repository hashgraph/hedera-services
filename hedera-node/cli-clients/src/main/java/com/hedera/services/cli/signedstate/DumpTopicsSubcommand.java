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

package com.hedera.services.cli.signedstate;

import static com.hedera.services.cli.utils.Formatters.getNullableFormatter;
import static com.hedera.services.cli.utils.ThingsToStrings.getMaybeStringifyByteString;
import static com.hedera.services.cli.utils.ThingsToStrings.quoteForCsv;
import static java.util.Objects.requireNonNull;

import com.hedera.node.app.service.mono.legacy.core.jproto.JKey;
import com.hedera.node.app.service.mono.state.adapters.MerkleMapLike;
import com.hedera.node.app.service.mono.state.merkle.MerkleTopic;
import com.hedera.node.app.service.mono.state.submerkle.EntityId;
import com.hedera.node.app.service.mono.state.submerkle.RichInstant;
import com.hedera.node.app.service.mono.utils.EntityNum;
import com.hedera.services.cli.signedstate.DumpStateCommand.EmitSummary;
import com.hedera.services.cli.signedstate.SignedStateCommand.Verbosity;
import com.hedera.services.cli.utils.FieldBuilder;
import com.hedera.services.cli.utils.ThingsToStrings;
import com.hedera.services.cli.utils.Writer;
import com.swirlds.base.utility.Pair;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Dump all topics from a signed state file to a text file in a deterministic order  */
public class DumpTopicsSubcommand {

    static void doit(
            @NonNull final SignedStateHolder state,
            @NonNull final Path topicsPath,
            @NonNull final EmitSummary emitSummary,
            @NonNull final Verbosity verbosity) {
        new DumpTopicsSubcommand(state, topicsPath, emitSummary, verbosity).doit();
    }

    @NonNull
    final SignedStateHolder state;

    @NonNull
    final Path topicsPath;

    @NonNull
    final EmitSummary emitSummary;

    @NonNull
    final Verbosity verbosity;

    DumpTopicsSubcommand(
            @NonNull final SignedStateHolder state,
            @NonNull final Path topicsPath,
            @NonNull final EmitSummary emitSummary,
            @NonNull final Verbosity verbosity) {
        requireNonNull(state, "state");
        requireNonNull(topicsPath, "topicsPath");
        requireNonNull(emitSummary, "emitSummary");
        requireNonNull(verbosity, "verbosity");

        this.state = state;
        this.topicsPath = topicsPath;
        this.emitSummary = emitSummary;
        this.verbosity = verbosity;
    }

    void doit() {
        final var topicsStore = state.getTopics();
        System.out.printf("=== %d topics ===%n", topicsStore.size());

        final var allTopics = gatherTopics(topicsStore);

        int reportSize;
        try (@NonNull final var writer = new Writer(topicsPath)) {
            if (emitSummary == EmitSummary.YES) reportSummary(writer, allTopics);
            reportOnTopics(writer, allTopics);
            reportSize = writer.getSize();
        }

        System.out.printf("=== topics report is %d bytes%n", reportSize);
    }

    @SuppressWarnings(
            "java:S6218") // "Equals/hashcode method should be overridden in records containing array fields" - this
    record Topic(
            int number,
            @NonNull String memo,
            @NonNull RichInstant expirationTimestamp,
            boolean deleted,
            @NonNull JKey adminKey,
            @NonNull JKey submitKey,
            @NonNull byte[] runningHash,
            long sequenceNumber,
            long autoRenewDurationSeconds,
            @Nullable EntityId autoRenewAccountId) {
        Topic(@NonNull final MerkleTopic topic) {
            this(
                    topic.getKey().intValue(),
                    topic.getMemo(),
                    topic.getExpirationTimestamp(),
                    topic.isDeleted(),
                    topic.getAdminKey(),
                    topic.getSubmitKey(),
                    null != topic.getRunningHash() ? topic.getRunningHash() : EMPTY_BYTES,
                    topic.getSequenceNumber(),
                    topic.getAutoRenewDurationSeconds(),
                    topic.getAutoRenewAccountId());
            Objects.requireNonNull(memo, "memo");
            Objects.requireNonNull(adminKey, "adminKey");
            Objects.requireNonNull(submitKey, "submitKey");
            Objects.requireNonNull(runningHash, "runningHash");
        }

        static final byte[] EMPTY_BYTES = new byte[0];
    }

    @NonNull
    Map<Long, Topic> gatherTopics(@NonNull final MerkleMapLike<EntityNum, MerkleTopic> topicsStore) {
        final var allTopics = new TreeMap<Long, Topic>();
        topicsStore.forEachNode((en, mt) -> allTopics.put(en.longValue(), new Topic(mt)));
        return allTopics;
    }

    void reportSummary(@NonNull Writer writer, @NonNull Map<Long, Topic> topics) {
        writer.writeln("=== %7d: topics".formatted(topics.size()));
        writer.writeln("");
    }

    void reportOnTopics(@NonNull Writer writer, @NonNull Map<Long, Topic> topics) {
        writer.writeln(formatHeader());
        topics.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(e -> formatTopic(writer, e.getValue()));
        writer.writeln("");
    }

    void formatTopic(@NonNull final Writer writer, @NonNull final Topic topic) {
        final var fb = new FieldBuilder(FIELD_SEPARATOR);
        fieldFormatters.stream().map(Pair::right).forEach(ff -> ff.accept(fb, topic));
        writer.writeln(fb);
    }

    @NonNull
    String formatHeader() {
        return fieldFormatters.stream().map(Pair::left).collect(Collectors.joining(FIELD_SEPARATOR));
    }

    static final String FIELD_SEPARATOR = ";";
    static Function<Boolean, String> booleanFormatter = b -> b ? "T" : "";
    static Function<String, String> csvQuote = s -> quoteForCsv(FIELD_SEPARATOR, s);

    @NonNull
    static List<Pair<String, BiConsumer<FieldBuilder, Topic>>> fieldFormatters = List.of(
            Pair.of("number", getFieldFormatter(Topic::number, Object::toString)),
            Pair.of("memo", getFieldFormatter(Topic::memo, csvQuote)),
            Pair.of("expiry", getFieldFormatter(Topic::expirationTimestamp, ThingsToStrings::toStringOfRichInstant)),
            Pair.of("deleted", getFieldFormatter(Topic::deleted, booleanFormatter)),
            Pair.of(
                    "adminKey",
                    getFieldFormatter(Topic::adminKey, getNullableFormatter(ThingsToStrings::toStringOfJKey))),
            Pair.of(
                    "submitKey",
                    getFieldFormatter(Topic::submitKey, getNullableFormatter(ThingsToStrings::toStringOfJKey))),
            Pair.of("runningHash", getFieldFormatter(Topic::runningHash, getMaybeStringifyByteString(FIELD_SEPARATOR))),
            Pair.of("sequenceNumber", getFieldFormatter(Topic::sequenceNumber, Object::toString)),
            Pair.of("autoRenewSecs", getFieldFormatter(Topic::autoRenewDurationSeconds, Object::toString)),
            Pair.of(
                    "autoRenewAccount",
                    getFieldFormatter(
                            Topic::autoRenewAccountId, getNullableFormatter(ThingsToStrings::toStringOfEntityId))));

    static <T> BiConsumer<FieldBuilder, Topic> getFieldFormatter(
            @NonNull final Function<Topic, T> fun, @NonNull final Function<T, String> formatter) {
        return (fb, t) -> formatField(fb, t, fun, formatter);
    }

    static <T> void formatField(
            @NonNull final FieldBuilder fb,
            @NonNull final Topic topic,
            @NonNull final Function<Topic, T> fun,
            @NonNull final Function<T, String> formatter) {
        fb.append(formatter.apply(fun.apply(topic)));
    }
}
