/*
 * Copyright (C) 2020-2024 Hedera Hashgraph, LLC
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

package com.swirlds.common.notification.internal;

import com.swirlds.common.notification.DispatchMode;
import com.swirlds.common.notification.DispatchOrder;
import com.swirlds.common.notification.Listener;
import com.swirlds.common.notification.NoListenersAvailableException;
import com.swirlds.common.notification.Notification;
import com.swirlds.common.notification.NotificationResult;
import com.swirlds.common.threading.futures.StandardFuture;
import com.swirlds.common.threading.manager.ThreadManager;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Future;
import java.util.function.Consumer;

public class AsyncNotificationEngine extends AbstractNotificationEngine {

    /**
     * the internal listener registry that associates a given type of {@link Listener} with a {@link Dispatcher}
     */
    private final Map<Class<? extends Listener<?>>, Dispatcher<? extends Listener<?>>> listenerRegistry;

    /**
     * Responsible for creating and managing threads used by this object.
     */
    private final ThreadManager threadManager;

    /**
     * Default Constructor.
     * @param threadManager
     * 		responsible for managing thread lifecycles
     */
    public AsyncNotificationEngine(final ThreadManager threadManager) {
        this.threadManager = threadManager;
        this.listenerRegistry = new ConcurrentHashMap<>();
        Runtime.getRuntime().addShutdownHook(new Thread(this::shutdown));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void initialize() {}

    /**
     * {@inheritDoc}
     */
    @Override
    public void shutdown() {
        for (Dispatcher<?> dispatcher : listenerRegistry.values()) {
            if (dispatcher.isRunning()) {
                dispatcher.stop();
            }
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public <L extends Listener<N>, N extends Notification> Future<NotificationResult<N>> dispatch(
            final Class<L> listenerClass,
            final N notification,
            final StandardFuture.CompletionCallback<NotificationResult<N>> notificationsCompletedCallback) {

        checkArguments(listenerClass, notification);

        final DispatchOrder dispatchOrder = dispatchOrder(listenerClass);
        final DispatchMode dispatchMode = dispatchMode(listenerClass);

        final StandardFuture<NotificationResult<N>> future = new StandardFuture<>(notificationsCompletedCallback);

        try {
            invokeWithDispatcher(dispatchOrder, listenerClass, (dispatcher) -> {
                assignSequence(notification);

                if (dispatchMode == DispatchMode.ASYNC) {
                    dispatcher.notifyAsync(notification, future::complete);
                } else {
                    dispatcher.notifySync(notification, future::complete);
                }
            });
        } catch (NoListenersAvailableException ex) {
            future.complete(new NotificationResult<>(notification, 0));
        }

        return future;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public <L extends Listener<?>> boolean register(final Class<L> listenerClass, final L callback) {

        checkArguments(listenerClass, callback);

        final Dispatcher<L> dispatcher = ensureDispatcherExists(listenerClass);

        return dispatcher.addListener(callback);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public <L extends Listener<?>> boolean unregister(final Class<L> listenerClass, final L callback) {

        checkArguments(listenerClass, callback);

        final Dispatcher<L> dispatcher = ensureDispatcherExists(listenerClass);

        return dispatcher.removeListener(callback);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void unregisterAll() {
        listenerRegistry.clear();
    }

    private <L extends Listener<N>, N extends Notification> void checkArguments(
            final Class<L> listenerClass, final N notification) {
        if (listenerClass == null) {
            throw new IllegalArgumentException("listenerClass");
        }

        if (notification == null) {
            throw new IllegalArgumentException("notification");
        }
    }

    private static <L extends Listener<?>> void checkArguments(final Class<L> listenerClass, final L callback) {
        if (listenerClass == null) {
            throw new IllegalArgumentException("listenerClass");
        }

        if (callback == null) {
            throw new IllegalArgumentException("callback");
        }
    }

    private <L extends Listener<?>> Dispatcher<L> ensureDispatcherExists(final Class<L> listenerClass) {
        @SuppressWarnings("unchecked")
        Dispatcher<L> dispatcher = (Dispatcher<L>)
                listenerRegistry.putIfAbsent(listenerClass, new Dispatcher<>(threadManager, listenerClass));

        if (dispatcher == null) {
            dispatcher = new Dispatcher<>(threadManager, listenerClass);
            listenerRegistry.put(listenerClass, dispatcher);
        }

        return dispatcher;
    }

    private <L extends Listener<N>, N extends Notification> void invokeWithDispatcher(
            final DispatchOrder order, final Class<L> listenerClass, final Consumer<Dispatcher<L>> method)
            throws NoListenersAvailableException {
        @SuppressWarnings("unchecked")
        final Dispatcher<L> dispatcher = (Dispatcher<L>) listenerRegistry.get(listenerClass);

        if (dispatcher == null) {
            throw new NoListenersAvailableException();
        }

        if (order == DispatchOrder.ORDERED) {
            synchronized (dispatcher.getMutex()) {
                method.accept(dispatcher);
            }
        } else {
            method.accept(dispatcher);
        }
    }
}
