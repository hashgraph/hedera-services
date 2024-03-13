/*
 * Copyright (C) 2023-2024 Hedera Hashgraph, LLC
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

package com.swirlds.common.wiring.schedulers;

import com.swirlds.common.wiring.counters.ObjectCounter;
import com.swirlds.common.wiring.model.internal.StandardWiringModel;
import com.swirlds.common.wiring.schedulers.builders.TaskSchedulerBuilder;
import com.swirlds.common.wiring.schedulers.builders.TaskSchedulerMetricsBuilder;
import com.swirlds.common.wiring.schedulers.builders.TaskSchedulerType;
import com.swirlds.common.wiring.schedulers.internal.DefaultSquelcher;
import com.swirlds.common.wiring.schedulers.internal.Squelcher;
import com.swirlds.common.wiring.schedulers.internal.ThrowingSquelcher;
import com.swirlds.common.wiring.wires.input.Bindable;
import com.swirlds.common.wiring.wires.input.BindableInputWire;
import com.swirlds.common.wiring.wires.input.InputWire;
import com.swirlds.common.wiring.wires.input.TaskSchedulerInput;
import com.swirlds.common.wiring.wires.output.OutputWire;
import com.swirlds.common.wiring.wires.output.StandardOutputWire;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Schedules tasks for a component.
 * <p>
 * The lifecycle of a task is as follows:
 * <ol>
 * <li>Unscheduled: the task has not been passed to the scheduler yet (e.g. via {@link InputWire#put(Object)})</li>
 * <li>Scheduled but not processed: the task has been passed to the scheduler but the corresponding handler has not
 * yet returned (either because the handler has not yet been called or because the handler has been called but hasn't
 * finished yet)</li>
 * <li>Processed: the corresponding handle method for the task has been called and has returned.</li>
 * </ol>
 *
 * @param <OUT> the output type of the primary output wire (use {@link Void} if no output is needed)
 */
public abstract class TaskScheduler<OUT> extends TaskSchedulerInput<OUT> {

    private final boolean flushEnabled;
    private final StandardWiringModel model;
    private final String name;
    private final TaskSchedulerType type;
    private final StandardOutputWire<OUT> primaryOutputWire;
    private final boolean insertionIsBlocking;

    /**
     * Handles squelching for this task scheduler. Will be a valid object whether or not squelching is enabled for this
     * task scheduler.
     */
    private final Squelcher squelcher;

    /**
     * Constructor.
     *
     * @param model               the wiring model containing this task scheduler
     * @param name                the name of the task scheduler
     * @param type                the type of task scheduler
     * @param flushEnabled        if true, then {@link #flush()} will be enabled, otherwise it will throw.
     * @param squelchingEnabled   if true, then squelching will be enabled, otherwise trying to squelch will throw.
     * @param insertionIsBlocking when data is inserted into this task scheduler, will it block until capacity is
     *                            available?
     */
    protected TaskScheduler(
            @NonNull final StandardWiringModel model,
            @NonNull final String name,
            @NonNull final TaskSchedulerType type,
            final boolean flushEnabled,
            final boolean squelchingEnabled,
            final boolean insertionIsBlocking) {

        this.model = Objects.requireNonNull(model);
        this.name = Objects.requireNonNull(name);
        this.type = Objects.requireNonNull(type);
        this.flushEnabled = flushEnabled;

        if (squelchingEnabled) {
            this.squelcher = new DefaultSquelcher();
        } else {
            this.squelcher = new ThrowingSquelcher();
        }

        primaryOutputWire = new StandardOutputWire<>(model, name);
        this.insertionIsBlocking = insertionIsBlocking;
    }

    /**
     * Build an input wire for passing data to this task scheduler. In order to use this wire, a handler must be bound
     * via {@link BindableInputWire#bind(Function)} {@link BindableInputWire#bindConsumer(Consumer)}.
     *
     * @param name the name of the input wire
     * @param <I>  the type of data that is inserted via this input wire
     * @return the input wire
     */
    @NonNull
    public final <I> BindableInputWire<I, OUT> buildInputWire(@NonNull final String name) {
        return new BindableInputWire<>(model, this, name);
    }

    /**
     * Get the default output wire for this task scheduler. Sometimes referred to as the "primary" output wire. All data
     * returned by handlers is passed ot this output wire. Calling this method more than once will always return the
     * same object.
     *
     * @return the primary output wire
     */
    @NonNull
    public OutputWire<OUT> getOutputWire() {
        return primaryOutputWire;
    }

    /**
     * By default a component has a single output wire (i.e. the primary output wire). This method allows additional
     * output wires to be created.
     *
     * <p>
     * Unlike primary wires, secondary output wires need to be passed to a component's constructor. It is considered a
     * violation of convention to push data into a secondary output wire from any code that is not executing within this
     * task scheduler.
     *
     * @param <T> the type of data that is transmitted over this output wire
     * @return the secondary output wire
     */
    @NonNull
    public <T> StandardOutputWire<T> buildSecondaryOutputWire() {
        // Intentionally do not register this with the model. Connections using this output wire will be represented
        // in the model in the same way as connections to the primary output wire.
        return new StandardOutputWire<>(model, name);
    }

