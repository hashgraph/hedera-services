/*
 * Copyright (C) 2023-2024 Hedera Hashgraph, LLC
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

package com.hedera.node.app.service.networkadmin.impl.handlers;

import static java.util.Objects.requireNonNull;

import com.hedera.hapi.node.base.Timestamp;
import com.hedera.node.app.service.addressbook.ReadableNodeStore;
import com.hedera.node.app.service.file.ReadableUpgradeFileStore;
import com.hedera.node.app.service.networkadmin.impl.WritableFreezeStore;
import com.hedera.node.app.service.token.ReadableStakingInfoStore;
import com.swirlds.config.api.Configuration;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.concurrent.Executor;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Provides actions that take place during network upgrade.
 */
public class FreezeUpgradeActions extends ReadableFreezeUpgradeActions {
    private static final Logger log = LogManager.getLogger(FreezeUpgradeActions.class);
    private final WritableFreezeStore freezeStore;

    /**
     * Constructs a {@link FreezeUpgradeActions} instance.
     *
     * @param configuration the configuration
     * @param freezeStore the freeze store
     * @param executor the executor
     * @param upgradeFileStore the upgrade file store
     * @param nodeStore the node store
     * @param stakingInfoStore the staking info store
     */
    public FreezeUpgradeActions(
            @NonNull final Configuration configuration,
            @NonNull final WritableFreezeStore freezeStore,
            @NonNull final Executor executor,
            @NonNull final ReadableUpgradeFileStore upgradeFileStore,
            @NonNull final ReadableNodeStore nodeStore,
            @NonNull final ReadableStakingInfoStore stakingInfoStore) {
        super(configuration, freezeStore, executor, upgradeFileStore, nodeStore, stakingInfoStore);
        this.freezeStore = freezeStore;
    }

    /**
     * Schedules a freeze at the given time. This will only freeze the network and not upgrade it.
     * Manual intervention is required to restart it.
     * @param freezeTime the time to freeze the network
     */
    public void scheduleFreezeOnlyAt(@NonNull final Timestamp freezeTime) {
        requireNonNull(freezeTime);
        requireNonNull(freezeStore, "Cannot schedule freeze without access to the dual state");
        freezeStore.freezeTime(freezeTime);
    }

    /**
     * Schedules a freeze upgrade at the given time. This will freeze the network and then
     * perform a previously prepared upgrade.
     * @param freezeTime the time to freeze the network
     */
    public void scheduleFreezeUpgradeAt(@NonNull final Timestamp freezeTime) {
        requireNonNull(freezeTime);
        requireNonNull(freezeStore, "Cannot schedule freeze without access to the dual state");
        freezeStore.freezeTime(freezeTime);
        writeSecondMarker(FREEZE_SCHEDULED_MARKER, freezeTime);
    }

    /**
     * Aborts a scheduled freeze. This will only work if a freeze has been scheduled.
     */
    public void abortScheduledFreeze() {
        requireNonNull(freezeStore, "Cannot abort freeze without access to the dual state");
        freezeStore.freezeTime(Timestamp.DEFAULT);
        writeCheckMarker(FREEZE_ABORTED_MARKER);
    }
}
