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

import com.swirlds.common.wiring.transformers.WireFilter;
import com.swirlds.common.wiring.transformers.WireListSplitter;
import com.swirlds.common.wiring.transformers.WireTransformer;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * A class that can have its output soldered to wires/consumers.
 *
 * @param <O> the output type of the object
 * @param <C> the type of the implementing class (i.e. the type of this)
 */
public abstract class OutputChannel<O, C> {

    private final List<Consumer<O>> forwardingDestinations = new ArrayList<>();

    /**
     * Constructor.
     */
    protected OutputChannel() {}

    /**
     * Forward output data to any channels/consumers that are listening for it.
     *
     * @param data the output data to forward
     */
    protected void forward(@NonNull final O data) {
        // TODO catch exceptions and do something sensible
        for (final Consumer<O> destination : forwardingDestinations) {
            destination.accept(data);
        }
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
     * @param channel the channel to forward output data to
     * @return this
     */
    @NonNull
    public final C solderTo(@NonNull final InputChannel<O, ?> channel) {
        return solderTo(channel, false);
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
     * @param channel the channel to forward output data to
     * @param inject  if true then the data is injected and ignores back pressure. If false then back pressure is
     *                respected.
     * @return this
     */
    @SuppressWarnings("unchecked")
    @NonNull
    public C solderTo(@NonNull final InputChannel<O, ?> channel, final boolean inject) {
        if (inject) {
            forwardingDestinations.add(channel::inject);
        } else {
            forwardingDestinations.add(channel::put);
        }
        return (C) this;
    }

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
     * @param handler the consumer to forward output data to
     * @return this
     */
    @SuppressWarnings("unchecked")
    @NonNull
    public C solderTo(@NonNull final Consumer<O> handler) {
        forwardingDestinations.add(Objects.requireNonNull(handler));
        return (C) this;
    }

    /**
     * Build a {@link WireFilter} that is soldered to the output of this wire.
     *
     * @param predicate the predicate that filters the output of this wire
     * @return the filter
     */
    @NonNull
    public WireFilter<O> buildFilter(@NonNull final Predicate<O> predicate) {
        final WireFilter<O> filter = new WireFilter<>(Objects.requireNonNull(predicate));
        solderTo(filter);
        return filter;
    }

    /**
     * Build a {@link WireListSplitter} that is soldered to the output of this wire. Creating a splitter for wires
     * without a list output type will cause to runtime exceptions.
     *
     * @param <E> the type of the list elements
     * @return the splitter
     */
    @SuppressWarnings("unchecked")
    @NonNull
    public <E> WireListSplitter<E> buildSplitter() {
        final WireListSplitter<E> splitter = new WireListSplitter<>();
        solderTo((Consumer<O>) splitter);
        return splitter;
    }

    /**
     * Build a {@link WireListSplitter} that is soldered to the output of this wire. Creating a splitter for wires
     * without a list output type will cause to runtime exceptions.
     *
     * @param clazz the class of the list elements, convince parameter for hinting generic type to the compiler
     * @param <T>   the type of the list elements
     * @return the splitter
     */
    @NonNull
    public <T> WireListSplitter<T> buildSplitter(@NonNull Class<T> clazz) {
        return buildSplitter();
    }

    /**
     * Build a {@link WireTransformer} that is soldered to the output of this wire.
     *
     * @param transform the function that transforms the output of this wire into the output of the transformer
     * @param <T>       the output type of the transformer
     * @return the transformer
     */
    @NonNull
    public <T> WireTransformer<O, T> buildTransformer(@NonNull Function<O, T> transform) {
        final WireTransformer<O, T> transformer = new WireTransformer<>(transform);
        solderTo(transformer);
        return transformer;
    }
}
