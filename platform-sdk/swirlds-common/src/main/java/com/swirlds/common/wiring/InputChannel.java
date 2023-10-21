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

import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * An object that can insert work to be handled on a wire.
 *
 * @param <I> the type of data that passes into the channel
 * @param <O> the type of data that comes out of the channel
 */
public class InputChannel<I, O> {

    private final Wire<O> wire;
    private Consumer<Object> handler;
    private String name;

    /**
     * Constructor.
     *
     * @param wire the wire to insert data into
     * @param name the name of the input channel
     */
    InputChannel(@NonNull final Wire<O> wire, @NonNull final String name) {
        this.wire = Objects.requireNonNull(wire);
        this.name = Objects.requireNonNull(name); // TODO formatting checks on name
    }

    /**
     * Cast this wire channel into whatever a variable is expecting. Sometimes the compiler gets confused with generics,
     * and path of least resistance is to just cast to the proper data type.
     *
     * <p>
     * Warning: this will appease the compiler, but it is possible to cast a wire into a data type that will cause
     * runtime exceptions. Use with appropriate caution.
     *
     * @param <A> the input type to cast to
     * @param <B> the output type to cast to
     * @return this, cast into whatever type is requested
     */
    @NonNull
    @SuppressWarnings("unchecked")
    public final <A, B> InputChannel<A, B> cast() {
        return (InputChannel<A, B>) this;
    }

    /**
     * Convenience method for creating a channel with a specific input type. This method is useful for when the compiler
     * can't figure out the generic type of the channel. This method is a no op.
     *
     * @param inputType the input type of the channel
     * @param <X>       the input type of the channel
     * @return this
     */
    @SuppressWarnings("unchecked")
    public <X> InputChannel<X, O> withInputType(@NonNull final Class<X> inputType) {
        return (InputChannel<X, O>) this;
    }

    /**
     * Bind this inserter to a handler. A handler must be bound to this inserter prior to inserting data via any
     * method.
     *
     * @param handler the handler to bind to this inserter
     * @return this
     * @throws IllegalStateException if a handler is already bound and this method is called a second time
     */
    @SuppressWarnings("unchecked")
    @NonNull
    public InputChannel<I, O> bind(@NonNull final Consumer<I> handler) {
        if (this.handler != null) {
            throw new IllegalStateException("Handler already bound");
        }
        this.handler = (Consumer<Object>) Objects.requireNonNull(handler);

        return this;
    }

    /**
     * Bind this inserter to a handler. A handler must be bound to this inserter prior to inserting data via any
     * method.
     *
     * @param handler the handler to bind to this inserter
     * @return this
     * @throws IllegalStateException if a handler is already bound and this method is called a second time
     */
    @SuppressWarnings("unchecked")
    @NonNull
    public InputChannel<I, O> bind(@NonNull final Function<I, O> handler) {
        if (this.handler != null) {
            throw new IllegalStateException("Handler already bound");
        }
        this.handler = i -> {
            final O output = handler.apply((I) i);
            if (output != null) {
                wire.forward(output);
            }
        };

        return this;
    }

    /**
     * Add a task to the wire. May block if back pressure is enabled. Similar to {@link #interruptablePut(Object)}
     * except that it cannot be interrupted and can block forever if backpressure is enabled.
     *
     * @param data the data to be processed by the wire
     */
    public void put(@Nullable final I data) {
        wire.put(handler, data);
    }

    /**
     * Add a task to the wire. May block if back pressure is enabled. If backpressure is enabled and being applied, this
     * method can be interrupted.
     *
     * @param data the data to be processed by the wire
     * @throws InterruptedException if the thread is interrupted while waiting for capacity to become available
     */
    public void interruptablePut(@Nullable final I data) throws InterruptedException {
        wire.interruptablePut(handler, data);
    }

    /**
     * Add a task to the wire. If backpressure is enabled and there is not immediately capacity available, this method
     * will not accept the data.
     *
     * @param data the data to be processed by the wire
     * @return true if the data was accepted, false otherwise
     */
    public boolean offer(@Nullable final I data) {
        return wire.offer(handler, data);
    }

    /**
     * Inject data into the wire, doing so even if it causes the number of unprocessed tasks to exceed the capacity
     * specified by configured back pressure. If backpressure is disabled, this operation is logically equivalent to
     * {@link #put(Object)}.
     *
     * @param data the data to be processed by the wire
     */
    public void inject(@Nullable final I data) {
        wire.inject(handler, data);
    }
}
