// SPDX-License-Identifier: Apache-2.0
package com.hedera.node.app.roster.schemas;

import static java.util.Objects.requireNonNull;

import com.hedera.node.app.roster.RosterService;
import com.swirlds.platform.config.AddressBookConfig;
import com.swirlds.platform.roster.RosterUtils;
import com.swirlds.platform.state.service.WritableRosterStore;
import com.swirlds.state.lifecycle.MigrationContext;
import com.swirlds.state.lifecycle.Schema;
import com.swirlds.state.spi.WritableStates;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.function.Function;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * The {@link Schema#restart(MigrationContext)} implementation whereby the {@link RosterService} ensures that any
 * roster overrides in the startup assets are copied into the state.
 * <p>
 * <b>Important:</b> The latest {@link RosterService} schema should always implement this interface.
 */
public interface RosterTransplantSchema {
    Logger log = LogManager.getLogger(RosterTransplantSchema.class);

    /**
     * Restart the {@link RosterService} by copying any roster overrides from the startup assets into the state.
     * @param ctx the migration context
     * @param rosterStoreFactory the factory to use to create the writable roster store
     */
    default boolean restart(
            @NonNull final MigrationContext ctx,
            @NonNull final Function<WritableStates, WritableRosterStore> rosterStoreFactory) {
        requireNonNull(ctx);
        if (ctx.appConfig().getConfigData(AddressBookConfig.class).useRosterLifecycle()) {
            final long roundNumber = ctx.roundNumber();
            final var startupNetworks = ctx.startupNetworks();
            final var overrideNetwork = startupNetworks.overrideNetworkFor(roundNumber);
            overrideNetwork.ifPresent(network -> {
                final long activeRoundNumber = roundNumber + 1;
                log.info("Adopting roster from override network in round {}", activeRoundNumber);
                final var rosterStore = rosterStoreFactory.apply(ctx.newStates());
                final var roster = RosterUtils.rosterFrom(network);
                rosterStore.putActiveRoster(roster, activeRoundNumber);
                startupNetworks.setOverrideRound(roundNumber);
            });
            return overrideNetwork.isPresent();
        }
        return false;
    }
}
