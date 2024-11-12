/*
 * Copyright (C) 2021-2024 Hedera Hashgraph, LLC
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

package com.hedera.node.app.state.listeners;

import static java.util.Objects.requireNonNull;

import com.hedera.node.app.service.addressbook.ReadableNodeStore;
import com.hedera.node.app.service.file.ReadableUpgradeFileStore;
import com.hedera.node.app.service.networkadmin.ReadableFreezeStore;
import com.hedera.node.app.service.networkadmin.impl.handlers.ReadableFreezeUpgradeActions;
import com.hedera.node.app.service.token.ReadableStakingInfoStore;
import com.hedera.node.app.store.ReadableStoreFactory;
import com.hedera.node.config.ConfigProvider;
import com.swirlds.platform.listeners.ReconnectCompleteListener;
import com.swirlds.platform.listeners.ReconnectCompleteNotification;
import com.swirlds.platform.state.service.ReadablePlatformStateStore;
import com.swirlds.state.State;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.concurrent.Executor;
import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * A {@link ReconnectCompleteListener} that catches up on missed upgrade side effects after a reconnect.
 */
@Singleton
public class ReconnectListener implements ReconnectCompleteListener {
    private static final Logger log = LogManager.getLogger(ReconnectListener.class);

    private final Executor executor;
    private final ConfigProvider configProvider;

    @Inject
    public ReconnectListener(
            @NonNull @Named("FreezeService") final Executor executor, @NonNull final ConfigProvider configProvider) {
        this.executor = requireNonNull(executor);
        this.configProvider = requireNonNull(configProvider);
    }

    @Override
    public void notify(@NonNull final ReconnectCompleteNotification notification) {
        requireNonNull(notification);
        log.info(
                "ReconnectCompleteNotification Received: Reconnect Finished. "
                        + "consensusTimestamp: {}, roundNumber: {}, sequence: {}",
                notification.getConsensusTimestamp(),
                notification.getRoundNumber(),
                notification.getSequence());
        final State state = notification.getState().cast();
        final var readableStoreFactory = new ReadableStoreFactory(state);
        final var freezeStore = readableStoreFactory.getStore(ReadableFreezeStore.class);
        final var upgradeFileStore = readableStoreFactory.getStore(ReadableUpgradeFileStore.class);
        final var upgradeNodeStore = readableStoreFactory.getStore(ReadableNodeStore.class);
        final var upgradeStakingInfoStore = readableStoreFactory.getStore(ReadableStakingInfoStore.class);
        final var platformStateStore = readableStoreFactory.getStore(ReadablePlatformStateStore.class);
        final var upgradeActions = new ReadableFreezeUpgradeActions(
                configProvider.getConfiguration(),
                freezeStore,
                executor,
                upgradeFileStore,
                upgradeNodeStore,
                upgradeStakingInfoStore);
        try {
            // Because we only leave the latest Dagger infrastructure registered with the platform
            // notification system when the reconnect state is initialized, this platform state
            // will be up-to-date
            upgradeActions.catchUpOnMissedSideEffects(platformStateStore);
        } catch (Exception e) {
            log.error("Unable to catch up on missed upgrade side effects after reconnect", e);
        }
    }
}
