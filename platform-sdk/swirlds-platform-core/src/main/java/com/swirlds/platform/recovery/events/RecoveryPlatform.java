/*
 * Copyright (C) 2022-2023 Hedera Hashgraph, LLC
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

package com.swirlds.platform.recovery.events;

import static com.swirlds.common.threading.manager.AdHocThreadManager.getStaticThreadManager;
import static com.swirlds.platform.crypto.CryptoSetup.initNodeSecurity;

import com.swirlds.common.AutoCloseableNonThrowing;
import com.swirlds.common.context.DefaultPlatformContext;
import com.swirlds.common.context.PlatformContext;
import com.swirlds.common.crypto.CryptographyHolder;
import com.swirlds.common.crypto.Signature;
import com.swirlds.common.metrics.Metrics;
import com.swirlds.common.metrics.noop.NoOpMetrics;
import com.swirlds.common.notification.NotificationEngine;
import com.swirlds.common.system.NodeId;
import com.swirlds.common.system.Platform;
import com.swirlds.common.system.SwirldState;
import com.swirlds.common.system.address.AddressBook;
import com.swirlds.common.utility.AutoCloseableWrapper;
import com.swirlds.config.api.Configuration;
import com.swirlds.platform.Crypto;
import com.swirlds.platform.state.signed.ReservedSignedState;
import com.swirlds.platform.state.signed.SignedState;
import com.swirlds.platform.state.signed.SignedStateReference;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Objects;

/**
 * A simplified version of the platform to be used during the recovery workflow.
 */
public class RecoveryPlatform implements Platform, AutoCloseableNonThrowing {

    private final NodeId selfId;

    private final AddressBook addressBook;
    private final Crypto crypto;

    private final SignedStateReference immutableState = new SignedStateReference();

    private final NotificationEngine notificationEngine;

    private final PlatformContext context;

    /**
     * Create a new recovery platform.
     *
     * @param configuration   the node's configuration
     * @param initialState    the starting signed state
     * @param selfId          the ID of the node
     * @param loadSigningKeys whether to load the signing keys, if false then {@link #sign(byte[])} will throw if
     *                        called
     */
    public RecoveryPlatform(
            @NonNull final Configuration configuration,
            @NonNull final SignedState initialState,
            @NonNull final NodeId selfId,
            final boolean loadSigningKeys) {
        Objects.requireNonNull(configuration, "configuration must not be null");
        Objects.requireNonNull(initialState, "initialState must not be null");
        this.selfId = Objects.requireNonNull(selfId, "selfId must not be null");

        this.addressBook = initialState.getAddressBook();

        if (loadSigningKeys) {
            crypto = initNodeSecurity(addressBook, configuration).get(selfId);
        } else {
            crypto = null;
        }

        final Metrics metrics = new NoOpMetrics();

        notificationEngine = NotificationEngine.buildEngine(getStaticThreadManager());

        context = new DefaultPlatformContext(configuration, metrics, CryptographyHolder.get());

        setLatestState(initialState);
    }

    /**
     * Set the most recent immutable state.
     *
     * @param signedState the most recent signed state
     */
    public synchronized void setLatestState(final SignedState signedState) {
        immutableState.set(signedState, "RecoveryPlatform.setLatestState");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Signature sign(final byte[] data) {
        if (crypto == null) {
            throw new UnsupportedOperationException(
                    "RecoveryPlatform was not loaded with signing keys, this operation is not supported");
        }
        return crypto.sign(data);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public PlatformContext getContext() {
        return context;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public NotificationEngine getNotificationEngine() {
        return notificationEngine;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public AddressBook getAddressBook() {
        return addressBook;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public NodeId getSelfId() {
        return selfId;
    }

    /**
     * {@inheritDoc}
     */
    @SuppressWarnings("unchecked")
    @Override
    public synchronized <T extends SwirldState> AutoCloseableWrapper<T> getLatestImmutableState(
            @NonNull final String reason) {
        final ReservedSignedState reservedSignedState = immutableState.getAndReserve(reason);
        return new AutoCloseableWrapper<>(
                reservedSignedState.isNull()
                        ? null
                        : (T) reservedSignedState.get().getSwirldState(),
                reservedSignedState::close);
    }

    /**
     * {@inheritDoc}
     */
    @SuppressWarnings("unchecked")
    @Override
    public <T extends SwirldState> AutoCloseableWrapper<T> getLatestSignedState(@NonNull final String reason) {
        final ReservedSignedState reservedSignedState = immutableState.getAndReserve(reason);
        return new AutoCloseableWrapper<>(
                reservedSignedState.isNull()
                        ? null
                        : (T) reservedSignedState.get().getSwirldState(),
                reservedSignedState::close);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean createTransaction(@NonNull final byte[] transaction) {
        // Transaction creation always fails
        return false;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void close() {
        immutableState.clear();
        notificationEngine.shutdown();
    }
}
