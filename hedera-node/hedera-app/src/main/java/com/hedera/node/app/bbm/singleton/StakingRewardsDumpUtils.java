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

import com.hedera.hapi.node.state.token.NetworkStakingRewards;
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

public class StakingRewardsDumpUtils {

    static final String FIELD_SEPARATOR = ";";
    static Function<Boolean, String> booleanFormatter = b -> b ? "T" : "";

    @NonNull
    static List<Pair<String, BiConsumer<FieldBuilder, StakingRewards>>> fieldFormatters = List.of(
            Pair.of(
                    "stakingRewardsActivated",
                    getFieldFormatter(StakingRewards::stakingRewardsActivated, booleanFormatter)),
            Pair.of(
                    "totalStakedRewardStart",
                    getFieldFormatter(StakingRewards::totalStakedRewardStart, Object::toString)),
            Pair.of("totalStakedStart", getFieldFormatter(StakingRewards::totalStakedStart, Object::toString)),
            Pair.of("pendingRewards", getFieldFormatter(StakingRewards::pendingRewards, Object::toString)));

    public static void dumpMonoStakingRewards(
            @NonNull final Path path,
            @NonNull final MerkleNetworkContext merkleNetworkContext,
            @NonNull final DumpCheckpoint checkpoint) {

        int reportSize;
        try (@NonNull final var writer = new Writer(path)) {
            reportOnStakingRewards(writer, StakingRewards.fromMono(merkleNetworkContext));
            reportSize = writer.getSize();
        }

        System.out.printf("=== staking rewards report is %d bytes %n", reportSize);
    }

    public static void dumpModStakingRewards(
            @NonNull final Path path,
            @NonNull final NetworkStakingRewards stakingRewards,
            @NonNull final DumpCheckpoint checkpoint) {
        int reportSize;
        try (@NonNull final var writer = new Writer(path)) {
            reportOnStakingRewards(writer, StakingRewards.fromMod(stakingRewards));
            reportSize = writer.getSize();
        }

        System.out.printf("=== staking rewards report is %d bytes %n", reportSize);
    }

    static void reportOnStakingRewards(@NonNull Writer writer, @NonNull StakingRewards stakingRewards) {
        writer.writeln(formatHeader());
        formatStakingRewards(writer, stakingRewards);
        writer.writeln("");
    }

    static void formatStakingRewards(@NonNull final Writer writer, @NonNull final StakingRewards stakingRewards) {
        final var fb = new FieldBuilder(FIELD_SEPARATOR);
        fieldFormatters.stream().map(Pair::right).forEach(ff -> ff.accept(fb, stakingRewards));
        writer.writeln(fb);
    }

    @NonNull
    static String formatHeader() {
        return fieldFormatters.stream().map(Pair::left).collect(Collectors.joining(FIELD_SEPARATOR));
    }

    static <T> BiConsumer<FieldBuilder, StakingRewards> getFieldFormatter(
            @NonNull final Function<StakingRewards, T> fun, @NonNull final Function<T, String> formatter) {
        return (fb, t) -> formatField(fb, t, fun, formatter);
    }

    static <T> void formatField(
            @NonNull final FieldBuilder fb,
            @NonNull final StakingRewards stakingRewards,
            @NonNull final Function<StakingRewards, T> fun,
            @NonNull final Function<T, String> formatter) {
        fb.append(formatter.apply(fun.apply(stakingRewards)));
    }
}
