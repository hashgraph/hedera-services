/*
 * Copyright (C) 2023 Hedera Hashgraph, LLC
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

package com.swirlds.common.metrics.platform;

import static com.swirlds.base.ArgumentUtils.ERROR_ARGUMENT_NULL;

import java.util.Objects;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executor;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Stream;

/**
 * The {@code MetricsEventBus} is an intermediate solution to organize the communication and decouple components
 * until we have defined a more general, final solution.
 *
 * @param <T>
 * 		the event-class
 */
public class MetricsEventBus<T> {

    private final Executor executor;
    private final Queue<Consumer<? super T>> subscribers = new ConcurrentLinkedQueue<>();

    /**
     * Constructor of {@code MetricsEventBus}
     *
     * @param executor
     * 		An {@link Executor} that is used to notify subscribers
     * @throws NullPointerException in case {@code executor} parameter is {@code null}
     */
    public MetricsEventBus(final Executor executor) {
        Objects.requireNonNull(executor, String.format(ERROR_ARGUMENT_NULL, "executor"));
        this.executor = executor;
    }

    /**
     * Subscribe a new subscriber
     * <p>
     * The new subscriber will receive the given previous events after subscription.
     *
     * @param subscriber
     * 		The new {@code subscriber}
     * @param previousEvents
     * 		A {@link Supplier} of previous events. To ensure, that we do not miss events, this will be evaluated
     * 		after the subscriber was added.
     * @return a {@link Runnable} with which the subscriber can be unsubscribed
     * @throws NullPointerException in case {@code subscriber} parameter is {@code null}
     * @throws NullPointerException in case {@code previousEvents} parameter is {@code null}
     */
    public Runnable subscribe(final Consumer<? super T> subscriber, final Supplier<Stream<T>> previousEvents) {
        Objects.requireNonNull(subscriber, String.format(ERROR_ARGUMENT_NULL, "subscriber"));
        Objects.requireNonNull(previousEvents, String.format(ERROR_ARGUMENT_NULL, "previousEvents"));
        subscribers.add(subscriber);
        executor.execute(() -> previousEvents.get().forEach(subscriber));
        return () -> subscribers.remove(subscriber);
    }

    /**
     * Submit a new event.
     * <p>
     * This method will return immediately. The subscribers using the {@link Executor} of this class
     *
     * @param event
     * 		The Event that will be sent to all subscribers
     * @throws NullPointerException in case {@code event} parameter is {@code null}
     */
    public void submit(final T event) {
        Objects.requireNonNull(event, String.format(ERROR_ARGUMENT_NULL, "event"));
        executor.execute(() -> subscribers.forEach(subscriber -> subscriber.accept(event)));
    }
}
