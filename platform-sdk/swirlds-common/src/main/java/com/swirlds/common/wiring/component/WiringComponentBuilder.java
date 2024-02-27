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

package com.swirlds.common.wiring.component;

import com.swirlds.common.wiring.model.WiringModel;
import com.swirlds.common.wiring.wires.input.InputWire;
import com.swirlds.common.wiring.wires.output.OutputWire;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;

public class WiringComponentBuilder<COMPONENT_TYPE extends WiringComponent<OUTPUT_TYPE>, OUTPUT_TYPE> {

    public WiringComponentBuilder(
            @NonNull final WiringModel model,
            @NonNull final Class<COMPONENT_TYPE> clazz,
            @NonNull final WiringComponentConfiguration configuration) {}

    @NonNull
    public OutputWire<OUTPUT_TYPE> getOutputWire() {
        return null; // TODO
    }

    public <INPUT_TYPE> InputWire<INPUT_TYPE> getInputWire(
            @NonNull final BiFunction<COMPONENT_TYPE, INPUT_TYPE, OUTPUT_TYPE> handlerDescription) {
        return null; // TODO
    }

    public <INPUT_TYPE> InputWire<INPUT_TYPE> getInputWire(
            @NonNull final BiConsumer<COMPONENT_TYPE, INPUT_TYPE> handlerDescription) {
        return null; // TODO
    }

    public void flush() {
        // TODO
    }

    public void squelch(final boolean squelch) {
        // TODO
    }

    public void bind(@NonNull final COMPONENT_TYPE component) {
        // TODO
    }
}
