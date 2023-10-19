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

package com.swirlds.common.wiring.transformers;

import com.swirlds.common.wiring.WireChannel;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * Transforms wire data from one type to another.
 *
 * @param <A> the input type
 * @param <B> the output type
 * @param <T> the type of the transformer (i.e. the type of this)
 */
abstract class AbstractWireTransformer<A, B, T extends AbstractWireTransformer<A, B, T>> implements Consumer<A> {

    private final List<Consumer<B>> forwardingDestinations = new ArrayList<>();

    // TODO might want to do some generic magic to make the return type correct

    /**
     * Specify a channel where output data should be passed. This forwarding operation respects back pressure.
     *
     * @param channels the channels to forward output data to
     * @return this
     */
    @SafeVarargs
    @NonNull
    public final T solderTo(@NonNull final WireChannel<B, ?>... channels) {
        return solderTo(false, channels);
    }

    /**
     * Specify a channel where output data should be forwarded.
     *
     * @param inject   if true then the data is injected and ignores back pressure. If false then back pressure is
     *                 respected.
     * @param channels the channels to forward output data to
     * @return this
     */
    @SuppressWarnings("unchecked")
    @NonNull
    public final T solderTo(final boolean inject, @NonNull final WireChannel<B, ?>... channels) {
        for (final WireChannel<B, ?> channel : channels) {
            if (inject) {
                forwardingDestinations.add(channel::inject);
            } else {
                forwardingDestinations.add(channel::put);
            }
        }
        return (T) this;
    }

    /**
     * Specify a consumer where output data should be forwarded.
     *
     * @param handlers the consumers to forward output data to
     * @return this
     */
    @SuppressWarnings("unchecked")
    @NonNull
    public final T solderTo(@NonNull final Consumer<B> handlers) {
        for (final Consumer<B> handler : forwardingDestinations) {
            forwardingDestinations.add(Objects.requireNonNull(handler));
        }
        return (T) this;
    }

    /**
     * Forward a transformed data element to all subscribers.
     *
     * @param b the data element to forward
     */
    protected void forward(@NonNull final B b) {
        for (final Consumer<B> destination : forwardingDestinations) {
            destination.accept(b);
        }
    }

    /**
     * This method is passed objects of type A. It can do whatever it wants with them, and then pass an arbitrary number
     * of non-null objects of type B to the {@link #forward(Object)} method.
     */
    @Override
    public abstract void accept(@NonNull A a);
}
