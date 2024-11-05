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

import com.hedera.node.app.service.addressbook.ReadableNodeStore;
import com.hedera.node.app.service.file.FileService;
import com.hedera.node.app.service.file.impl.schemas.V0490FileSchema;
import com.hedera.node.app.spi.RpcService;
import com.hedera.node.app.spi.workflows.SystemContext;
import com.swirlds.state.lifecycle.SchemaRegistry;
import edu.umd.cs.findbugs.annotations.NonNull;
import javax.inject.Inject;

/** Standard implementation of the {@link FileService} {@link RpcService}. */
public final class FileServiceImpl implements FileService {
    public static final long THREE_MONTHS_IN_SECONDS = 7776000L;
    public static final String DEFAULT_MEMO = "";

    private final V0490FileSchema fileSchema = new V0490FileSchema();

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
        registry.register(fileSchema);
    }

    /**
     * Creates the system files in the given genesis context.
     *
     * @param context the genesis context
     */
    public void createSystemEntities(@NonNull final SystemContext context) {
        fileSchema.createGenesisAddressBookAndNodeDetails(context);
        fileSchema.createGenesisFeeSchedule(context);
        fileSchema.createGenesisExchangeRate(context);
        fileSchema.createGenesisNetworkProperties(context);
        fileSchema.createGenesisHapiPermissions(context);
        fileSchema.createGenesisThrottleDefinitions(context);
        fileSchema.createGenesisSoftwareUpdateFiles(context);
    }

    /**
     * Returns the genesis file schema.
     *
     * @return the genesis file schema
     */
    public V0490FileSchema fileSchema() {
        return fileSchema;
    }

    /**
     * Update the 101, 102 files with the nodeStore data.
     *
     * @param context the genesis context
     * @param nodeStore the ReadableNodeStore
     */
    public void updateAddressBookAndNodeDetailsAfterFreeze(
            @NonNull final SystemContext context, @NonNull final ReadableNodeStore nodeStore) {
        fileSchema.updateAddressBookAndNodeDetailsAfterFreeze(context, nodeStore);
    }
}
