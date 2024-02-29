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

import static java.util.Objects.requireNonNull;

import com.hedera.hapi.node.state.throttles.ThrottleUsageSnapshot;
import com.hedera.node.app.service.mono.pbj.PbjConverter;
import com.hedera.node.app.service.mono.state.merkle.MerkleNetworkContext;
import com.hedera.node.app.service.mono.state.submerkle.RichInstant;
import com.hedera.services.cli.utils.FieldBuilder;
import com.hedera.services.cli.utils.ThingsToStrings;
import com.hedera.services.cli.utils.Writer;
import com.swirlds.base.utility.Pair;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Dump congestion from a signed state file to a text file in a deterministic order  */
public class DumpCongestionSubcommand {

    static void doit(@NonNull final SignedStateHolder state, @NonNull final Path congestionInfoPath) {
        new DumpCongestionSubcommand(state, congestionInfoPath).doit();
    }

    @NonNull
    final SignedStateHolder state;

    @NonNull
    final Path congestionInfoPath;

    DumpCongestionSubcommand(@NonNull final SignedStateHolder state, @NonNull final Path congestionInfoPath) {
        requireNonNull(state, "state");
        requireNonNull(congestionInfoPath, "congestionInfoPath");

        this.state = state;
        this.congestionInfoPath = congestionInfoPath;
    }

    void doit() {
        final var networkContext = state.getNetworkContext();
        System.out.printf("=== congestion ===%n");

        final var congestion = Congestion.fromMerkleNetworkContext(networkContext);

        int reportSize;
        try (@NonNull final var writer = new Writer(congestionInfoPath)) {
            reportOnCongestion(writer, congestion);
            reportSize = writer.getSize();
        }

        System.out.printf("=== congestion report is %d bytes%n", reportSize);
    }

    @SuppressWarnings(
            "java:S6218") // "Equals/hashcode method should be overridden in records containing array fields" - this
    record Congestion(
            @Nullable List<ThrottleUsageSnapshot> tpsThrottles,
            @Nullable ThrottleUsageSnapshot gasThrottle,

            // last two represented as Strings already formatted from List<RichInstant>
            @Nullable String genericLevelStarts,
            @Nullable String gasLevelStarts) {
        static Congestion fromMerkleNetworkContext(@NonNull final MerkleNetworkContext networkContext) {
            final var tpsThrottleUsageSnapshots = Arrays.stream(networkContext.usageSnapshots())
                    .map(PbjConverter::toPbj)
                    .toList();
            final var gasThrottleUsageSnapshot = PbjConverter.toPbj(networkContext.getGasThrottleUsageSnapshot());
            // format the following two from `List<RichInstant>` to String
            final var gasCongestionStarts = Arrays.stream(
                            networkContext.getMultiplierSources().gasCongestionStarts())
                    .map(RichInstant::fromJava)
                    .map(ThingsToStrings::toStringOfRichInstant)
                    .collect(Collectors.joining(", "));
            final var genericCongestionStarts = Arrays.stream(
                            networkContext.getMultiplierSources().genericCongestionStarts())
                    .map(RichInstant::fromJava)
                    .map(ThingsToStrings::toStringOfRichInstant)
                    .collect(Collectors.joining(", "));

            return new Congestion(
                    tpsThrottleUsageSnapshots, gasThrottleUsageSnapshot, genericCongestionStarts, gasCongestionStarts);
        }
    }

    void reportOnCongestion(@NonNull Writer writer, @NonNull Congestion congestion) {
        writer.writeln(formatHeader());
        formatCongestion(writer, congestion);
        writer.writeln("");
    }

    void formatCongestion(@NonNull final Writer writer, @NonNull final Congestion congestion) {
        final var fb = new FieldBuilder(FIELD_SEPARATOR);
        fieldFormatters.stream().map(Pair::right).forEach(ff -> ff.accept(fb, congestion));
        writer.writeln(fb);
    }

    @NonNull
    String formatHeader() {
        return fieldFormatters.stream().map(Pair::left).collect(Collectors.joining(FIELD_SEPARATOR));
    }

    static final String FIELD_SEPARATOR = ";";

    @NonNull
    static List<Pair<String, BiConsumer<FieldBuilder, Congestion>>> fieldFormatters = List.of(
            Pair.of(
                    "tpsThrottles",
                    getFieldFormatter(Congestion::tpsThrottles, getNullableFormatter(Object::toString))),
            Pair.of("gasThrottle", getFieldFormatter(Congestion::gasThrottle, getNullableFormatter(Object::toString))),
            Pair.of(
                    "genericLevelStarts",
                    getFieldFormatter(Congestion::genericLevelStarts, getNullableFormatter(Object::toString))),
            Pair.of(
                    "gasLevelStarts",
                    getFieldFormatter(Congestion::gasLevelStarts, getNullableFormatter(Object::toString))));

    static <T> BiConsumer<FieldBuilder, Congestion> getFieldFormatter(
            @NonNull final Function<Congestion, T> fun, @NonNull final Function<T, String> formatter) {
        return (fb, t) -> formatField(fb, t, fun, formatter);
    }

    static <T> Function<T, String> getNullableFormatter(@NonNull final Function<T, String> formatter) {
        return t -> null != t ? formatter.apply(t) : "";
    }

    static <T> void formatField(
            @NonNull final FieldBuilder fb,
            @NonNull final Congestion congestion,
            @NonNull final Function<Congestion, T> fun,
            @NonNull final Function<T, String> formatter) {
        fb.append(formatter.apply(fun.apply(congestion)));
    }
}
