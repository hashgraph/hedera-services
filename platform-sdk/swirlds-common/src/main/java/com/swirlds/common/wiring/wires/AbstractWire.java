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

package com.swirlds.common.wiring.wires;

import com.swirlds.common.wiring.Wire;
import com.swirlds.common.wiring.WireChannel;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * Boilerplate implementation for wires. This is intentionally not included in Wire.java for the sake of keeping the API
 * clean and separated from implementation details.
 *
 * @param <O> the output type of the wire
 */
public abstract class AbstractWire<O> extends Wire<O> {
    private final String name;
    private final List<Consumer<O>> forwardingDestinations = new ArrayList<>();
    private final boolean flushEnabled;

    /**
     * Constructor.
     *
     * @param name the name of the wire
     */
    protected AbstractWire(@NonNull final String name, final boolean flushEnabled) {
        this.name = name;
        this.flushEnabled = flushEnabled;
    }

    /**
     * {@inheritDoc}
     */
    @NonNull
    @Override
    public final String getName() {
        return name;
    }

    /**
     * {@inheritDoc}
     */
    @SafeVarargs
    @NonNull
    @Override
    public final Wire<O> solderTo(final boolean inject, @NonNull final WireChannel<O, ?>... channels) {
        Objects.requireNonNull(channels);

        for (final WireChannel<O, ?> channel : channels) {
            if (inject) {
                forwardingDestinations.add(channel::inject);
            } else {
                forwardingDestinations.add(channel::put);
            }
        }

        return this;
    }

    /**
     * {@inheritDoc}
     */
    @SafeVarargs
    @NonNull
    @Override
    public final Wire<O> solderTo(@NonNull final Consumer<O>... handlers) {
        for (final Consumer<O> handler : handlers) {
            forwardingDestinations.add(Objects.requireNonNull(handler));
        }
        return this;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected final void forwardOutput(@NonNull final O data) {
        for (final Consumer<O> destination : forwardingDestinations) {
            destination.accept(data);
        }
    }

    /**
     * Throw an {@link UnsupportedOperationException} if flushing is not enabled.
     */
    protected final void throwIfFlushDisabled() {
        if (!flushEnabled) {
            throw new UnsupportedOperationException("Flushing is not enabled the wire " + getName());
        }
    }
}
