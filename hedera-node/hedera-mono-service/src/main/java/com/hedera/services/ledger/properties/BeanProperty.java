/*
 * Copyright (C) 2020-2022 Hedera Hashgraph, LLC
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
package com.hedera.services.ledger.properties;

import java.util.function.BiConsumer;
import java.util.function.Function;

/**
 * Defines a type that can provide a getter/setter pair for a given type. (The getter/setter pair
 * should apply to a specific property of the target type, of course.)
 *
 * @param <A> the type which the getter/setter apply to.
 */
public interface BeanProperty<A> {
    /**
     * Gets the setter relevant to the property at hand.
     *
     * @return the setter on the target type.
     */
    BiConsumer<A, Object> setter();

    /**
     * Provides the matching getter for the property at hand.
     *
     * @return the getter on the target type.
     */
    Function<A, Object> getter();
}
