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

import static com.hedera.node.app.service.mono.statedumpers.singleton.StakingRewardsDumpUtils.formatHeader;
import static com.hedera.node.app.service.mono.statedumpers.singleton.StakingRewardsDumpUtils.formatStakingRewards;

import com.hedera.hapi.node.state.token.NetworkStakingRewards;
import com.hedera.node.app.service.mono.statedumpers.DumpCheckpoint;
import com.hedera.node.app.service.mono.statedumpers.singleton.BBMStakingRewards;
import com.hedera.node.app.service.mono.statedumpers.utils.Writer;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.nio.file.Path;

public class StakingRewardsDumpUtils {
    public static void dumpModStakingRewards(
            @NonNull final Path path,
            @NonNull final NetworkStakingRewards stakingRewards,
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
        formatStakingRewards(writer, BBMStakingRewards);
        writer.writeln("");
    }

    public static BBMStakingRewards fromMod(@NonNull final NetworkStakingRewards networkBBMStakingRewards) {
        return new BBMStakingRewards(
                networkBBMStakingRewards.stakingRewardsActivated(),
                networkBBMStakingRewards.totalStakedRewardStart(),
                networkBBMStakingRewards.totalStakedStart(),
                networkBBMStakingRewards.pendingRewards());
    }
}
