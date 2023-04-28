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

package com.hedera.node.app.spi.meta;

import com.hedera.node.app.spi.validation.AttributeValidator;
import com.hedera.node.app.spi.validation.ExpiryValidator;
import com.hedera.node.app.spi.workflows.TransactionHandler;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.time.Instant;
import java.util.function.LongSupplier;

/**
 * Bundles up the in-handle application context required by {@link TransactionHandler}
 * implementations.
 *
 * <p>At present, only supplies the context needed for Consensus Service handlers in the
 * limited form described by https://github.com/hashgraph/hedera-services/issues/4945.
 */
public interface HandleContext {
    /**
     * Returns the current consensus time.
     *
     * @return the current consensus time
     */
    Instant consensusNow();

    /**
     * Returns a supplier of the next entity number, for use by handlers that create entities.
     *
     * @return a supplier of the next entity number
     */
    LongSupplier newEntityNumSupplier();

    /**
     * Returns the validator for attributes of entities created or updated by handlers.
     *
     * @return the validator for attributes
     */
    AttributeValidator attributeValidator();

    /**
     * Returns the validator for expiry metadata (both explicit expiration times and
     * auto-renew configuration) of entities created or updated by handlers.
     *
     * @return the validator for expiry metadata
     */
    ExpiryValidator expiryValidator();

    /**
     * Create a new store given the store's interface. This gives read-only access to the store.
     *
     * @param storeInterface The store interface to find and create a store for
     * @return An implementation of store interface provided, or null if the store
     * @param <C> Interface class for a Store
     * @throws IllegalArgumentException if the storeInterface class provided is unknown to the app
     * @throws NullPointerException if {@code clazz} is {@code null}
     */
    @NonNull
    <C> C createStore(@NonNull Class<C> storeInterface);
}
