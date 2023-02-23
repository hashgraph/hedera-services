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

package com.hedera.node.app.spi.service;

import com.hedera.node.app.spi.state.SchemaRegistry;
import com.hedera.node.app.spi.workflows.QueryHandler;
import com.hedera.node.app.spi.workflows.TransactionHandler;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Set;

/**
 * A definition of an interface that will be implemented by each conceptual "service" like crypto-service, token-service
 * etc.,
 */
public interface Service {

    /**
     * Returns the name of the service. This name must be unique for each service deployed on the application.
     *
     * @return the name
     */
    @NonNull
    String getServiceName();

    /**
     * Returns the set of all transaction handlers that are provided by this service.
     *
     * @return set of all transaction handlers
     */
    @NonNull
    default Set<TransactionHandler> getTransactionHandler() {
        return Set.of();
    }

    /**
     * Returns the set of all query handlers that are provided by this service.
     *
     * @return set of all query handlers
     */
    @NonNull
    default Set<QueryHandler> getQueryHandler() {
        return Set.of();
    }

    default void registerSchemas(@NonNull final SchemaRegistry registry) {
        // no-op
    }
}
