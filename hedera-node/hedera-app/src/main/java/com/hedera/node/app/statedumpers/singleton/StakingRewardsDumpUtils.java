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

package com.hedera.node.app.statedumpers.singleton;

import com.hedera.node.app.service.mono.statedumpers.DumpCheckpoint;
import com.hedera.node.app.service.mono.statedumpers.singleton.BBMStakingRewards;
import com.hedera.node.app.service.mono.statedumpers.utils.Writer;
import com.hedera.node.app.statedumpers.utils.FieldBuilder;
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
    static List<Pair<String, BiConsumer<FieldBuilder, BBMStakingRewards>>> fieldFormatters = List.of(
            Pair.of(
                    "BBMStakingRewardsActivated",
                    getFieldFormatter(BBMStakingRewards::stakingRewardsActivated, booleanFormatter)),
            Pair.of(
                    "totalStakedRewardStart",
                    getFieldFormatter(BBMStakingRewards::totalStakedRewardStart, Object::toString)),
            Pair.of("totalStakedStart", getFieldFormatter(BBMStakingRewards::totalStakedStart, Object::toString)),
            Pair.of("pendingRewards", getFieldFormatter(BBMStakingRewards::pendingRewards, Object::toString)));

    public static void dumpModBBMStakingRewards(
            @NonNull final Path path,
            @NonNull final BBMStakingRewards stakingRewards,
            @NonNull final DumpCheckpoint checkpoint) {
        int reportSize;
        try (@NonNull final var writer = new Writer(path)) {
            reportOnBBMStakingRewards(writer, fromMod(stakingRewards));
            reportSize = writer.getSize();
        }

        System.out.printf("=== staking rewards report is %d bytes %n", reportSize);
    }

    static void reportOnBBMStakingRewards(@NonNull Writer writer, @NonNull BBMStakingRewards BBMStakingRewards) {
        writer.writeln(formatHeader());
        formatBBMStakingRewards(writer, BBMStakingRewards);
        writer.writeln("");
    }

    static void formatBBMStakingRewards(
            @NonNull final Writer writer, @NonNull final BBMStakingRewards BBMStakingRewards) {
        final var fb = new FieldBuilder(FIELD_SEPARATOR);
        fieldFormatters.stream().map(Pair::right).forEach(ff -> ff.accept(fb, BBMStakingRewards));
        writer.writeln(fb);
    }

    @NonNull
    static String formatHeader() {
        return fieldFormatters.stream().map(Pair::left).collect(Collectors.joining(FIELD_SEPARATOR));
    }

    static <T> BiConsumer<FieldBuilder, BBMStakingRewards> getFieldFormatter(
            @NonNull final Function<BBMStakingRewards, T> fun, @NonNull final Function<T, String> formatter) {
        return (fb, t) -> formatField(fb, t, fun, formatter);
    }

    static <T> void formatField(
            @NonNull final FieldBuilder fb,
            @NonNull final BBMStakingRewards BBMStakingRewards,
            @NonNull final Function<BBMStakingRewards, T> fun,
            @NonNull final Function<T, String> formatter) {
        fb.append(formatter.apply(fun.apply(BBMStakingRewards)));
    }

    public static BBMStakingRewards fromMod(@NonNull final BBMStakingRewards networkBBMStakingRewards) {
        return new BBMStakingRewards(
                networkBBMStakingRewards.stakingRewardsActivated(),
                networkBBMStakingRewards.totalStakedRewardStart(),
                networkBBMStakingRewards.totalStakedStart(),
                networkBBMStakingRewards.pendingRewards());
    }
}
