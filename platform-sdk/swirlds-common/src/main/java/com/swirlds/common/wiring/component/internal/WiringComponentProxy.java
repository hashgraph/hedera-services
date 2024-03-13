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

package com.swirlds.common.wiring.component.internal;

import edu.umd.cs.findbugs.annotations.NonNull;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.util.Objects;

/**
 * This dynamic proxy is used by the {@link com.swirlds.common.wiring.component.ComponentWiring} to capture the most
 * recently invoked method.
 */
public class WiringComponentProxy implements InvocationHandler {

    private Method mostRecentlyInvokedMethod = null;

    /**
     * {@inheritDoc}
     */
    @Override
    public Object invoke(@NonNull final Object proxy, @NonNull final Method method, @NonNull final Object[] args)
            throws Throwable {
        mostRecentlyInvokedMethod = Objects.requireNonNull(method);
        return null;
    }

    /**
     * Get the most recently invoked method. Calling this method resets the most recently invoked method to null
     * as a safety measure.
     *
     * @return the most recently invoked method
     */
    @NonNull
    public Method getMostRecentlyInvokedMethod() {
        if (mostRecentlyInvokedMethod == null) {
            throw new IllegalArgumentException("Provided lambda is not a method on the component interface.");
        }
        try {
            return mostRecentlyInvokedMethod;
        } finally {
            mostRecentlyInvokedMethod = null;
        }
    }
}
