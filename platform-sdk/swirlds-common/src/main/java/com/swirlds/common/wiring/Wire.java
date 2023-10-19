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

package com.swirlds.common.wiring;

import com.swirlds.base.time.Time;
import com.swirlds.common.metrics.Metrics;
import com.swirlds.common.wiring.counters.ObjectCounter;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.util.function.Consumer;

/**
 * Wires two or more components together.
 *
 * @param <O> the output time of the wire (use {@link Void}) for a wire with no output type)
 */
public abstract class Wire<O> {

    /**
     * Get a new wire builder.
     *
     * @param name the name of the wire. Used for metrics and debugging. Must be unique (not enforced by framework).
     *             Must only contain alphanumeric characters, underscores, and hyphens (enforced by framework).
     * @return a new wire builder
     */
    public static <O> WireBuilder<O> builder(@NonNull final String name) {
        return new WireBuilder<>(name);
    }

    /**
     * Get a new wire metrics builder. Can be passed to {@link WireBuilder#withMetricsBuilder(WireMetricsBuilder)} to
     * add metrics to the wire.
     *
     * @param metrics the metrics framework
     * @param time    provides wall clock time
     * @return a new wire metrics builder
     */
    public static WireMetricsBuilder metricsBuilder(@NonNull final Metrics metrics, @NonNull final Time time) {
        return new WireMetricsBuilder(metrics, time);
    }

    /**
     * Get the name of the wire.
     *
     * @return the name of the wire
     */
    @NonNull
    public abstract String getName();

    /**
     * Get the number of unprocessed tasks. Returns -1 if this wire is not monitoring the number of unprocessed tasks.
     * Wires do not track the number of unprocessed tasks by default. This method will always return -1 unless one of
     * the following is true:
     * <ul>
     * <li>{@link WireMetricsBuilder#withUnhandledTaskMetricEnabled(boolean)} is called with the value true</li>
     * <li>{@link WireBuilder#withUnhandledTaskCapacity(long)} is passed a positive value</li>
     * <li>{@link WireBuilder#withOnRamp(ObjectCounter)} is passed a counter that is not a no op counter</li>
     * </ul>
     */
    public abstract long getUnprocessedTaskCount();

    /**
     * Get a wire channel for inserting data into this wire. In order to use this channel, a handler must be bound via
     * {@link WireChannel#bind(Consumer)}.
     *
     * @param <I> the type of data that is inserted into the channel
     * @return the channel
     */
    @NonNull
    public final <I> WireChannel<I, O> createChannel() {
        return new WireChannel<>(this);
    }

    /**
     * Specify a channel where output data should be passed. This forwarding operation respects back pressure.
     *
     * <p>
     * "Solder" in this context is pronounced "sodder". It rhymes with "odder". (Don't blame me, English is wierd.
     * Anyways, we stole this word from the French.) Soldering is the act of connecting two wires together, usually by
     * melting a metal alloy between them. See https://en.wikipedia.org/wiki/Soldering
     *
     * <p>
     * Forwarding should be fully configured prior to data being inserted into the wire. Adding forwarding destinations
     * after data has been inserted into the wire is not thread safe and has undefined behavior.
     *
     * @param channels the channels to forward output data to
     * @return this
     */
    @SuppressWarnings("UnusedReturnValue")
    @SafeVarargs
    @NonNull
    public final Wire<O> solderTo(@NonNull final WireChannel<O, ?>... channels) {
        return solderTo(false, channels);
    }

    /**
     * Specify a channel where output data should be forwarded.
     *
     * <p>
     * "Solder" in this context is pronounced "sodder". It rhymes with "odder". (Don't blame me, English is wierd.
     * Anyways, we stole this word from the French.) Soldering is the act of connecting two wires together, usually by
     * melting a metal alloy between them. See https://en.wikipedia.org/wiki/Soldering
     *
     * <p>
     * Forwarding should be fully configured prior to data being inserted into the wire. Adding forwarding destinations
     * after data has been inserted into the wire is not thread safe and has undefined behavior.
     *
     * @param inject  if true then the data is injected and ignores back pressure. If false then back pressure is
     *                respected.
     * @param channels the channels to forward output data to
     * @return this
     */
    @SuppressWarnings("unchecked")
    @NonNull
    public abstract Wire<O> solderTo(final boolean inject, @NonNull final WireChannel<O, ?>... channels);

