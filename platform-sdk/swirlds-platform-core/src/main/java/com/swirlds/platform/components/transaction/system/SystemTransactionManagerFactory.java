/*
 * Copyright (C) 2016-2023 Hedera Hashgraph, LLC
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

package com.swirlds.platform.components.transaction.system;

import com.swirlds.platform.components.transaction.system.internal.SystemTransactionManagerImpl;
import java.util.ArrayList;
import java.util.List;

/**
 * A factory object to produce a {@link SystemTransactionManager}
 */
public class SystemTransactionManagerFactory {

    /**
     * The handle methods that will be passed into the transaction manager at construction
     */
    private final List<TypedSystemTransactionHandler<?>> handleMethods = new ArrayList<>();

    /**
     * Adds to the handle methods that will be managed by the output object
     *
     * @param handleMethods a list of consumer methods
     * @return this factory object
     */
    public SystemTransactionManagerFactory addHandlers(final List<TypedSystemTransactionHandler<?>> handleMethods) {
        this.handleMethods.addAll(handleMethods);

        return this;
    }

    /**
     * Build the output object
     *
     * @return an implementation of {@link SystemTransactionManager}
     */
    public SystemTransactionManager build() {
        return new SystemTransactionManagerImpl(handleMethods);
    }
}
