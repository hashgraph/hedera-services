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

package com.hedera.node.app.service.addressbook.impl.schemas;

import static java.util.Objects.requireNonNull;

import com.hedera.hapi.node.base.SemanticVersion;
import com.hedera.node.internal.network.Network;
import com.swirlds.platform.config.AddressBookConfig;
import com.swirlds.state.lifecycle.MigrationContext;
import com.swirlds.state.lifecycle.Schema;
import edu.umd.cs.findbugs.annotations.NonNull;

/**
 * A genesis-only schema that ensures address book state reflects the genesis {@link Network}s returned from
 * {@link MigrationContext#startupNetworks()} on disk at startup when using the roster lifecycle.
 */
public class V057AddressBookSchema extends Schema implements AddressBookTransplantSchema {
    private static final SemanticVersion VERSION =
            SemanticVersion.newBuilder().major(0).minor(57).build();

    public V057AddressBookSchema() {
        super(VERSION);
    }

    @Override
    public void migrate(@NonNull final MigrationContext ctx) {
        requireNonNull(ctx);
        if (!ctx.appConfig().getConfigData(AddressBookConfig.class).useRosterLifecycle()) {
            return;
        }
        if (ctx.isGenesis()) {
            setNodeMetadata(ctx.startupNetworks().genesisNetworkOrThrow(), ctx.newStates());
        }
    }

    @Override
    public void restart(@NonNull final MigrationContext ctx) {
        AddressBookTransplantSchema.super.restart(ctx);
    }
}
