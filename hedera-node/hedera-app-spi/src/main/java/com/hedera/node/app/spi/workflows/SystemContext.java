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

package com.hedera.node.app.spi.workflows;

import com.hedera.hapi.node.transaction.TransactionBody;
import com.swirlds.config.api.Configuration;
import com.swirlds.state.lifecycle.info.NetworkInfo;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.time.Instant;

/**
 * Lets a service do genesis entity creations that must be legible in the block stream as specific HAPI
 * transactions for mirror node importers that were designed for the semantics of the original record
 * stream.
 */
public interface SystemContext {
    /**
     * Dispatches a transaction to the appropriate service using the requested next entity number, which
     * must be less than the first user entity number.
     *
     * @param txBody the transaction body
     * @param entityNum the entity number
     * @throws IllegalArgumentException if the entity number is not less than the first user entity number
     */
    void dispatchCreation(@NonNull TransactionBody txBody, long entityNum);

    /**
     * Dispatches a transaction to the appropriate service
     *
     * @param txBody the transaction body
     * @throws IllegalArgumentException if the entity number is not less than the first user entity number
     */
    void dispatchUpdate(@NonNull TransactionBody txBody);

    /**
     * The {@link Configuration} at genesis.
     *
     * @return The configuration to use.
     */
    @NonNull
    Configuration configuration();

    /**
     * The {@link NetworkInfo} at genesis.
     *
     * @return The network info to use.
     */
    @NonNull
    NetworkInfo networkInfo();

    /**
     * The consensus {@link Instant} of the genesis transaction.
     *
     * @return The genesis instant.
     */
    @NonNull
    Instant now();
}
