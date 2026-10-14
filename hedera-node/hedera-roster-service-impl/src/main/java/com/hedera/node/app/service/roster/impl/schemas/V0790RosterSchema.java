// SPDX-License-Identifier: Apache-2.0
package com.hedera.node.app.service.roster.impl.schemas;

import static com.hedera.hapi.util.HapiUtils.SEMANTIC_VERSION_COMPARATOR;
import static java.util.Objects.requireNonNull;

import com.hedera.hapi.node.base.SemanticVersion;
import com.hedera.node.config.data.HederaConfig;
import com.hedera.node.config.data.VersionConfig;
import com.swirlds.state.lifecycle.MigrationContext;
import com.swirlds.state.lifecycle.Schema;
import com.swirlds.state.spi.WritableStates;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.function.Function;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.hiero.base.crypto.Cryptography;
import org.hiero.consensus.roster.WritableRosterStore;

/**
 * Re-keys the stored rosters under the platform's default digest at an upgrade boundary. Roster hashes only
 * change at an upgrade, on every node.
 */
public class V0790RosterSchema extends Schema<SemanticVersion> {
    private static final Logger log = LogManager.getLogger(V0790RosterSchema.class);
    private static final SemanticVersion VERSION =
            SemanticVersion.newBuilder().major(0).minor(79).patch(0).build();

    /**
     * The factory to use to create the writable roster store.
     */
    private final Function<WritableStates, WritableRosterStore> rosterStoreFactory;

    public V0790RosterSchema(@NonNull final Function<WritableStates, WritableRosterStore> rosterStoreFactory) {
        super(VERSION, SEMANTIC_VERSION_COMPARATOR);
        this.rosterStoreFactory = requireNonNull(rosterStoreFactory);
    }

    @Override
    public void restart(@NonNull final MigrationContext<SemanticVersion> ctx) {
        requireNonNull(ctx);
        if (ctx.isGenesis()) {
            return;
        }
        if (ctx.isUpgrade(ctx.appConfig()
                .getConfigData(VersionConfig.class)
                .servicesVersion()
                .copyBuilder()
                .build("" + ctx.appConfig().getConfigData(HederaConfig.class).configVersion())
                .build())) {
            final var rosterStore = rosterStoreFactory.apply(ctx.newStates());
            log.info("Rehashing rosters with default digest {}", Cryptography.DEFAULT_DIGEST_TYPE);
            rosterStore.rehashRosters(Cryptography.DEFAULT_DIGEST_TYPE);
        }
    }
}