    /**
     * Build an input wire that produces an instant (reflecting current time) at the specified rate. Note that the exact
     * rate of heartbeats may vary. This is a best effort algorithm, and actual rates may vary depending on a variety of
     * factors.
     *
     * @param name   the name of the input
     * @param period the period of the heartbeat. For example, setting a period of 100ms will cause the heartbeat to be
     *               sent at 10 hertz. Note that time is measured at millisecond precision, and so periods less than 1ms
     *               are not supported.
     * @return a bindable object that allows for the implementation of the heartbeat handler to be bound to the input
     * wire that provides the heartbeats
     * @throws IllegalStateException if the heartbeat has already started
     */
    @NonNull
    public Bindable<Instant, OUT> buildHeartbeatInputWire(@NonNull final String name, @NonNull final Duration period) {
        final BindableInputWire<Instant, OUT> inputWire = buildInputWire(name);
        model.buildHeartbeatWire(period).solderTo(inputWire);
        return inputWire;
    }

    /**
     * Build a wire that produces an instant (reflecting current time) at the specified rate. Note that the exact rate
     * of heartbeats may vary. This is a best effort algorithm, and actual rates may vary depending on a variety of
     * factors.
     *
     * @param name      the name of the input
     * @param frequency the frequency of the heartbeat in hertz. Note that time is measured at millisecond precision,
     *                  and so frequencies greater than 1000hz are not supported.
     * @return a bindable object that allows for the implementation of the heartbeat handler to be bound to the input
     * wire that provides the heartbeats
     */
    @NonNull
    public Bindable<Instant, OUT> buildHeartbeatInputWire(@NonNull final String name, final double frequency) {
        final BindableInputWire<Instant, OUT> inputWire = buildInputWire(name);
        model.buildHeartbeatWire(frequency).solderTo(inputWire);
        return inputWire;
    }

    /**
     * Get the name of this task scheduler.
     *
     * @return the name of this task scheduler
     */
    @NonNull
    public String getName() {
        return name;
    }

    /**
     * Get the type of this task scheduler.
     *
     * @return the type of this task scheduler
     */
    @NonNull
    public TaskSchedulerType getType() {
        return type;
    }

    /**
     * Get whether or not this task scheduler can block when data is inserted into it.
     *
     * @return true if this task scheduler can block when data is inserted into it, false otherwise
     */
    public boolean isInsertionBlocking() {
        return insertionIsBlocking;
    }

    /**
     * Cast this scheduler into whatever a variable is expecting. Sometimes the compiler gets confused with generics,
     * and path of least resistance is to just cast to the proper data type.
     *
     * <p>
     * Warning: this will appease the compiler, but it is possible to cast a scheduler into a data type that will cause
     * runtime exceptions. Use with appropriate caution.
     *
     * @param <X> the type to cast to
     * @return this, cast into whatever type is requested
     */
    @NonNull
    @SuppressWarnings("unchecked")
    public final <X> TaskScheduler<X> cast() {
        return (TaskScheduler<X>) this;
    }

    /**
     * Get the number of unprocessed tasks. A task is considered to be unprocessed until the data has been passed to the
     * handler method (i.e. the one given to {@link BindableInputWire#bind(Function)} or
     * {@link BindableInputWire#bindConsumer(Consumer)}) and that handler method has returned.
     * <p>
     * Returns {@link ObjectCounter#COUNT_UNDEFINED} if this task scheduler is not monitoring the number of unprocessed
     * tasks. Schedulers do not track the number of unprocessed tasks by default. This method will always return
     * {@link ObjectCounter#COUNT_UNDEFINED} unless one of the following is true:
     * <ul>
     * <li>{@link TaskSchedulerMetricsBuilder#withUnhandledTaskMetricEnabled(boolean)} is called with the value
     * true</li>
     * <li>{@link TaskSchedulerBuilder#withUnhandledTaskCapacity(long)} is passed a positive value</li>
     * <li>{@link TaskSchedulerBuilder#withOnRamp(ObjectCounter)} is passed a counter that is not a no op counter</li>
     * </ul>
     */
    public abstract long getUnprocessedTaskCount();

    /**
     * Flush all data in the task scheduler. Blocks until all data currently in flight has been processed.
     *
     * <p>
     * Note: must be enabled by passing true to {@link TaskSchedulerBuilder#withFlushingEnabled(boolean)}.
     *
     * <p>
     * Warning: some implementations of flush may block indefinitely if new work is continuously added to the scheduler
     * while flushing. Such implementations are guaranteed to finish flushing once new work is no longer being added.
     * Some implementations do not have this restriction, and will return as soon as all of the in flight work has been
     * processed, regardless of whether or not new work is being added.
     *
     * @throws UnsupportedOperationException if {@link TaskSchedulerBuilder#withFlushingEnabled(boolean)} was set to
     *                                       false (or was unset, default is false)
     */
    public abstract void flush();

    /**
     * Throw an {@link UnsupportedOperationException} if flushing is not enabled.
     */
    protected final void throwIfFlushDisabled() {
        if (!flushEnabled) {
            throw new UnsupportedOperationException("Flushing is not enabled for the task scheduler " + name);
        }
    }

    /**
     * Start squelching, and continue doing so until {@link #stopSquelching()} is called.
     *
     * @throws UnsupportedOperationException if squelching is not supported by this scheduler
     * @throws IllegalStateException         if scheduler is already squelching
     */
    public void startSquelching() {
        squelcher.startSquelching();
    }

    /**
     * Stop squelching.
     *
     * @throws UnsupportedOperationException if squelching is not supported by this scheduler
     * @throws IllegalStateException         if scheduler is not currently squelching
     */
    public void stopSquelching() {
        squelcher.stopSquelching();
    }

    /**
     * Get whether or not this task scheduler is currently squelching.
     *
     * @return true if this task scheduler is currently squelching, false otherwise
     */
    public final boolean currentlySquelching() {
        return squelcher.shouldSquelch();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void forward(@NonNull final OUT data) {
        primaryOutputWire.forward(data);
    }
}
