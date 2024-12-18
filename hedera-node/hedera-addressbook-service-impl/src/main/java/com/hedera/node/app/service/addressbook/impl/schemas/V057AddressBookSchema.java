// SPDX-License-Identifier: Apache-2.0
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
            setNodeMetadata(ctx.startupNetworks().genesisNetworkOrThrow(ctx.platformConfig()), ctx.newStates());
        }
    }

    @Override
    public void restart(@NonNull final MigrationContext ctx) {
        AddressBookTransplantSchema.super.restart(ctx);
    }
}
