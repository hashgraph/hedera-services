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

package com.swirlds.state.lifecycle;

import edu.umd.cs.findbugs.annotations.NonNull;

/**
 * A definition of an interface that will be implemented by each conceptual "service" like
 * crypto-service, token-service etc.,
 */
public interface Service {

    /**
     * A sort value for the service, used to determine the order in which service
     * schemas are migrated.
     *
     * <p><b>(FUTURE)</b> This order should actually depend on the migration
     * software version, because nothing prevents service {@code A} from needing
     * to precede service {@code B} in version {@code N}; while at the same time
     * {@code B} needing to precede {@code A} in version {@code N+1}. But this
     * will require a significant restructuring in {@code hedera-app} and does
     * not provide any current value, so we defer that work.
     *
     * @return the migrationOrder value
     */
    default int migrationOrder() {
        return 0;
    }

    /**
     * Returns the name of the application state. This name must be unique for each state type deployed on the
     * application.
     *
     * @return the name
     */
    @NonNull
    String getServiceName();

    /**
     * Registers the schemas this application state really uses with the given {@link SchemaRegistry}.
     *
     * @param registry the registry to register the schemas with
     */
    void registerSchemas(@NonNull SchemaRegistry registry);
}
