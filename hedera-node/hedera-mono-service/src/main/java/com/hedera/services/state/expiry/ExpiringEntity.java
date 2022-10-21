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
package com.hedera.services.state.expiry;

import com.hedera.services.state.submerkle.EntityId;
import java.util.function.Consumer;

public class ExpiringEntity {
    private final Consumer<EntityId> consumer;
    private final EntityId id;
    private final long expiry;

    public ExpiringEntity(EntityId id, Consumer<EntityId> consumer, long expiry) {
        this.consumer = consumer;
        this.id = id;
        this.expiry = expiry;
    }

    public long expiry() {
        return expiry;
    }

    public EntityId id() {
        return id;
    }

    public Consumer<EntityId> consumer() {
        return consumer;
    }
}
