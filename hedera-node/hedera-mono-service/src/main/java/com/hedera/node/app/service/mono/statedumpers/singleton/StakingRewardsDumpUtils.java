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

public class StakingRewardsDumpUtils {

    static final String FIELD_SEPARATOR = ";";
    static Function<Boolean, String> booleanFormatter = b -> b ? "T" : "";

    @NonNull
    static List<Pair<String, BiConsumer<FieldBuilder, BBMStakingRewards>>> stakingRewardFieldFormatters = List.of(
            Pair.of(
                    "stakingRewardsActivated",
                    getFieldFormatter(BBMStakingRewards::stakingRewardsActivated, booleanFormatter)),
            Pair.of(
                    "totalStakedRewardStart",
                    getFieldFormatter(BBMStakingRewards::totalStakedRewardStart, Object::toString)),
            Pair.of("totalStakedStart", getFieldFormatter(BBMStakingRewards::totalStakedStart, Object::toString)),
            Pair.of("pendingRewards", getFieldFormatter(BBMStakingRewards::pendingRewards, Object::toString)));

    public static void dumpMonoStakingRewards(
            @NonNull final Path path,
            @NonNull final MerkleNetworkContext merkleNetworkContext,
            @NonNull final DumpCheckpoint checkpoint) {

        int reportSize;
        try (@NonNull final var writer = new Writer(path)) {
            reportOnStakingRewards(writer, BBMStakingRewards.fromMono(merkleNetworkContext));
            reportSize = writer.getSize();
        }

        System.out.printf("=== staking rewards report is %d bytes %n", reportSize);
    }

    static void reportOnStakingRewards(@NonNull Writer writer, @NonNull BBMStakingRewards stakingRewards) {
        writer.writeln(formatHeader());
        formatStakingRewards(writer, stakingRewards);
        writer.writeln("");
    }

    public static void formatStakingRewards(
            @NonNull final Writer writer, @NonNull final BBMStakingRewards stakingRewards) {
        final var fb = new FieldBuilder(FIELD_SEPARATOR);
        stakingRewardFieldFormatters.stream().map(Pair::right).forEach(ff -> ff.accept(fb, stakingRewards));
        writer.writeln(fb);
    }

    @NonNull
    public static String formatHeader() {
        return stakingRewardFieldFormatters.stream().map(Pair::left).collect(Collectors.joining(FIELD_SEPARATOR));
    }

    static <T> BiConsumer<FieldBuilder, BBMStakingRewards> getFieldFormatter(
            @NonNull final Function<BBMStakingRewards, T> fun, @NonNull final Function<T, String> formatter) {
        return (fb, t) -> formatField(fb, t, fun, formatter);
    }

    static <T> void formatField(
            @NonNull final FieldBuilder fb,
            @NonNull final BBMStakingRewards stakingRewards,
            @NonNull final Function<BBMStakingRewards, T> fun,
            @NonNull final Function<T, String> formatter) {
        fb.append(formatter.apply(fun.apply(stakingRewards)));
    }
}