    /**
     * Specify a consumer where output data should be forwarded.
     *
     * <p>
     * "Solder" in this context is pronounced "sodder". It rhymes with "odder". (Don't blame me, English is wierd.
     * Anyways, we stole this word from the French.) Soldering is the act of connecting two wires together, usually by
     * melting a metal alloy between them. See https://en.wikipedia.org/wiki/Soldering
     *
     * <p>
     * Forwarding should be fully configured prior to data being inserted into the wire. Adding forwarding destinations
     * after data has been inserted into the wire is not thread safe and has undefined behavior.
     *
     * @param handlers the consumers to forward output data to
     * @return this
     */
    @SuppressWarnings("unchecked")
    @NonNull
    public abstract Wire<O> solderTo(@NonNull final Consumer<O>... handlers);

    /**
     * Flush all data in the wire. Blocks until all data currently in flight has been processed.
     *
     * <p>
     * Note: must be enabled by passing true to {@link WireBuilder#withFlushingEnabled(boolean)}.
     *
     * <p>
     * Warning: some implementations of flush may block indefinitely if new work is continuously added to the wire while
     * flushing. Such implementations are guaranteed to finish flushing once new work is no longer being added. Some
     * implementations do not have this restriction, and will return as soon as all of the in flight work has been
     * processed, regardless of whether or not new work is being added.
     *
     * @throws UnsupportedOperationException if {@link WireBuilder#withFlushingEnabled(boolean)} was set to false (or
     *                                       was unset, default is false)
     */
    public abstract void flush();

    /**
     * Flush all data in the wire. Blocks until all data currently in flight has been processed or until the thread is
     * interrupted.
     *
     * <p>
     * Note: must be enabled by passing true to {@link WireBuilder#withFlushingEnabled(boolean)}.
     *
     * <p>
     * Warning: some implementations of flush may block indefinitely if new work is continuously added to the wire while
     * flushing. Such implementations are guaranteed to finish flushing once new work is no longer being added. Some
     * implementations do not have this restriction, and will return as soon as all of the in flight work has been
     * processed, regardless of whether or not new work is being added.
     *
     * @throws InterruptedException          if the thread is interrupted while waiting for all data to be processed
     * @throws UnsupportedOperationException if {@link WireBuilder#withFlushingEnabled(boolean)} was set to false (or
     *                                       was unset, default is false)
     */
    public abstract void interruptableFlush() throws InterruptedException;

    /**
     * Forward output data to any channels that are listening for it.
     *
     * @param data the output data to forward
     */
    protected abstract void forwardOutput(@NonNull final O data);

    /**
     * Add a task to the wire. May block if back pressure is enabled. Similar to
     * {@link #interruptablePut(Consumer, Object)} except that it cannot be interrupted and can block forever if
     * backpressure is enabled.
     *
     * @param handler handles the provided data
     * @param data    the data to be processed by the wire
     */
    protected abstract void put(@NonNull Consumer<Object> handler, @Nullable Object data);

    /**
     * Add a task to the wire. May block if back pressure is enabled. If backpressure is enabled and being applied, this
     * method can be interrupted.
     *
     * @param data the data to be processed by the wire
     * @throws InterruptedException if the thread is interrupted while waiting for capacity to become available
     */
    protected abstract void interruptablePut(@NonNull Consumer<Object> handler, @Nullable Object data)
            throws InterruptedException;

    /**
     * Add a task to the wire. If backpressure is enabled and there is not immediately capacity available, this method
     * will not accept the data.
     *
     * @param data the data to be processed by the wire
     * @return true if the data was accepted, false otherwise
     */
    protected abstract boolean offer(@NonNull Consumer<Object> handler, @Nullable Object data);

    /**
     * Inject data into the wire, doing so even if it causes the number of unprocessed tasks to exceed the capacity
     * specified by configured back pressure. If backpressure is disabled, this operation is logically equivalent to
     * {@link #put(Consumer, Object)}.
     *
     * @param data the data to be processed by the wire
     */
    protected abstract void inject(@NonNull Consumer<Object> handler, @Nullable Object data);
}
