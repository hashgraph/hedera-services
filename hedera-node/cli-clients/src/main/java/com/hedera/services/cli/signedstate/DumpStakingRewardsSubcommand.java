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

import com.hedera.node.app.service.mono.state.merkle.MerkleNetworkContext;
import com.hedera.services.cli.utils.FieldBuilder;
import com.hedera.services.cli.utils.Writer;
import com.swirlds.base.utility.Pair;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.nio.file.Path;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Dump staking rewards from a signed state file to a text file in a deterministic order  */
public class DumpStakingRewardsSubcommand {

    static void doit(@NonNull final SignedStateHolder state, @NonNull final Path stakingRewardsPath) {
        new DumpStakingRewardsSubcommand(state, stakingRewardsPath).doit();
    }

    @NonNull
    final SignedStateHolder state;

    @NonNull
    final Path stakingRewardsPath;

    DumpStakingRewardsSubcommand(@NonNull final SignedStateHolder state, @NonNull final Path stakingRewardsPath) {
        requireNonNull(state, "state");
        requireNonNull(stakingRewardsPath, "stakingRewardsPath");

        this.state = state;
        this.stakingRewardsPath = stakingRewardsPath;
    }

    void doit() {
        final var networkContext = state.getNetworkContext();
        System.out.printf("=== staking rewards ===%n");

        final var stakingRewards = StakingRewards.fromMerkleNetworkContext(networkContext);

        int reportSize;
        try (@NonNull final var writer = new Writer(stakingRewardsPath)) {
            reportOnStakingRewards(writer, stakingRewards);
            reportSize = writer.getSize();
        }

        System.out.printf("=== staking rewards report is %d bytes%n", reportSize);
    }

    @SuppressWarnings(
            "java:S6218") // "Equals/hashcode method should be overridden in records containing array fields" - this
    public record StakingRewards(
            boolean stakingRewardsActivated, long totalStakedRewardStart, long totalStakedStart, long pendingRewards) {

        public static StakingRewards fromMerkleNetworkContext(
                @NonNull final MerkleNetworkContext merkleNetworkContext) {

            return new StakingRewards(
                    merkleNetworkContext.areRewardsActivated(),
                    merkleNetworkContext.getTotalStakedRewardStart(),
                    merkleNetworkContext.getTotalStakedStart(),
                    merkleNetworkContext.pendingRewards());
        }
    }

    void reportOnStakingRewards(@NonNull Writer writer, @NonNull StakingRewards stakingRewards) {
        writer.writeln(formatHeader());
        formatStakingRewards(writer, stakingRewards);
        writer.writeln("");
    }

    void formatStakingRewards(@NonNull final Writer writer, @NonNull final StakingRewards stakingRewards) {
        final var fb = new FieldBuilder(FIELD_SEPARATOR);
        fieldFormatters.stream().map(Pair::right).forEach(ff -> ff.accept(fb, stakingRewards));
        writer.writeln(fb);
    }

    @NonNull
    String formatHeader() {
        return fieldFormatters.stream().map(Pair::left).collect(Collectors.joining(FIELD_SEPARATOR));
    }

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
