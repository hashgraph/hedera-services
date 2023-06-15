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

package com.swirlds.common.threading.utility;

import com.swirlds.common.threading.interrupt.InterruptableConsumer;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Invokes different handlers based on the type of the object.
 */
public class MultiHandler {
    /**
     * A map of data type to handler for that type.
     */
    private final Map<Class<?>, InterruptableConsumer<?>> subHandlers;

    public MultiHandler(final Map<Class<?>, InterruptableConsumer<?>> subHandlers) {
        this.subHandlers = new HashMap<>(subHandlers);
    }

    public boolean containsHandlerFor(final Class<?> clazz) {
        return subHandlers.containsKey(clazz);
    }

    /**
     * Handle an object from the queue.
     *
     * @param object
     * 		the object to be handled
     */
    @SuppressWarnings("unchecked")
    public void handle(final Object object) throws InterruptedException {
        Objects.requireNonNull(object, "null objects not supported");
        final Class<?> clazz = object.getClass();
        final InterruptableConsumer<Object> handler = (InterruptableConsumer<Object>) subHandlers.get(clazz);
        if (handler == null) {
            throw new IllegalStateException("no handler for " + clazz);
        }
        handler.accept(object);
    }
}
