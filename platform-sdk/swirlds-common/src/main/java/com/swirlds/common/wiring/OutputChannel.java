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
    private final String name;
    private final List<Consumer<O>> forwardingDestinations = new ArrayList<>();

    /**
     * Constructor.
     *
     * @param model               the wiring model containing this output channel
     * @param name                the name of the output channel
     * @param insertionIsBlocking when data is inserted into this channel, will it block until capacity is available?
     */
    protected OutputChannel(
            @NonNull final WiringModel model, @NonNull final String name, final boolean insertionIsBlocking) {
        this.model = Objects.requireNonNull(model);
        this.name = Objects.requireNonNull(name);

        model.registerVertex(name, insertionIsBlocking);
    }

    /**
     * Get the wiring model containing this output channel.
     *
     * @return the wiring model
     */
    @NonNull
    protected WiringModel getModel() { // TODO is this needed?
        return model;
    } // TODO necessary?

    /**
     * Get the name of this output channel. If this object is a wire, this is the same as the name of the wire.
     *
     * @return the name
     */
    @NonNull
    public String getName() {
        return name;
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
        model.registerEdge(name, channel.getWireName(), channel.getName(), false);
        forwardingDestinations.add(channel::put);
        return this;
    }

    /**
     * Specify a channel where output data should be forwarded via injection. Injection ignores back pressure at the
     * destination.
     *
     * <p>
     * Soldering is the act of connecting two wires together, usually by melting a metal alloy between them. See
     * <a href="https://en.wikipedia.org/wiki/Soldering">wikipedia's entry on soldering</a>.
     *
     * <p>
     * Forwarding should be fully configured prior to data being inserted into the wire. Adding forwarding destinations
     * after data has been inserted into the wire is not thread safe and has undefined behavior.
     *
     * @param channel the channel to forward output data to
     * @return this
     */
    @NonNull
    public OutputChannel<O> injectionSolderTo(@NonNull final InputChannel<O, ?> channel) {
        model.registerEdge(name, channel.getWireName(), channel.getName(), true);
        forwardingDestinations.add(channel::inject);
        return this;
    }

    /**
     * Specify a consumer where output data should be forwarded.
     *
     * <p>
     * Soldering is the act of connecting two wires together, usually by melting a metal alloy between them. See
     * <a href="https://en.wikipedia.org/wiki/Soldering">wikipedia's entry on soldering</a>.
     *
     * <p>
     * Forwarding should be fully configured prior to data being inserted into the wire. Adding forwarding destinations
     * after data has been inserted into the wire is not thread safe and has undefined behavior.
     *
     * @param handlerName the name of the consumer
     * @param handler     the consumer to forward output data to
     * @return this
     */
    @NonNull
    public OutputChannel<O> solderTo(@NonNull final String handlerName, @NonNull final Consumer<O> handler) {
        model.registerEdge(name, handlerName, "", false);
        forwardingDestinations.add(Objects.requireNonNull(handler));
        return this;
    }

    /**
     * Build a {@link WireFilter} that is soldered to the output of this wire.
     *
     * @param name      the name of the filter
     * @param predicate the predicate that filters the output of this wire
     * @return the filter
     */
    @NonNull
    public OutputChannel<O> buildFilter(@NonNull final String name, @NonNull final Predicate<O> predicate) {
        final WireFilter<O> filter =
                new WireFilter<>(model, Objects.requireNonNull(name), Objects.requireNonNull(predicate));
        solderTo(filter.getName(), filter);
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
        final WireListSplitter<E> splitter = new WireListSplitter<>(model, name + "_splitter");
        solderTo(splitter.getName(), (Consumer<O>) splitter);
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
     * @param name      the name of the transformer
     * @param transform the function that transforms the output of this wire into the output of the transformer
     * @param <T>       the output type of the transformer
     * @return the transformer
     */
    @NonNull
    public <T> OutputChannel<T> buildTransformer(@NonNull final String name, @NonNull Function<O, T> transform) {
        final WireTransformer<O, T> transformer =
                new WireTransformer<>(model, Objects.requireNonNull(name), Objects.requireNonNull(transform));
        solderTo(transformer.getName(), transformer);
        return transformer;
    }
}
