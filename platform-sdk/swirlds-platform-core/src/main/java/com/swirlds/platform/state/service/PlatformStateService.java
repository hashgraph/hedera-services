/*
 * Copyright (C) 2024 Hedera Hashgraph, LLC
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

package com.swirlds.platform.state.service;

import static com.swirlds.platform.state.service.schemas.V0540PlatformStateSchema.PLATFORM_STATE_KEY;
import static java.util.Objects.requireNonNull;

import com.hedera.hapi.node.base.SemanticVersion;
import com.hedera.hapi.platform.state.PlatformState;
import com.swirlds.platform.state.MerkleStateRoot;
import com.swirlds.platform.state.service.schemas.V0540PlatformStateSchema;
import com.swirlds.state.lifecycle.Schema;
import com.swirlds.state.lifecycle.SchemaRegistry;
import com.swirlds.state.lifecycle.Service;
import com.swirlds.state.merkle.singleton.SingletonNode;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Collection;
import java.util.List;

/**
 * A service that provides the schema for the platform state, used by {@link MerkleStateRoot}
 * to implement accessors to the platform state.
 */
public enum PlatformStateService implements Service {
    PLATFORM_STATE_SERVICE;

    private static final Collection<Schema> SCHEMAS = List.of(new V0540PlatformStateSchema());

    public static final String NAME = "PlatformStateService";

    @NonNull
    @Override
    public String getServiceName() {
        return NAME;
    }

    @Override
    public void registerSchemas(@NonNull final SchemaRegistry registry) {
        requireNonNull(registry);
        SCHEMAS.forEach(registry::register);
    }

    /**
     * Given a {@link MerkleStateRoot}, returns the creation version of the platform state if it exists.
     * @param root the root to extract the creation version from
     * @return the creation version of the platform state, or null if the state is a genesis state
     */
    @SuppressWarnings("unchecked")
    public SemanticVersion creationVersionOf(@NonNull final MerkleStateRoot root) {
        requireNonNull(root);
        if (root.getNumberOfChildren() == 0) {
            return null;
        }
        final var index = root.findNodeIndex(NAME, PLATFORM_STATE_KEY);
        if (index == -1) {
            throw new IllegalStateException("Platform state not found in root");
        }
        return ((SingletonNode<PlatformState>) root.getChild(index)).getValue().creationSoftwareVersionOrThrow();
    }
}
