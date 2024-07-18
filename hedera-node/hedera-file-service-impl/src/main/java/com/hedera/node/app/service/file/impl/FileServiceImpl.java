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
import com.hedera.node.app.service.file.impl.schemas.V0490FileSchema;
import com.hedera.node.app.spi.RpcService;
import com.hedera.node.app.spi.workflows.GenesisContext;
import com.swirlds.state.spi.SchemaRegistry;
import edu.umd.cs.findbugs.annotations.NonNull;
import javax.inject.Inject;

/** Standard implementation of the {@link FileService} {@link RpcService}. */
public final class FileServiceImpl implements FileService {
    public static final long THREE_MONTHS_IN_SECONDS = 7776000L;
    public static final String DEFAULT_MEMO = "";

    private final V0490FileSchema genesisSchema = new V0490FileSchema();

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
        registry.register(genesisSchema);
    }

    /**
     * Creates the system files in the given genesis context.
     *
     * @param context the genesis context
     */
    public void createSystemEntities(@NonNull final GenesisContext context) {
        genesisSchema.createGenesisAddressBookAndNodeDetails(context);
        genesisSchema.createGenesisExchangeRate(context);
        genesisSchema.createGenesisFeeSchedule(context);
        genesisSchema.createGenesisNetworkProperties(context);
        genesisSchema.createGenesisHapiPermissions(context);
        genesisSchema.createGenesisThrottleDefinitions(context);
        genesisSchema.createGenesisSoftwareUpdateFiles(context);
    }

    /**
     * Returns the genesis file schema.
     *
     * @return the genesis file schema
     */
    public V0490FileSchema genesisSchema() {
        return genesisSchema;
    }
}
