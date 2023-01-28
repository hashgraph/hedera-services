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
package com.hedera.node.app.service.file.impl;

import com.hedera.node.app.service.file.FileService;
import com.hedera.node.app.service.mono.state.virtual.VirtualBlobKey;
import com.hedera.node.app.service.mono.state.virtual.VirtualBlobKeySerializer;
import com.hedera.node.app.service.mono.state.virtual.VirtualBlobValue;
import com.hedera.node.app.spi.state.Schema;
import com.hedera.node.app.spi.state.SchemaRegistry;
import com.hedera.node.app.spi.state.StateDefinition;
import com.hedera.node.app.spi.state.serdes.MonoMapSerdesAdapter;
import com.hederahashgraph.api.proto.java.SemanticVersion;
import edu.umd.cs.findbugs.annotations.NonNull;

import java.util.Set;

/**
 * Standard implementation of the {@link FileService} {@link com.hedera.node.app.spi.Service}.
 */
public final class FileServiceImpl implements FileService {
    private static final int MAX_BLOBS = 50_000_000;
    private static final String BLOBS_KEY = "BLOBS";
    private static final SemanticVersion CURRENT_VERSION = SemanticVersion.newBuilder()
            .setMinor(34).build();

    @Override
    public void registerSchemas(@NonNull SchemaRegistry registry) {
        registry.register(fileServiceSchema());
    }

    private Schema fileServiceSchema() {
        return new Schema(CURRENT_VERSION) {
            @NonNull
            @Override
            public Set<StateDefinition> statesToCreate() {
                return Set.of(blobsDef());
            }
        };
    }

    private static StateDefinition<VirtualBlobKey, VirtualBlobValue> blobsDef() {
        final var keySerdes = MonoMapSerdesAdapter.serdesForVirtualKey(
                VirtualBlobKey.CURRENT_VERSION,
                VirtualBlobKey::new,
                new VirtualBlobKeySerializer());
        final var valueSerdes = MonoMapSerdesAdapter.serdesForVirtualValue(
                VirtualBlobValue.CURRENT_VERSION,
                VirtualBlobValue::new);

        return StateDefinition.onDisk(
                BLOBS_KEY, keySerdes, valueSerdes, MAX_BLOBS);
    }
}
