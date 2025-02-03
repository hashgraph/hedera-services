/*
 * Copyright (C) 2025 Hedera Hashgraph, LLC
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

package com.hedera.node.app.service.token.impl.schemas;

import static com.hedera.node.app.service.token.impl.schemas.V0490TokenSchema.STAKING_INFO_KEY;
import static com.hedera.node.app.service.token.impl.schemas.V0490TokenSchema.STAKING_NETWORK_REWARDS_KEY;
import static java.util.Objects.requireNonNull;

import com.hedera.hapi.node.base.SemanticVersion;
import com.hedera.hapi.node.state.common.EntityNumber;
import com.hedera.hapi.node.state.token.NetworkStakingRewards;
import com.hedera.hapi.node.state.token.StakingNodeInfo;
import com.swirlds.state.lifecycle.MigrationContext;
import com.swirlds.state.lifecycle.Schema;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.time.Duration;
import java.util.SortedMap;
import java.util.function.Supplier;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class V058PendingRewardsSchema extends Schema {
    private static final Logger log = LogManager.getLogger(V058PendingRewardsSchema.class);

    private static final SemanticVersion VERSION =
            SemanticVersion.newBuilder().major(0).minor(58).patch(0).build();

    private final Supplier<SortedMap<Long, Long>> pendingRewards;

    public V058PendingRewardsSchema(@NonNull final Supplier<SortedMap<Long, Long>> pendingRewards) {
        super(VERSION);
        this.pendingRewards = requireNonNull(pendingRewards);
    }

    @Override
    public void migrate(@NonNull final MigrationContext ctx) {
        final long start = System.nanoTime();
        final var pending = pendingRewards.get();
        final long end = System.nanoTime();
        log.info(
                "Computed pending rewards in {}ms",
                Duration.ofNanos(end - start).toMillis());

        // Update the total pending rewards
        final var totalPending =
                pending.values().stream().mapToLong(Long::longValue).sum();
        final var singletonState = ctx.newStates().<NetworkStakingRewards>getSingleton(STAKING_NETWORK_REWARDS_KEY);
        final var networkStakingRewards = requireNonNull(singletonState.get());
        singletonState.put(
                networkStakingRewards.copyBuilder().pendingRewards(totalPending).build());

        // Update the per-node pending rewards
        final var stakingInfos = ctx.newStates().<EntityNumber, StakingNodeInfo>get(STAKING_INFO_KEY);
        pending.forEach((nodeId, rewards) -> {
            final var key = new EntityNumber(nodeId);
            final var stakingInfo = stakingInfos.get(key);
            if (stakingInfo == null) {
                log.error("Cannot reconcile missing node{} with pending rewards {}", nodeId, rewards);
                return;
            }
            stakingInfos.put(
                    key, stakingInfo.copyBuilder().pendingRewards(rewards).build());
        });
    }
}
