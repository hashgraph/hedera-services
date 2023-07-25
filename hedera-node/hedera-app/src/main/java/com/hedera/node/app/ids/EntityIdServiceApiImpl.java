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

package com.hedera.node.app.ids;

import static java.util.Objects.requireNonNull;

import com.hedera.node.app.spi.ids.EntityIdServiceApi;
import com.hedera.node.app.spi.store.WritableStoreFactory;
import com.swirlds.config.api.Configuration;
import edu.umd.cs.findbugs.annotations.NonNull;

public class EntityIdServiceApiImpl implements EntityIdServiceApi {
    private final WritableStoreFactory storeFactory;

    public EntityIdServiceApiImpl(
            @NonNull final Configuration config, @NonNull final WritableStoreFactory storeFactory) {
        requireNonNull(config);
        this.storeFactory = requireNonNull(storeFactory);
    }

    @Override
    public long useNextNumber() {
        final var store = storeFactory.getStore(WritableEntityIdStore.class);
        return store.incrementAndGet();
    }

    @Override
    public long peekNextNumber() {
        final var store = storeFactory.getStore(WritableEntityIdStore.class);
        return store.peekAtNextNumber();
    }
}
