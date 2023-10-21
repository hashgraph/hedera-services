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

import static com.swirlds.logging.LogMarker.EXCEPTION;

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
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Describes the output of a wire (or wire-like object). Can be soldered to wire input channels or lambdas.
 *
 * @param <O> the output type of the object
 */
public abstract class OutputChannel<O> {

    private static final Logger logger = LogManager.getLogger(OutputChannel.class);

    private final WiringModel model;
    private final List<Consumer<O>> forwardingDestinations = new ArrayList<>();

    /**
     * Constructor.
     *
     * @param model the wiring model containing this output channel
     */
    protected OutputChannel(@NonNull final WiringModel model) {
        this.model = Objects.requireNonNull(model);
    }

    /**
     * Get the wiring model containing this output channel.
     *
     * @return the wiring model
     */
    @NonNull
    protected WiringModel getModel() { // TODO is this needed?
        return model;
    }

    /**
     * Forward output data to any channels/consumers that are listening for it.
     *
     * @param data the output data to forward
     */
    protected void forward(@NonNull final O data) {
        for (final Consumer<O> destination : forwardingDestinations) {
            try {
                destination.accept(data);
            } catch (final Exception e) {
                // Future work: if we ever add a name to output channels, include that name in this log message.
                logger.error(EXCEPTION.getMarker(), "Exception thrown while forwarding data {}", data, e);
            }
        }
    }

    /**
     * Specify a channel where output data should be passed. This forwarding operation respects back pressure.
     *
     * <p>
     * Soldering is the act of connecting two wires together, usually by melting a metal alloy between them. See
     * https://en.wikipedia.org/wiki/Soldering
     *
     * <p>
     * Forwarding should be fully configured prior to data being inserted into the wire. Adding forwarding destinations
     * after data has been inserted into the wire is not thread safe and has undefined behavior.
     *
     * @param channel the channel to forward output data to
     * @return this
     */
    @NonNull
    public final OutputChannel<O> solderTo(@NonNull final InputChannel<O, ?> channel) {
        return solderTo(channel, false);
    }

    /**
     * Specify a channel where output data should be forwarded.
     *
     * <p>
     * Soldering is the act of connecting two wires together, usually by melting a metal alloy between them. See
     * https://en.wikipedia.org/wiki/Soldering
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
    public OutputChannel<O> solderTo(@NonNull final InputChannel<O, ?> channel, final boolean inject) {
        if (inject) {
            forwardingDestinations.add(channel::inject);
        } else {
            forwardingDestinations.add(channel::put);
        }
        return this;
    }

    /**
     * Specify a consumer where output data should be forwarded.
     *
     * <p>
     * Soldering is the act of connecting two wires together, usually by melting a metal alloy between them. See
     * https://en.wikipedia.org/wiki/Soldering
     *
     * <p>
     * Forwarding should be fully configured prior to data being inserted into the wire. Adding forwarding destinations
     * after data has been inserted into the wire is not thread safe and has undefined behavior.
     *
     * @param handler the consumer to forward output data to
     * @return this
     */
    @NonNull
    public OutputChannel<O> solderTo(@NonNull final Consumer<O> handler) {
        forwardingDestinations.add(Objects.requireNonNull(handler));
        return this;
    }

    /**
     * Build a {@link WireFilter} that is soldered to the output of this wire.
     *
     * @param predicate the predicate that filters the output of this wire
     * @return the filter
     */
    @NonNull
    public OutputChannel<O> buildFilter(@NonNull final Predicate<O> predicate) {
        final WireFilter<O> filter = new WireFilter<>(model, Objects.requireNonNull(predicate));
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
    public <E> OutputChannel<E> buildSplitter() {
        final WireListSplitter<E> splitter = new WireListSplitter<>(model);
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
    public <T> OutputChannel<T> buildSplitter(@NonNull Class<T> clazz) {
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
    public <T> OutputChannel<T> buildTransformer(@NonNull Function<O, T> transform) {
        final WireTransformer<O, T> transformer = new WireTransformer<>(model, transform);
        solderTo(transformer);
        return transformer;
    }
}
