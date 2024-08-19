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

package com.hedera.services.bdd.junit.hedera.embedded.fakes;

import static java.util.Objects.requireNonNull;

import com.swirlds.common.context.PlatformContext;
import com.swirlds.common.crypto.Signature;
import com.swirlds.common.crypto.SignatureType;
import com.swirlds.common.notification.NotificationEngine;
import com.swirlds.common.platform.NodeId;
import com.swirlds.common.utility.AutoCloseableWrapper;
import com.swirlds.platform.listeners.PlatformStatusChangeNotification;
import com.swirlds.platform.system.Platform;
import com.swirlds.platform.system.SwirldState;
import com.swirlds.platform.system.address.AddressBook;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.atomic.AtomicLong;

public abstract class AbstractFakePlatform implements Platform {
    private static final Signature TOY_SIGNATURE = new Signature(SignatureType.RSA, new byte[384]);
    protected static final long NANOS_BETWEEN_CONS_EVENTS = 1_000;

    protected final AtomicLong roundNo = new AtomicLong(1);
    protected final AtomicLong consensusOrder = new AtomicLong(1);

    private final NodeId selfId;
    private final AddressBook addressBook;
    private final PlatformContext platformContext;
    private final FakeNotificationEngine notificationEngine = new FakeNotificationEngine();

    public AbstractFakePlatform(
            @NonNull final NodeId selfId,
            @NonNull final AddressBook addressBook,
            @NonNull final ScheduledExecutorService executorService) {
        requireNonNull(executorService);
        this.selfId = requireNonNull(selfId);
        this.addressBook = requireNonNull(addressBook);
        platformContext = new FakePlatformContext(selfId, executorService);
    }

    /**
     * Notifies listeners of a platform status change.
     *
     * @param notification the notification to send
     */
    public void notifyListeners(@NonNull final PlatformStatusChangeNotification notification) {
        notificationEngine.statusChangeListeners.forEach(l -> l.notify(notification));
    }

    @NonNull
    @Override
    public Signature sign(@NonNull final byte[] data) {
        return TOY_SIGNATURE;
    }

    @NonNull
    @Override
    public PlatformContext getContext() {
        return platformContext;
    }

    @NonNull
    @Override
    public NodeId getSelfId() {
        return selfId;
    }

    @NonNull
    @Override
    public <T extends SwirldState> AutoCloseableWrapper<T> getLatestImmutableState(@NonNull String reason) {
        throw new UnsupportedOperationException("Not used by Hedera");
    }

    @NonNull
    @Override
    public AddressBook getAddressBook() {
        return addressBook;
    }

    @NonNull
    @Override
    public NotificationEngine getNotificationEngine() {
        return notificationEngine;
    }
}
