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

import com.hedera.node.app.service.mono.state.adapters.MerkleMapLike;
import com.hedera.node.app.service.mono.state.merkle.MerkleStakingInfo;
import com.hedera.node.app.service.mono.statedumpers.DumpCheckpoint;
import com.hedera.node.app.service.mono.statedumpers.utils.FieldBuilder;
import com.hedera.node.app.service.mono.statedumpers.utils.Writer;
import com.hedera.node.app.service.mono.utils.EntityNum;
import com.swirlds.base.utility.Pair;
import com.swirlds.merkle.map.MerkleMap;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.stream.Collectors;

public class StakingInfoDumpUtils {

    static final String FIELD_SEPARATOR = ";";

    @NonNull
    public static List<Pair<String, BiConsumer<FieldBuilder, BBMStakingInfo>>> stakingInfoFieldFormatters = List.of(
            Pair.of("number", getFieldFormatter(BBMStakingInfo::number, Object::toString)),
            Pair.of("minStake", getFieldFormatter(BBMStakingInfo::minStake, Object::toString)),
            Pair.of("maxStake", getFieldFormatter(BBMStakingInfo::maxStake, Object::toString)),
            Pair.of("stakeToReward", getFieldFormatter(BBMStakingInfo::stakeToReward, Object::toString)),
            Pair.of("stakeToNotReward", getFieldFormatter(BBMStakingInfo::stakeToNotReward, Object::toString)),
            Pair.of("stakeRewardStart", getFieldFormatter(BBMStakingInfo::stakeRewardStart, Object::toString)),
            Pair.of(
                    "unclaimedStakeRewardStart",
                    getFieldFormatter(BBMStakingInfo::unclaimedStakeRewardStart, Object::toString)),
            Pair.of("stake", getFieldFormatter(BBMStakingInfo::stake, Object::toString)),
            Pair.of("rewardSumHistory", getFieldFormatter(BBMStakingInfo::rewardSumHistory, Arrays::toString)),
            Pair.of("weight", getFieldFormatter(BBMStakingInfo::weight, Object::toString)));

    public static void dumpMonoStakingInfo(
            @NonNull final Path path,
            @NonNull final MerkleMap<EntityNum, MerkleStakingInfo> stakingInfoMerkleMap,
            @NonNull final DumpCheckpoint checkpoint) {
        System.out.printf("=== %d staking info ===%n", stakingInfoMerkleMap.size());

        final var allStakingInfo = gatherStakingInfoFromMono(MerkleMapLike.from(stakingInfoMerkleMap));

        int reportSize;
        try (@NonNull final var writer = new Writer(path)) {
            reportSummary(writer, allStakingInfo);
            reportOnStakingInfo(writer, allStakingInfo);
            reportSize = writer.getSize();
        }

        System.out.printf("=== staking info report is %d bytes %n", reportSize);
    }

    @NonNull
    static Map<Long, BBMStakingInfo> gatherStakingInfoFromMono(
            @NonNull final MerkleMapLike<EntityNum, MerkleStakingInfo> stakingInfoStore) {
        final var allStakingInfo = new TreeMap<Long, BBMStakingInfo>();
        stakingInfoStore.forEachNode((en, mt) -> allStakingInfo.put(en.longValue(), BBMStakingInfo.fromMono(mt)));
        return allStakingInfo;
    }

    public static void reportSummary(@NonNull Writer writer, @NonNull Map<Long, BBMStakingInfo> stakingInfo) {
        writer.writeln("=== %7d: staking info".formatted(stakingInfo.size()));
        writer.writeln("");
    }

    public static void reportOnStakingInfo(@NonNull Writer writer, @NonNull Map<Long, BBMStakingInfo> stakingInfo) {
        writer.writeln(formatHeader());
        stakingInfo.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(e -> formatStakingInfo(writer, e.getValue()));
        writer.writeln("");
    }

    static void formatStakingInfo(@NonNull final Writer writer, @NonNull final BBMStakingInfo stakingInfo) {
        final var fb = new FieldBuilder(FIELD_SEPARATOR);
        stakingInfoFieldFormatters.stream().map(Pair::right).forEach(ff -> ff.accept(fb, stakingInfo));
        writer.writeln(fb);
    }

    @NonNull
    static String formatHeader() {
        return stakingInfoFieldFormatters.stream().map(Pair::left).collect(Collectors.joining(FIELD_SEPARATOR));
    }

    public static <T> BiConsumer<FieldBuilder, BBMStakingInfo> getFieldFormatter(
            @NonNull final Function<BBMStakingInfo, T> fun, @NonNull final Function<T, String> formatter) {
        return (fb, t) -> formatField(fb, t, fun, formatter);
    }

    static <T> void formatField(
            @NonNull final FieldBuilder fb,
            @NonNull final BBMStakingInfo stakingInfo,
            @NonNull final Function<BBMStakingInfo, T> fun,
            @NonNull final Function<T, String> formatter) {
        fb.append(formatter.apply(fun.apply(stakingInfo)));
    }

    static <T> Function<List<T>, String> getListFormatter(
            @NonNull final Function<T, String> formatter, @NonNull final String subfieldSeparator) {
        return lt -> {
            if (!lt.isEmpty()) {
                final var sb = new StringBuilder();
                for (@NonNull final var e : lt) {
                    final var v = formatter.apply(e);
                    sb.append(v);
                    sb.append(subfieldSeparator);
                }
                // Remove last subfield separator
                if (sb.length() >= subfieldSeparator.length()) sb.setLength(sb.length() - subfieldSeparator.length());
                return sb.toString();
            } else return "";
        };
    }
}
