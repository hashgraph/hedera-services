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
import java.util.function.Predicate;

/**
 * Filters out data, allowing some objects to pass and blocking others.
 */
public class WireFilter<T> implements Consumer<T> {

    private final Predicate<T> predicate;
    private final OutputWire<T> outputWire;

    /**
     * Constructor.
     *
     * @param model     the wiring model containing this output channel
     * @param name      the name of the output wire
     * @param predicate only data that causes this method to return true is forwarded. This method must be very fast.
     *                  Putting large amounts of work into this transformer violates the intended usage pattern of the
     *                  wiring framework and may result in very poor system performance.
     */
    public WireFilter(
            @NonNull final WiringModel model, @NonNull final String name, @NonNull final Predicate<T> predicate) {
        this.predicate = Objects.requireNonNull(predicate);
        this.outputWire = new OutputWire<>(model, name);
        model.registerVertex(name, true);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void accept(@NonNull final T t) {
        if (predicate.test(t)) {
            outputWire.forward(t);
        }
    }

    /**
     * Get the output wire for this transformer.
     *
     * @return the output wire
     */
    @NonNull
    public OutputWire<T> getOutputWire() {
        return outputWire;
    }
}
