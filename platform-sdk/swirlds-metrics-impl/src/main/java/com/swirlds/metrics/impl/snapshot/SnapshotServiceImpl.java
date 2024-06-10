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

package com.swirlds.metrics.impl.snapshot;

import com.swirlds.base.state.Startable;
import com.swirlds.base.state.Stoppable;
import com.swirlds.metrics.api.Metric;
import com.swirlds.metrics.api.Metrics;
import com.swirlds.metrics.api.snapshot.Label;
import com.swirlds.metrics.api.snapshot.SnapshotableMetric;
import com.swirlds.metrics.api.snapshot.Subscription;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.time.Duration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Future.State;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;
import java.util.function.Function;

public class SnapshotServiceImpl implements Startable, Stoppable {

    private final Metrics metrics;

    private final ScheduledExecutorService executor;

    private final Queue<Consumer<? super SnapshotEvent>> subscribers = new ConcurrentLinkedQueue<>();

    private final List<Function<Metric, Label>> labelFactories = new CopyOnWriteArrayList<>();

    private final AtomicReference<ScheduledFuture<?>> scheduledFuture = new AtomicReference<>();

    private final Lock executionLock = new ReentrantLock();

    private final long delayNanos;

    public SnapshotServiceImpl(
            @NonNull final Metrics metrics,
            @NonNull final ScheduledExecutorService executor,
            @NonNull final Duration interval) {
        this.metrics = Objects.requireNonNull(metrics, "metrics must not be null");
        this.executor = Objects.requireNonNull(executor, "executor must not be null");
        this.delayNanos =
                Objects.requireNonNull(interval, "interval must not be null").toNanos();

        addLabelFactory(m -> new Label("category", m.getCategory()));
        addLabelFactory(m -> new Label("name", m.getName()));
    }

    public boolean isRunning() {
        executionLock.lock();
        try {
            return Optional.ofNullable(scheduledFuture.get())
                    .map(ScheduledFuture::state)
                    .filter(State.RUNNING::equals)
                    .isPresent();
        } finally {
            executionLock.unlock();
        }
    }

    @Override
    public void start() {
        executionLock.lock();
        try {
            if (isRunning()) {
                throw new IllegalStateException("The snapshot-service is already running");
            }
            final ScheduledFuture<?> future =
                    executor.scheduleAtFixedRate(this::mainLoop, 0, delayNanos, TimeUnit.NANOSECONDS);
            scheduledFuture.set(future);
        } finally {
            executionLock.unlock();
        }
    }

    @Override
    public void stop() {
        executionLock.lock();
        try {
            if (isRunning()) {
                final boolean canceled = Optional.ofNullable(scheduledFuture.get())
                        .map(future -> future.cancel(true))
                        .orElse(true);
                if (!canceled && isRunning()) {
                    throw new IllegalStateException("Failed to cancel scheduled background task");
                }
            }
        } finally {
            executionLock.unlock();
        }
    }

    public void addLabelFactory(@NonNull final Function<Metric, Label> labelFactory) {
        Objects.requireNonNull(labelFactory, "labelFactory must not be null");
        labelFactories.add(labelFactory);
    }

    public Subscription subscribe(@NonNull final Consumer<? super SnapshotEvent> subscriber) {
        Objects.requireNonNull(subscriber, "subscriber must not be null");
        subscribers.add(subscriber);
        return () -> subscribers.remove(subscriber);
    }

    private void mainLoop() {
        final Map<String, Snapshot> allSnapshots = new HashMap<>();
        metrics.getAll().forEach(m -> {
            if (m instanceof SnapshotableMetric metric) {
                final String identifier = metric.getIdentifier();
                final Set<Label> labels = new HashSet<>(metric.getLabels());
                labelFactories.stream().map(factory -> factory.apply(metric)).forEach(labels::add);
                final Snapshot snapshot = new Snapshot(metric, metric.takeSnapshot(), labels);
                allSnapshots.put(identifier, snapshot);
            }
            final SnapshotEvent event = new SnapshotEvent(allSnapshots.values());
            subscribers.forEach(subscriber -> subscriber.accept(event));
        });
    }
}
