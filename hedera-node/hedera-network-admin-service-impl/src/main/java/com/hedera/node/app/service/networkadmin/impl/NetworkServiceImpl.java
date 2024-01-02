/*
 * Copyright (C) 2023-2024 Hedera Hashgraph, LLC
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

import com.hedera.hapi.node.base.SemanticVersion;
import com.hedera.node.app.service.networkadmin.NetworkService;
import com.hedera.node.app.spi.state.MigrationContext;
import com.hedera.node.app.spi.state.Schema;
import com.hedera.node.app.spi.state.SchemaRegistry;
import com.hedera.node.app.spi.state.StateDefinition;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Set;

/**
 * Standard implementation of the {@link NetworkService} {@link com.hedera.node.app.spi.Service}.
 */
public final class NetworkServiceImpl implements NetworkService {

    @Override
    public void registerSchemas(final @NonNull SchemaRegistry registry, final SemanticVersion version) {
        registry.register(networkSchema(version));
    }

    private Schema networkSchema(final SemanticVersion version) {
        return new Schema(version) {
            @NonNull
            @Override
            public Set<StateDefinition> statesToCreate() {
                return Set.of();
            }

            @Override
            public void migrate(@NonNull MigrationContext ctx) {}
        };
    }
}
