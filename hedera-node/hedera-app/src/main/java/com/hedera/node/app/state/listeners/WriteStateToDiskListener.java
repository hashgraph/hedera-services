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
import com.swirlds.common.utility.AutoCloseableWrapper;
import com.swirlds.platform.listeners.StateWriteToDiskCompleteListener;
import com.swirlds.platform.listeners.StateWriteToDiskCompleteNotification;
import com.swirlds.state.State;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.concurrent.Executor;
import java.util.function.Supplier;
import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Listener that will be notified with {@link
 * StateWriteToDiskCompleteNotification} when state is
 * written to disk. This writes {@code NOW_FROZEN_MARKER} to disk when upgrade is pending
 */
@Singleton
public class WriteStateToDiskListener implements StateWriteToDiskCompleteListener {
    private static final Logger log = LogManager.getLogger(WriteStateToDiskListener.class);

    private final Supplier<AutoCloseableWrapper<State>> stateAccessor;
    private final Executor executor;
    private final ConfigProvider configProvider;

    @Inject
    public WriteStateToDiskListener(
            @NonNull final Supplier<AutoCloseableWrapper<State>> stateAccessor,
            @NonNull @Named("FreezeService") final Executor executor,
            @NonNull final ConfigProvider configProvider) {
        requireNonNull(stateAccessor);
        requireNonNull(executor);
        requireNonNull(configProvider);
        this.stateAccessor = stateAccessor;
        this.executor = executor;
        this.configProvider = configProvider;
    }

    @Override
    public void notify(@NonNull final StateWriteToDiskCompleteNotification notification) {
        if (notification.isFreezeState()) {
            log.info(
                    "StateWriteToDiskCompleteNotification Received : Freeze State Finished. "
                            + "consensusTimestamp: {}, roundNumber: {}, sequence: {}",
                    notification.getConsensusTimestamp(),
                    notification.getRoundNumber(),
                    notification.getSequence());
            try (final var wrappedState = stateAccessor.get()) {
                final var readableStoreFactory = new ReadableStoreFactory(wrappedState.get());
                final var readableFreezeStore = readableStoreFactory.getStore(ReadableFreezeStore.class);
                final var readableUpgradeFileStore = readableStoreFactory.getStore(ReadableUpgradeFileStore.class);
                final var readableNodeStore = readableStoreFactory.getStore(ReadableNodeStore.class);
                final var readableStakingInfoStore = readableStoreFactory.getStore(ReadableStakingInfoStore.class);

                final var upgradeActions = new ReadableFreezeUpgradeActions(
                        configProvider.getConfiguration(),
                        readableFreezeStore,
                        executor,
                        readableUpgradeFileStore,
                        readableNodeStore,
                        readableStakingInfoStore);
                log.info("Externalizing freeze if upgrade is pending");
                upgradeActions.externalizeFreezeIfUpgradePending();
            } catch (Exception e) {
                log.error("Error while responding to freeze state notification", e);
            }
        }
    }
}
