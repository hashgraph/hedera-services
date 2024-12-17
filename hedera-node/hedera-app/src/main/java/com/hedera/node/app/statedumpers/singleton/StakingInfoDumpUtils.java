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

import static com.swirlds.common.threading.manager.AdHocThreadManager.getStaticThreadManager;

import com.hedera.hapi.node.state.common.EntityNumber;
import com.hedera.hapi.node.state.token.StakingNodeInfo;
import com.hedera.node.app.statedumpers.utils.FieldBuilder;
import com.hedera.node.app.statedumpers.utils.Writer;
import com.swirlds.base.utility.Pair;
import com.swirlds.state.merkle.disk.OnDiskKey;
import com.swirlds.state.merkle.disk.OnDiskValue;
import com.swirlds.virtualmap.VirtualMap;
import com.swirlds.virtualmap.VirtualMapMigration;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentLinkedQueue;
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

    public static void dumpModStakingInfo(
            @NonNull final Path path,
            @NonNull final VirtualMap<OnDiskKey<EntityNumber>, OnDiskValue<StakingNodeInfo>> BBMStakingInfoVirtualMap) {
        System.out.printf("=== %d staking info ===%n", BBMStakingInfoVirtualMap.size());

        final var allBBMStakingInfo = gatherBBMStakingInfoFromMod(BBMStakingInfoVirtualMap);

        int reportSize;
        try (@NonNull final var writer = new Writer(path)) {
            reportSummary(writer, allBBMStakingInfo);
            reportOnStakingInfo(writer, allBBMStakingInfo);
            reportSize = writer.getSize();
        }

        System.out.printf("=== staking info report is %d bytes %n", reportSize);
    }

    @NonNull
    static Map<Long, BBMStakingInfo> gatherBBMStakingInfoFromMod(
            @NonNull final VirtualMap<OnDiskKey<EntityNumber>, OnDiskValue<StakingNodeInfo>> stakingInfo) {
        final var r = new HashMap<Long, BBMStakingInfo>();
        final var threadCount = 5;
        final var mappings = new ConcurrentLinkedQueue<Pair<Long, BBMStakingInfo>>();
        try {
            VirtualMapMigration.extractVirtualMapDataC(
                    getStaticThreadManager(),
                    stakingInfo,
                    p -> mappings.add(Pair.of(
                            p.left().getKey().number(), fromMod(p.right().getValue()))),
                    threadCount);
        } catch (final InterruptedException ex) {
            System.err.println("*** Traversal of uniques virtual map interrupted!");
            Thread.currentThread().interrupt();
        }
        // Consider in the future: Use another thread to pull things off the queue as they're put on by the
        // virtual map traversal
        while (!mappings.isEmpty()) {
            final var mapping = mappings.poll();
            r.put(mapping.key(), mapping.value());
        }
        return r;
    }

    static void reportSummary(@NonNull Writer writer, @NonNull Map<Long, BBMStakingInfo> BBMStakingInfo) {
        writer.writeln("=== %7d: staking info".formatted(BBMStakingInfo.size()));
        writer.writeln("");
    }

    public static BBMStakingInfo fromMod(@NonNull final StakingNodeInfo BBMStakingInfo) {
        Objects.requireNonNull(BBMStakingInfo.rewardSumHistory(), "rewardSumHistory");
        return new BBMStakingInfo(
                Long.valueOf(BBMStakingInfo.nodeNumber()).intValue(),
                BBMStakingInfo.minStake(),
                BBMStakingInfo.maxStake(),
                BBMStakingInfo.stakeToReward(),
                BBMStakingInfo.stakeToNotReward(),
                BBMStakingInfo.stakeRewardStart(),
                BBMStakingInfo.unclaimedStakeRewardStart(),
                BBMStakingInfo.stake(),
                BBMStakingInfo.rewardSumHistory().stream()
                        .mapToLong(Long::longValue)
                        .toArray(),
                BBMStakingInfo.weight());
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
}
