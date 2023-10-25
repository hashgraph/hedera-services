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

import edu.umd.cs.findbugs.annotations.NonNull;

/**
 * Each {@link TaskScheduler} has a primary output wire. When a component method bound to an intake wire returns data,
 * that data is passed to the primary output wire of the task scheduler. This object provides a way for passing
 * additional data out of a component over additional output wires.
 *
 * @param <T> the type of data that is transmitted over this output wire
 */
public class SecondaryOutputWire<T> extends OutputWire<T> {

    /**
     * Constructor.
     *
     * @param model               the wiring model containing this output wire
     * @param name                the name of the parent wire
     * @param insertionIsBlocking when data is inserted into this wire, will it block until capacity is available?
     */
    protected SecondaryOutputWire(@NonNull WiringModel model, @NonNull String name, boolean insertionIsBlocking) {
        super(model, name, false, insertionIsBlocking);
    }

    /**
     * Call this to push data out over this output wire.
     *
     * <p>
     * It is a violation of convention to invoke this method from anywhere other than within the component being
     * executed on the parent wire. Don't do it.
     *
     * @param data the output data to forward
     */
    @Override
    public void forward(@NonNull final T data) {
        super.forward(data);
    }
}
