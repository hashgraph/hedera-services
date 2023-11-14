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

import com.swirlds.common.wiring.WiringModel;
import com.swirlds.common.wiring.transformers.WireFilter;
import com.swirlds.common.wiring.transformers.WireListSplitter;
import com.swirlds.common.wiring.transformers.WireTransformer;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * Describes the output of a task scheduler. Can be soldered to wire inputs or lambdas.
 *
 * @param <OUT> the output type of the object
 */
public abstract class OutputWire<OUT> {

    private final WiringModel model;
    private final String name;

    /**
     * Constructor.
     *
     * @param model the wiring model containing this output wire
     * @param name  the name of the output wire
     */
    public OutputWire(@NonNull final WiringModel model, @NonNull final String name) {

        this.model = Objects.requireNonNull(model);
        this.name = Objects.requireNonNull(name);
    }

    /**
     * Get the name of this output wire. If this object is a task scheduler, this is the same as the name of the task
     * scheduler.
     *
     * @return the name
     */
    @NonNull
    public String getName() {
        return name;
    }

    /**
     * Specify an input wire where output data should be passed. This forwarding operation respects back pressure.
     * Equivalent to calling {@link #solderTo(InputWire, SolderType)} with {@link SolderType#PUT}.
     *
     * <p>
     * Soldering is the act of connecting two wires together, usually by melting a metal alloy between them. See
     * <a href="https://en.wikipedia.org/wiki/Soldering">wikipedia's entry on soldering</a>.
     *
     * <p>
     * Forwarding should be fully configured prior to data being inserted into the system. Adding forwarding
     * destinations after data has been inserted into the system is not thread safe and has undefined behavior.
     *
     * @param inputWire the input wire to forward output data to
     */
    public void solderTo(@NonNull final InputWire<OUT, ?> inputWire) {
        solderTo(inputWire, SolderType.PUT);
    }

    /**
     * Specify an input wire where output data should be passed. This forwarding operation respects back pressure.
     *
     * <p>
     * Soldering is the act of connecting two wires together, usually by melting a metal alloy between them. See
     * <a href="https://en.wikipedia.org/wiki/Soldering">wikipedia's entry on soldering</a>.
     *
     * <p>
     * Forwarding should be fully configured prior to data being inserted into the system. Adding forwarding
     * destinations after data has been inserted into the system is not thread safe and has undefined behavior.
     *
     * @param inputWire  the input wire to forward output data to
     * @param solderType the semantics of the soldering operation
     */
    public void solderTo(@NonNull final InputWire<OUT, ?> inputWire, @NonNull final SolderType solderType) {
        model.registerEdge(name, inputWire.getTaskSchedulerName(), inputWire.getName(), solderType);

        switch (solderType) {
            case PUT -> addForwardingDestination(inputWire::put);
            case INJECT -> addForwardingDestination(inputWire::inject);
            case OFFER -> addForwardingDestination(inputWire::offer);
            default -> throw new IllegalArgumentException("Unknown solder type: " + solderType);
        }
    }

    /**
     * Specify a consumer where output data should be forwarded.
     *
     * <p>
     * Soldering is the act of connecting two wires together, usually by melting a metal alloy between them. See
     * <a href="https://en.wikipedia.org/wiki/Soldering">wikipedia's entry on soldering</a>.
     *
     * <p>
     * Forwarding should be fully configured prior to data being inserted into the system. Adding forwarding
     * destinations after data has been inserted into the system is not thread safe and has undefined behavior.
     *
     * @param handlerName the name of the consumer
     * @param handler     the consumer to forward output data to
     */
    public void solderTo(@NonNull final String handlerName, @NonNull final Consumer<OUT> handler) {
        model.registerEdge(name, handlerName, "", SolderType.PUT);
        addForwardingDestination(Objects.requireNonNull(handler));
    }

    /**
     * Build a {@link WireFilter}. The input wire to the filter is automatically soldered to this output wire (i.e. all
     * data that comes out of the wire will be inserted into the filter). The output wire of the filter is returned by
     * this method.
     *
     * @param name      the name of the filter
     * @param predicate the predicate that filters the output of this wire
     * @return the output wire of the filter
     */
    @NonNull
    public OutputWire<OUT> buildFilter(@NonNull final String name, @NonNull final Predicate<OUT> predicate) {
        final WireFilter<OUT> filter =
                new WireFilter<>(model, Objects.requireNonNull(name), Objects.requireNonNull(predicate));
        solderTo(name, filter);
        return filter.getOutputWire();
    }

    /**
     * Build a {@link WireListSplitter}. Creating a splitter for wires without a list output type will cause runtime
     * exceptions. The input wire to the splitter is automatically soldered to this output wire (i.e. all data that
     * comes out of the wire will be inserted into the splitter). The output wire of the splitter is returned by this
     * method.
     *
     * @param <E> the type of the list elements
     * @return output wire of the splitter
     */
    @SuppressWarnings("unchecked")
    @NonNull
    public <E> OutputWire<E> buildSplitter() {
        final String splitterName = name + "_splitter";
        final WireListSplitter<E> splitter = new WireListSplitter<>(model, splitterName);
        solderTo(splitterName, (Consumer<OUT>) splitter);
        return splitter.getOutputWire();
    }

    /**
     * Build a {@link WireListSplitter} that is soldered to the output of this wire. Creating a splitter for wires
     * without a list output type will cause runtime exceptions. The input wire to the splitter is automatically
     * soldered to this output wire (i.e. all data that comes out of the wire will be inserted into the splitter). The
     * output wire of the splitter is returned by this method.
     *
     * @param clazz the class of the list elements, convince parameter for hinting generic type to the compiler
     * @param <T>   the type of the list elements
     */
    @NonNull
    public <T> OutputWire<T> buildSplitter(@NonNull final Class<T> clazz) {
        return buildSplitter();
    }

    /**
     * Build a {@link WireTransformer}. The input wire to the transformer is automatically soldered to this output wire
     * (i.e. all data that comes out of the wire will be inserted into the transformer). The output wire of the
     * transformer is returned by this method.
     *
     * @param name      the name of the transformer
     * @param transform the function that transforms the output of this wire into the output of the transformer
     * @param <T>       the output type of the transformer
     * @return the output wire of the transformer
     */
    @NonNull
    public <T> OutputWire<T> buildTransformer(@NonNull final String name, @NonNull final Function<OUT, T> transform) {
        final WireTransformer<OUT, T> transformer =
                new WireTransformer<>(model, Objects.requireNonNull(name), Objects.requireNonNull(transform));
        solderTo(name, transformer);
        return transformer.getOutputWire();
    }

    protected abstract void addForwardingDestination(@NonNull final Consumer<OUT> destination);
}
