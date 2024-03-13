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

package com.swirlds.common.wiring.wires.input;

import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * An object that can be bound to a handler.
 *
 * @param <IN>  the type of data that enters
 * @param <OUT> the type of data that is permitted to be passed out (non-null values are forwarded to the primary output
 *              wire)
 */
public interface Bindable<IN, OUT> {

    /**
     * Bind this object to a handler. For things that don't send data to the output wire.
     *
     * @param handler the handler to bind to this input wire
     * @throws IllegalStateException if a handler is already bound and this method is called a second time
     */
    void bindConsumer(@NonNull Consumer<IN> handler);

    /**
     * Do not use this method.
     * <p>
     * Calling bindConsumer() on a function that returns the output type is explicitly not supported. This method is a
     * "trap" to catch situations where bindConsumer() is called when bind() should be called instead. Java is happy to
     * turn a Function into a Consumer if it can't match the types, and this is behavior we don't want to support.
     *
     * @param handler the wrong type of handler
     * @deprecated to show that this method should not be used. There are no plans to actually remove this method.
     */
    @Deprecated
    default void bindConsumer(@NonNull final Function<IN, OUT> handler) {
        throw new UnsupportedOperationException(
                "Do not call bindConsumer() with a function that returns a value. Call bind() instead.");
    }

    /**
     * Bind this object to a handler.
     *
     * @param handler the handler to bind to this input task scheduler, values returned are passed to the primary output
     *                wire of the associated scheduler.
     * @throws IllegalStateException if a handler is already bound and this method is called a second time
     */
    void bind(@NonNull final Function<IN, OUT> handler);
}
