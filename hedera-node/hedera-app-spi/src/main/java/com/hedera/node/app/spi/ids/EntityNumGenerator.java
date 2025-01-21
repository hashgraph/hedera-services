/*
 * Copyright (C) 2024-2025 Hedera Hashgraph, LLC
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

package com.hedera.node.app.spi.ids;

import com.hedera.node.app.spi.validation.EntityType;

/**
 * Provides a way to generate entity numbers.
 */
public interface EntityNumGenerator {

    /**
     * Consumes and returns the next entity number, for use by handlers that create entities.
     *
     * <p>If this method is called after a child transaction was dispatched, which is subsequently rolled back,
     * the counter will be rolled back, too. Consequently, the provided number must not be used anymore in this case,
     * because it will be reused.
     *
     * @param entityType the type of entity for which to generate a number
     * @return the next entity number
     */
    long newEntityNum(EntityType entityType);

    /**
     * Peeks at the next entity number, for use by handlers that create entities.
     *
     * <p>If this method is called after a child transaction was dispatched, which is subsequently rolled back,
     * the counter will be rolled back, too. Consequently, the provided number must not be used anymore in this case,
     * because it will be reused.
     *
     * @return the next entity number
     */
    long peekAtNewEntityNum();

    /**
     * Decrements the entity type counter for the given entity type.
     * Since entity counters are used to determine the size of the state, when an entity is removed,
     * the counter must be decremented.
     * This method is called when a remove operation is performed on a store.
     *
     * @param entityType the type of entity for which to decrement the number
     */
    void decrementEntityTypeCounter(EntityType entityType);
}
