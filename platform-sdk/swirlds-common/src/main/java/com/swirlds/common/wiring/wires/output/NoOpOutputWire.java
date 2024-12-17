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

package com.swirlds.common.wiring.wires.output;

import com.swirlds.common.wiring.model.TraceableWiringModel;
import com.swirlds.common.wiring.transformers.AdvancedTransformation;
import com.swirlds.common.wiring.wires.SolderType;
import com.swirlds.common.wiring.wires.input.InputWire;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * An output wire that doesn't actually do anything. When asked to solder to another wire, it does nothing. When asked
 * to build a transformer (or variations there upon) it produces no-op implementations.
 *
 * @param <OUT> the type of data passed to the forwarding method
 */
public class NoOpOutputWire<OUT> extends StandardOutputWire<OUT> {

    /**
     * Constructor.
     *
     * @param model the wiring model containing this output wire
     * @param name  the name of the output wire
     */
    public NoOpOutputWire(@NonNull final TraceableWiringModel model, @NonNull final String name) {
        super(model, name);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void addForwardingDestination(@NonNull final Consumer<OUT> destination) {
        // intentional no-op
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void forward(@NonNull final OUT data) {
        // intentional no-op
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void solderTo(@NonNull final InputWire<OUT> inputWire, @NonNull final SolderType solderType) {
        // intentional no-op
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void solderTo(
            @NonNull final String handlerName,
            @NonNull final String inputWireLabel,
            final @NonNull Consumer<OUT> handler) {
        // intentional no-op
    }

    /**
     * {@inheritDoc}
     */
    @NonNull
    @Override
    public OutputWire<OUT> buildFilter(
            @NonNull final String filterName,
            @NonNull final String filterInputName,
            @NonNull final Predicate<OUT> predicate) {
        return new NoOpOutputWire<>(getModel(), filterName);
    }

    /**
     * {@inheritDoc}
     */
    @NonNull
    @Override
    public <ELEMENT> OutputWire<ELEMENT> buildSplitter(
            @NonNull final String splitterName, @NonNull final String splitterInputName) {
        return new NoOpOutputWire<>(getModel(), splitterName);
    }

    /**
     * {@inheritDoc}
     */
    @NonNull
    @Override
    public <NEW_OUT> OutputWire<NEW_OUT> buildTransformer(
            @NonNull final String transformerName,
            @NonNull final String transformerInputName,
            @NonNull final Function<OUT, NEW_OUT> transformer) {
        return new NoOpOutputWire<>(getModel(), transformerName);
    }

    /**
     * {@inheritDoc}
     */
    @NonNull
    @Override
    public <NEW_OUT> OutputWire<NEW_OUT> buildAdvancedTransformer(
            @NonNull final AdvancedTransformation<OUT, NEW_OUT> transformer) {
        return new NoOpOutputWire<>(getModel(), transformer.getTransformerName());
    }
}
