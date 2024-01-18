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

package com.swirlds.common.wiring.transformers.internal;

import com.swirlds.common.wiring.model.internal.StandardWiringModel;
import com.swirlds.common.wiring.schedulers.builders.TaskSchedulerType;
import com.swirlds.common.wiring.transformers.WireTransformer;
import com.swirlds.common.wiring.wires.output.OutputWire;
import com.swirlds.common.wiring.wires.output.internal.ForwardingOutputWire;
import com.swirlds.common.wiring.wires.output.internal.TransformingOutputWire;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Similar to a {@link WireTransformer} but for more advanced use cases. Unlike a {@link WireTransformer}, the
 * transforming function is called once per output per data item, and a special method can be called after the data is
 * forwarded to all destinations.
 *
 * @param <A> the input type
 * @param <B> the output type
 */
public class AdvancedWireTransformer<A, B> implements Consumer<A> {

    private final ForwardingOutputWire<A, B> outputWire;

    /**
     * Constructor.
     *
     * @param model         the wiring model containing this output wire
     * @param name          the name of the output wire
     * @param transformer   the function to transform the data from the input type to the output type. Is called once
     *                      per output per data item. If this method returns null then the data is not forwarded.
     * @param inputCleanup  an optional method that is called on input data after the data is forwarded to all
     *                      destinations. The original data is passed to this method. Ignored if null.
     * @param outputCleanup an optional method that is called on output data if it is rejected by a destination. This is
     *                      possible if offer soldering is used and the destination declines to take the data.
     */
    public AdvancedWireTransformer(
            @NonNull final StandardWiringModel model,
            @NonNull final String name,
            @NonNull final Function<A, B> transformer,
            @Nullable final Consumer<A> inputCleanup,
            @Nullable final Consumer<B> outputCleanup) {

        model.registerVertex(name, TaskSchedulerType.DIRECT_STATELESS, true);
        outputWire = new TransformingOutputWire<>(model, name, transformer, inputCleanup, outputCleanup);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void accept(@NonNull final A a) {
        outputWire.forward(a);
    }

    /**
     * Get the output wire for this transformer.
     *
     * @return the output wire
     */
    @NonNull
    public OutputWire<B> getOutputWire() {
        return outputWire;
    }
}
