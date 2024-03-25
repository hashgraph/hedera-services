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

package com.hedera.node.app.service.mono.statedumpers.singleton;

import com.hedera.node.app.service.mono.state.merkle.MerkleNetworkContext;
import com.hedera.node.app.service.mono.statedumpers.DumpCheckpoint;
import com.hedera.node.app.service.mono.statedumpers.utils.FieldBuilder;
import com.hedera.node.app.service.mono.statedumpers.utils.Writer;
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
    static List<Pair<String, BiConsumer<FieldBuilder, BBMCongestion>>> fieldFormatters = List.of(
            Pair.of(
                    "tpsThrottles",
                    getFieldFormatter(BBMCongestion::tpsThrottles, getNullableFormatter(Object::toString))),
            Pair.of(
                    "gasThrottle",
                    getFieldFormatter(BBMCongestion::gasThrottle, getNullableFormatter(Object::toString))),
            Pair.of(
                    "genericLevelStarts",
                    getFieldFormatter(BBMCongestion::genericLevelStarts, getNullableFormatter(Object::toString))),
            Pair.of(
                    "gasLevelStarts",
                    getFieldFormatter(BBMCongestion::gasLevelStarts, getNullableFormatter(Object::toString))));

    public static void dumpMonoCongestion(
            @NonNull final Path path,
            @NonNull final MerkleNetworkContext merkleNetworkContext,
            @NonNull final DumpCheckpoint checkpoint) {

        int reportSize;
        try (@NonNull final var writer = new Writer(path)) {
            reportOnCongestion(writer, BBMCongestion.fromMerkleNetworkContext(merkleNetworkContext));
            reportSize = writer.getSize();
        }

        System.out.printf("=== staking rewards report is %d bytes %n", reportSize);
    }

    public static void reportOnCongestion(@NonNull Writer writer, @NonNull BBMCongestion congestion) {
        writer.writeln(formatHeader());
        formatCongestion(writer, congestion);
        writer.writeln("");
    }

    static void formatCongestion(@NonNull final Writer writer, @NonNull final BBMCongestion congestion) {
        final var fb = new FieldBuilder(FIELD_SEPARATOR);
        fieldFormatters.stream().map(Pair::right).forEach(ff -> ff.accept(fb, congestion));
        writer.writeln(fb);
    }

    @NonNull
    static String formatHeader() {
        return fieldFormatters.stream().map(Pair::left).collect(Collectors.joining(FIELD_SEPARATOR));
    }

    private static <T> BiConsumer<FieldBuilder, BBMCongestion> getFieldFormatter(
            @NonNull final Function<BBMCongestion, T> fun, @NonNull final Function<T, String> formatter) {
        return (fb, t) -> formatField(fb, t, fun, formatter);
    }

    static <T> void formatField(
            @NonNull final FieldBuilder fb,
            @NonNull final BBMCongestion stakingRewards,
            @NonNull final Function<BBMCongestion, T> fun,
            @NonNull final Function<T, String> formatter) {
        fb.append(formatter.apply(fun.apply(stakingRewards)));
    }

    static <T> Function<T, String> getNullableFormatter(@NonNull final Function<T, String> formatter) {
        return t -> null != t ? formatter.apply(t) : "";
    }
}
