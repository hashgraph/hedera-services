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

import com.swirlds.common.wiring.OutputWire;
import com.swirlds.common.wiring.WiringModel;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Transforms data on a wire from one type to another.
 *
 * @param <A> the input type
 * @param <B> the output type
 */
public class WireTransformer<A, B> implements Consumer<A> {

    private final Function<A, B> transformer;
    private final OutputWire<B> outputWire;

    /**
     * Constructor.
     *
     * @param model       the wiring model containing this output channel
     * @param name        the name of the output wire
     * @param transformer an object that transforms from type A to type B. If this method returns null then no data is
     *                    forwarded. This method must be very fast. Putting large amounts of work into this transformer
     *                    violates the intended usage pattern of the wiring framework and may result in very poor system
     *                    performance.
     */
    public WireTransformer(
            @NonNull final WiringModel model, @NonNull final String name, @NonNull final Function<A, B> transformer) {
        model.registerVertex(name, true);
        this.transformer = Objects.requireNonNull(transformer);
        outputWire = new OutputWire<>(model, name);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void accept(@NonNull final A a) {
        final B b = transformer.apply(a);
        if (b != null) {
            outputWire.forward(b);
        }
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
