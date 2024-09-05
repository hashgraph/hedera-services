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

import com.swirlds.common.notification.Listener;
import com.swirlds.common.notification.Notification;
import com.swirlds.common.notification.NotificationEngine;
import com.swirlds.common.notification.NotificationResult;
import com.swirlds.common.threading.futures.StandardFuture;
import com.swirlds.platform.listeners.PlatformStatusChangeListener;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Future;

public class FakeNotificationEngine implements NotificationEngine {
    public final List<PlatformStatusChangeListener> statusChangeListeners = new CopyOnWriteArrayList<>();

    @Override
    public void initialize() {
        throw new UnsupportedOperationException("Not used by Hedera");
    }

    @Override
    public void shutdown() {
        throw new UnsupportedOperationException("Not used by Hedera");
    }

    @Override
    public <L extends Listener<N>, N extends Notification> Future<NotificationResult<N>> dispatch(
            Class<L> listenerClass,
            N notification,
            StandardFuture.CompletionCallback<NotificationResult<N>> notificationsCompletedCallback) {
        throw new UnsupportedOperationException("Not used by Hedera");
    }

    @Override
    public <L extends Listener<?>> boolean register(@NonNull final Class<L> listenerClass, @NonNull final L callback) {
        requireNonNull(listenerClass);
        requireNonNull(callback);
        if (listenerClass == PlatformStatusChangeListener.class) {
            return statusChangeListeners.add((PlatformStatusChangeListener) callback);
        }
        return false;
    }

    @Override
    public <L extends Listener<?>> boolean unregister(Class<L> listenerClass, L callback) {
        throw new UnsupportedOperationException("Not used by Hedera");
    }

    @Override
    public void unregisterAll() {
        throw new UnsupportedOperationException("Not used by Hedera");
    }
}
