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

import com.hedera.node.app.service.mono.state.adapters.MerkleMapLike;
import com.hedera.node.app.service.mono.state.merkle.MerkleStakingInfo;
import com.hedera.node.app.service.mono.utils.EntityNum;
import com.hedera.services.cli.signedstate.DumpStateCommand.EmitSummary;
import com.hedera.services.cli.signedstate.SignedStateCommand.Verbosity;
import com.hedera.services.cli.utils.FieldBuilder;
import com.hedera.services.cli.utils.Writer;
import com.swirlds.base.utility.Pair;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Dump staking info from a signed state file to a text file in a deterministic order  */
public class DumpStakingInfoSubcommand {

    static void doit(
            @NonNull final SignedStateHolder state,
            @NonNull final Path stakingInfoPath,
            @NonNull final EmitSummary emitSummary,
            @NonNull final Verbosity verbosity) {
        new DumpStakingInfoSubcommand(state, stakingInfoPath, emitSummary, verbosity).doit();
    }

    @NonNull
    final SignedStateHolder state;

    @NonNull
    final Path stakingInfoPath;

    @NonNull
    final EmitSummary emitSummary;

    @NonNull
    final Verbosity verbosity;

    DumpStakingInfoSubcommand(
            @NonNull final SignedStateHolder state,
            @NonNull final Path stakingInfoPath,
            @NonNull final EmitSummary emitSummary,
            @NonNull final Verbosity verbosity) {
        requireNonNull(state, "state");
        requireNonNull(stakingInfoPath, "stakingInfoPath");
        requireNonNull(emitSummary, "emitSummary");
        requireNonNull(verbosity, "verbosity");

        this.state = state;
        this.stakingInfoPath = stakingInfoPath;
        this.emitSummary = emitSummary;
        this.verbosity = verbosity;
    }

    void doit() {
        final var stakingInfoStore = state.getStakingInfo();
        System.out.printf("=== %d staking info ===%n", stakingInfoStore.size());

        final var allStakingInfo = gatherStakingInfo(stakingInfoStore);

        int reportSize;
        try (@NonNull final var writer = new Writer(stakingInfoPath)) {
            if (emitSummary == EmitSummary.YES) reportSummary(writer, allStakingInfo);
            reportOnStakingInfo(writer, allStakingInfo);
            reportSize = writer.getSize();
        }

        System.out.printf("=== staking info report is %d bytes%n", reportSize);
    }

    @SuppressWarnings(
            "java:S6218") // "Equals/hashcode method should be overridden in records containing array fields" - this
    record StakingInfo(
            int number,
            long minStake,
            long maxStake,
            long stakeToReward,
            long stakeToNotReward,
            long stakeRewardStart,
            long unclaimedStakeRewardStart,
            long stake,
            @NonNull long[] rewardSumHistory,
            int weight) {
        StakingInfo(@NonNull final MerkleStakingInfo stakingInfo) {
            this(
                    stakingInfo.getKey().intValue(),
                    stakingInfo.getMinStake(),
                    stakingInfo.getMaxStake(),
                    stakingInfo.getStakeToReward(),
                    stakingInfo.getStakeToNotReward(),
                    stakingInfo.getStakeRewardStart(),
                    stakingInfo.getUnclaimedStakeRewardStart(),
                    stakingInfo.getStake(),
                    stakingInfo.getRewardSumHistory(),
                    stakingInfo.getWeight());
            Objects.requireNonNull(rewardSumHistory, "rewardSumHistory");
        }
    }

    @NonNull
    Map<Long, StakingInfo> gatherStakingInfo(
            @NonNull final MerkleMapLike<EntityNum, MerkleStakingInfo> stakingInfoStore) {
        final var allStakingInfo = new TreeMap<Long, StakingInfo>();
        stakingInfoStore.forEachNode((en, mt) -> allStakingInfo.put(en.longValue(), new StakingInfo(mt)));
        return allStakingInfo;
    }

    void reportSummary(@NonNull Writer writer, @NonNull Map<Long, StakingInfo> stakingInfo) {
        writer.writeln("=== %7d: staking info".formatted(stakingInfo.size()));
        writer.writeln("");
    }

    void reportOnStakingInfo(@NonNull Writer writer, @NonNull Map<Long, StakingInfo> stakingInfo) {
        writer.writeln(formatHeader());
        stakingInfo.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(e -> formatStakingInfo(writer, e.getValue()));
        writer.writeln("");
    }

    void formatStakingInfo(@NonNull final Writer writer, @NonNull final StakingInfo stakingInfo) {
        final var fb = new FieldBuilder(FIELD_SEPARATOR);
        fieldFormatters.stream().map(Pair::right).forEach(ff -> ff.accept(fb, stakingInfo));
        writer.writeln(fb);
    }

    @NonNull
    String formatHeader() {
        return fieldFormatters.stream().map(Pair::left).collect(Collectors.joining(FIELD_SEPARATOR));
    }

    static final String FIELD_SEPARATOR = ";";

    @NonNull
    static List<Pair<String, BiConsumer<FieldBuilder, StakingInfo>>> fieldFormatters = List.of(
            Pair.of("number", getFieldFormatter(StakingInfo::number, Object::toString)),
            Pair.of("minStake", getFieldFormatter(StakingInfo::minStake, Object::toString)),
            Pair.of("maxStake", getFieldFormatter(StakingInfo::maxStake, Object::toString)),
            Pair.of("stakeToReward", getFieldFormatter(StakingInfo::stakeToReward, Object::toString)),
            Pair.of("stakeToNotReward", getFieldFormatter(StakingInfo::stakeToNotReward, Object::toString)),
            Pair.of("stakeRewardStart", getFieldFormatter(StakingInfo::stakeRewardStart, Object::toString)),
            Pair.of(
                    "unclaimedStakeRewardStart",
                    getFieldFormatter(StakingInfo::unclaimedStakeRewardStart, Object::toString)),
            Pair.of("stake", getFieldFormatter(StakingInfo::stake, Object::toString)),
            Pair.of("rewardSumHistory", getFieldFormatter(StakingInfo::rewardSumHistory, Object::toString)),
            Pair.of("weight", getFieldFormatter(StakingInfo::weight, Object::toString)));

    static <T> BiConsumer<FieldBuilder, StakingInfo> getFieldFormatter(
            @NonNull final Function<StakingInfo, T> fun, @NonNull final Function<T, String> formatter) {
        return (fb, t) -> formatField(fb, t, fun, formatter);
    }

    static <T> Function<T, String> getNullableFormatter(@NonNull final Function<T, String> formatter) {
        return t -> null != t ? formatter.apply(t) : "";
    }

    static <T> void formatField(
            @NonNull final FieldBuilder fb,
            @NonNull final StakingInfo stakingInfo,
            @NonNull final Function<StakingInfo, T> fun,
            @NonNull final Function<T, String> formatter) {
        fb.append(formatter.apply(fun.apply(stakingInfo)));
    }
}
