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
    default void restart(
            @NonNull final MigrationContext ctx,
            @NonNull final Function<WritableStates, WritableRosterStore> rosterStoreFactory) {
        requireNonNull(ctx);
        if (ctx.appConfig().getConfigData(AddressBookConfig.class).useRosterLifecycle()) {
            final long roundNumber = ctx.roundNumber();
            final var startupNetworks = ctx.startupNetworks();
            startupNetworks.overrideNetworkFor(roundNumber).ifPresent(network -> {
                final long activeRoundNumber = roundNumber + 1;
                log.info("Adopting roster from override network in round {}", activeRoundNumber);
                final var rosterStore = rosterStoreFactory.apply(ctx.newStates());
                rosterStore.putActiveRoster(RosterUtils.rosterFrom(network), activeRoundNumber);
                startupNetworks.setOverrideRound(roundNumber);
            });
        }
    }
}
