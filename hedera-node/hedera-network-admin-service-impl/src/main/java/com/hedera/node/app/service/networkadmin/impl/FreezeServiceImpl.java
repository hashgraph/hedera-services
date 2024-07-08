/*
 * Copyright (C) 2020-2024 Hedera Hashgraph, LLC
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

package com.hedera.node.app.service.networkadmin.impl;

import com.hedera.node.app.service.networkadmin.FreezeService;
import com.hedera.node.app.service.networkadmin.ReadableFreezeStore;
import com.hedera.node.app.service.networkadmin.impl.schemas.V0490FreezeSchema;
import com.hedera.node.app.spi.RpcService;
import com.hedera.node.app.spi.store.ReadableStoreDefinition;
import com.hedera.node.app.spi.store.WritableStoreDefinition;
import com.swirlds.state.spi.SchemaRegistry;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Set;

/** Standard implementation of the {@link FreezeService} {@link RpcService}. */
public final class FreezeServiceImpl implements FreezeService {
    @Override
    public void registerSchemas(@NonNull final SchemaRegistry registry) {
        registry.register(new V0490FreezeSchema());
    }

    @Override
    public Set<ReadableStoreDefinition<?>> readableStoreDefinitions() {
        return Set.of(new ReadableStoreDefinition<>(ReadableFreezeStore.class, ReadableFreezeStoreImpl::new));
    }

    @Override
    public Set<WritableStoreDefinition<?>> writableStoreDefinitions() {
        return Set.of(new WritableStoreDefinition<>(
                WritableFreezeStore.class, (states, config, metrics) -> new WritableFreezeStore(states)));
    }
}
