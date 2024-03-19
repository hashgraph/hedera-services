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

import com.hedera.node.app.service.file.ReadableUpgradeFileStore;
import com.hedera.node.app.service.networkadmin.ReadableFreezeStore;
import com.hedera.node.app.service.networkadmin.impl.handlers.ReadableFreezeUpgradeActions;
import com.hedera.node.app.state.HederaState;
import com.hedera.node.app.state.PlatformStateAccessor;
import com.hedera.node.app.workflows.dispatcher.ReadableStoreFactory;
import com.hedera.node.config.ConfigProvider;
import com.hedera.node.config.data.NetworkAdminConfig;
import com.swirlds.common.utility.AutoCloseableWrapper;
import com.swirlds.platform.listeners.ReconnectCompleteListener;
import com.swirlds.platform.listeners.ReconnectCompleteNotification;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.concurrent.Executor;
import java.util.function.Supplier;
import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Singleton
public class ReconnectListener implements ReconnectCompleteListener {
    private static final Logger log = LogManager.getLogger(ReconnectListener.class);

    private final Supplier<AutoCloseableWrapper<HederaState>> stateAccessor;
    private final Executor executor;
    private final ConfigProvider configProvider;
    private final PlatformStateAccessor platformStateAccessor;

    @Inject
    public ReconnectListener(
            @NonNull final Supplier<AutoCloseableWrapper<HederaState>> stateAccessor,
            @NonNull @Named("FreezeService") final Executor executor,
            @NonNull final ConfigProvider configProvider,
            @NonNull final PlatformStateAccessor platformStateAccessor) {

        this.stateAccessor = stateAccessor;
        this.executor = executor;
        this.configProvider = configProvider;
        this.platformStateAccessor = platformStateAccessor;
    }

    @Override
    public void notify(final ReconnectCompleteNotification notification) {
        log.info(
                "ReconnectCompleteNotification Received: Reconnect Finished. "
                        + "consensusTimestamp: {}, roundNumber: {}, sequence: {}",
                notification.getConsensusTimestamp(),
                notification.getRoundNumber(),
                notification.getSequence());
        try (final var wrappedState = stateAccessor.get()) {
            final var readableStoreFactory = new ReadableStoreFactory(wrappedState.get());
            final var networkAdminConfig = configProvider.getConfiguration().getConfigData(NetworkAdminConfig.class);
            final var freezeStore = readableStoreFactory.getStore(ReadableFreezeStore.class);
            final var upgradeFileStore = readableStoreFactory.getStore(ReadableUpgradeFileStore.class);
            final var upgradeActions =
                    new ReadableFreezeUpgradeActions(networkAdminConfig, freezeStore, executor, upgradeFileStore);
            upgradeActions.catchUpOnMissedSideEffects(platformStateAccessor.getPlatformState());
        }
    }
}
