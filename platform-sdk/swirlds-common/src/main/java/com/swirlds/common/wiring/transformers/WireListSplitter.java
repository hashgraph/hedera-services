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
import java.util.List;
import java.util.function.Consumer;

/**
 * Transforms a list of items to a sequence of individual items. Expects that there will not be any null values in the
 * collection.
 */
public class WireListSplitter<T> implements Consumer<List<T>> {

    private final OutputWire<T> outputWire;

    /**
     * Constructor.
     *
     * @param model the wiring model containing this output wire
     * @param name  the name of the output channel
     */
    public WireListSplitter(@NonNull final WiringModel model, @NonNull final String name) {
        model.registerVertex(name, true);
        outputWire = new OutputWire<>(model, name);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void accept(@NonNull final List<T> list) {
        for (final T t : list) {
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
