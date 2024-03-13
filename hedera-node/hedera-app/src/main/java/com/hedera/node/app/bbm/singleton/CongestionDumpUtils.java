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

package com.hedera.node.app.bbm.singleton;

import com.hedera.hapi.node.state.congestion.CongestionLevelStarts;
import com.hedera.hapi.node.state.throttles.ThrottleUsageSnapshots;
import com.hedera.node.app.bbm.DumpCheckpoint;
import com.hedera.node.app.bbm.utils.FieldBuilder;
import com.hedera.node.app.bbm.utils.Writer;
import com.hedera.node.app.service.mono.state.merkle.MerkleNetworkContext;
import com.swirlds.base.utility.Pair;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.nio.file.Path;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.stream.Collectors;

public class CongestionDumpUtils {

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

    public static void dumpMonoCongestion(
            @NonNull final Path path,
            @NonNull final MerkleNetworkContext merkleNetworkContext,
            @NonNull final DumpCheckpoint checkpoint) {

        int reportSize;
        try (@NonNull final var writer = new Writer(path)) {
            reportOnCongestion(writer, Congestion.fromMerkleNetworkContext(merkleNetworkContext));
            reportSize = writer.getSize();
        }

        System.out.printf("=== staking rewards report is %d bytes %n", reportSize);
    }

    public static void dumpModCongestion(
            @NonNull final Path path,
            @NonNull final CongestionLevelStarts congestionLevelStarts,
            @NonNull final ThrottleUsageSnapshots throttleUsageSnapshots,
            @NonNull final DumpCheckpoint checkpoint) {
        int reportSize;
        try (@NonNull final var writer = new Writer(path)) {
            reportOnCongestion(writer, Congestion.fromMod(congestionLevelStarts, throttleUsageSnapshots));
            reportSize = writer.getSize();
        }

        System.out.printf("=== staking rewards report is %d bytes %n", reportSize);
    }

    static void reportOnCongestion(@NonNull Writer writer, @NonNull Congestion congestion) {
        writer.writeln(formatHeader());
        formatCongestion(writer, congestion);
        writer.writeln("");
    }

    static void formatCongestion(@NonNull final Writer writer, @NonNull final Congestion congestion) {
        final var fb = new FieldBuilder(FIELD_SEPARATOR);
        fieldFormatters.stream().map(Pair::right).forEach(ff -> ff.accept(fb, congestion));
        writer.writeln(fb);
    }

    @NonNull
    static String formatHeader() {
        return fieldFormatters.stream().map(Pair::left).collect(Collectors.joining(FIELD_SEPARATOR));
    }

    private static <T> BiConsumer<FieldBuilder, Congestion> getFieldFormatter(
            @NonNull final Function<Congestion, T> fun, @NonNull final Function<T, String> formatter) {
        return (fb, t) -> formatField(fb, t, fun, formatter);
    }

    static <T> void formatField(
            @NonNull final FieldBuilder fb,
            @NonNull final Congestion stakingRewards,
            @NonNull final Function<Congestion, T> fun,
            @NonNull final Function<T, String> formatter) {
        fb.append(formatter.apply(fun.apply(stakingRewards)));
    }

    static <T> Function<T, String> getNullableFormatter(@NonNull final Function<T, String> formatter) {
        return t -> null != t ? formatter.apply(t) : "";
    }
}
