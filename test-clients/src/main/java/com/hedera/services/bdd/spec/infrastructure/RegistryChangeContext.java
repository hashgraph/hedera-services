/*
 * Copyright (C) 2020-2023 Hedera Hashgraph, LLC
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
package com.hedera.services.bdd.spec.infrastructure;

import com.hedera.services.bdd.spec.HapiSpecOperation;
import java.util.Optional;

public class RegistryChangeContext<T> {
    private final T value;
    private final HapiSpecRegistry registry;
    private final Optional<HapiSpecOperation> cause;

    public RegistryChangeContext(
            T value, HapiSpecRegistry registry, Optional<HapiSpecOperation> cause) {
        this.value = value;
        this.registry = registry;
        this.cause = cause;
    }

    public T getValue() {
        return value;
    }

    public HapiSpecRegistry getRegistry() {
        return registry;
    }

    public Optional<HapiSpecOperation> getCause() {
        return cause;
    }
}
