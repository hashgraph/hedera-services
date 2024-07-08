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

package com.hedera.node.app.service.file.impl;

import com.hedera.node.app.service.file.FileService;
import com.hedera.node.app.service.file.ReadableFileStore;
import com.hedera.node.app.service.file.ReadableUpgradeFileStore;
import com.hedera.node.app.service.file.impl.schemas.V0490FileSchema;
import com.hedera.node.app.spi.RpcService;
import com.hedera.node.app.spi.store.ReadableStoreDefinition;
import com.hedera.node.app.spi.store.WritableStoreDefinition;
import com.swirlds.state.spi.SchemaRegistry;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Set;
import javax.inject.Inject;

/** Standard implementation of the {@link FileService} {@link RpcService}. */
public final class FileServiceImpl implements FileService {
    public static final long THREE_MONTHS_IN_SECONDS = 7776000L;
    public static final String DEFAULT_MEMO = "";

    /**
     * Constructs a {@link FileServiceImpl}.
     */
    @Inject
    public FileServiceImpl() {
        // No-op
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void registerSchemas(@NonNull final SchemaRegistry registry) {
        registry.register(new V0490FileSchema());
    }

    @Override
    public Set<ReadableStoreDefinition<?>> readableStoreDefinitions() {
        return Set.of(
                new ReadableStoreDefinition<>(ReadableFileStore.class, ReadableFileStoreImpl::new),
                new ReadableStoreDefinition<>(ReadableUpgradeFileStore.class, ReadableUpgradeFileStoreImpl::new));
    }

    @Override
    public Set<WritableStoreDefinition<?>> writableStoreDefinitions() {
        return Set.of(
                new WritableStoreDefinition<>(WritableFileStore.class, WritableFileStore::new),
                new WritableStoreDefinition<>(
                        WritableUpgradeFileStore.class,
                        (states, config, metrics) -> new WritableUpgradeFileStore(states)));
    }
}
