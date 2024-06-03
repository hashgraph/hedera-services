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

import com.hedera.hapi.node.base.SemanticVersion;
import com.hedera.node.app.service.file.FileService;
import com.hedera.node.app.service.file.impl.schemas.InitialModFileGenesisSchema;
import com.hedera.node.app.service.mono.state.adapters.VirtualMapLike;
import com.hedera.node.app.service.mono.state.virtual.VirtualBlobKey;
import com.hedera.node.app.service.mono.state.virtual.VirtualBlobValue;
import com.hedera.node.app.spi.Service;
import com.hedera.node.app.spi.state.SchemaRegistry;
import com.hedera.node.config.ConfigProvider;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.util.function.Supplier;
import javax.inject.Inject;

/** Standard implementation of the {@link FileService} {@link Service}. */
public final class FileServiceImpl implements FileService {
    public static final String BLOBS_KEY = "FILES";
    public static final String UPGRADE_FILE_KEY = "UPGRADE_FILE";
    public static final String UPGRADE_DATA_KEY = "UPGRADE_DATA[%s]";
    private final ConfigProvider configProvider;
    private InitialModFileGenesisSchema initialFileSchema;

    /**
     * Constructs a {@link FileServiceImpl} with the given {@link ConfigProvider}.
     * @param configProvider the configuration provider
     */
    @Inject
    public FileServiceImpl(@NonNull final ConfigProvider configProvider) {
        this.configProvider = configProvider;
    }

    /**
     * Sets the {@link Supplier} for the {@link VirtualMapLike} that will be used to store file data.
     * @param fss the file data storage supplier
     */
    public void setFs(@Nullable final Supplier<VirtualMapLike<VirtualBlobKey, VirtualBlobValue>> fss) {
        initialFileSchema.setFs(fss);
    }

    @Override
    public void registerSchemas(@NonNull final SchemaRegistry registry, @NonNull final SemanticVersion version) {
        initialFileSchema = new InitialModFileGenesisSchema(version, configProvider);
        registry.register(initialFileSchema);
    }
}
