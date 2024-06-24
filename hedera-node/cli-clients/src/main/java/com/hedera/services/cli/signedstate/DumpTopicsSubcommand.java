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

import static com.hedera.services.cli.utils.ThingsToStrings.quoteForCsv;
import static java.util.Objects.requireNonNull;

import com.hedera.hapi.node.state.consensus.Topic;
import com.hedera.services.cli.signedstate.DumpStateCommand.EmitSummary;
import com.hedera.services.cli.signedstate.SignedStateCommand.Verbosity;
import com.hedera.services.cli.utils.FieldBuilder;
import com.hedera.services.cli.utils.Writer;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.nio.file.Path;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Function;

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
        System.out.printf("=== %d topics ===%n", 0);

        final var allTopics = gatherTopics();

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
    @NonNull
    Map<Long, Topic> gatherTopics() {
        return Map.of();
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
        writer.writeln(fb);
    }

    @NonNull
    String formatHeader() {
        return "";
    }

    static final String FIELD_SEPARATOR = ";";
    static Function<Boolean, String> booleanFormatter = b -> b ? "T" : "";
    static Function<String, String> csvQuote = s -> quoteForCsv(FIELD_SEPARATOR, s);

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
