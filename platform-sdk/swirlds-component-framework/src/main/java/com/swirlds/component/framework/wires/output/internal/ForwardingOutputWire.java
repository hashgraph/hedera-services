/*
 * Copyright (C) 2023-2025 Hedera Hashgraph, LLC
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

package com.swirlds.component.framework.wires.output.internal;

import com.swirlds.component.framework.model.TraceableWiringModel;
import com.swirlds.component.framework.wires.output.OutputWire;
import edu.umd.cs.findbugs.annotations.NonNull;

/**
 * An output wire that will take data and forward it to its outputs.
 *
 * @param <IN>  the type of data passed to the forwarding method
 * @param <OUT> the type of data forwarded to things soldered to this wire
 */
public abstract class ForwardingOutputWire<IN, OUT> extends OutputWire<OUT> {

    /**
     * Constructor.
     *
     * @param model the wiring model containing this output wire
     * @param name  the name of the output wire
     */
    protected ForwardingOutputWire(@NonNull final TraceableWiringModel model, final @NonNull String name) {
        super(model, name);
    }

    /**
     * Forward output data to any wires/consumers that are listening for it.
     * <p>
     * Although it will technically work, it is a violation of convention to directly put data into this output wire
     * except from within code being executed by the task scheduler that owns this output wire. Don't do it.
     *
     * @param data the output data to forward
     */
    public abstract void forward(@NonNull final IN data);
}
