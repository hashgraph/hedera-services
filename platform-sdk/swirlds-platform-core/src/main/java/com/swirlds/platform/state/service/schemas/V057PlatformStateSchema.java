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

package com.swirlds.platform.state.service.schemas;

import static java.util.Objects.requireNonNull;

import com.hedera.hapi.node.base.SemanticVersion;
import com.hedera.hapi.node.state.NodeMetadata;
import com.hedera.hapi.node.state.roster.Roster;
import com.swirlds.platform.config.AddressBookConfig;
import com.swirlds.platform.config.BasicConfig;
import com.swirlds.platform.roster.RosterUtils;
import com.swirlds.platform.state.service.WritablePlatformStateStore;
import com.swirlds.state.lifecycle.MigrationContext;
import com.swirlds.state.lifecycle.Schema;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.time.Instant;
import java.util.function.Supplier;

/**
 * Restart-only schema that ensures the platform state at startup
 * reflects the active roster.
 */
public class V057PlatformStateSchema extends Schema {
    private static final SemanticVersion VERSION =
            SemanticVersion.newBuilder().major(0).minor(57).build();

    /**
     * A supplier for the active roster.
     */
    private final Supplier<Roster> activeRoster;

    public V057PlatformStateSchema(@NonNull final Supplier<Roster> activeRoster) {
        super(VERSION);
        this.activeRoster = requireNonNull(activeRoster);
    }

    @Override
    public void restart(@NonNull final MigrationContext ctx) {
        requireNonNull(ctx);
        if (!ctx.configuration().getConfigData(AddressBookConfig.class).useRosterLifecycle()) {
            return;
        }

        final var platformStateStore = new WritablePlatformStateStore(ctx.newStates());
        final var startupNetworks = ctx.startupNetworks();
        if (ctx.isGenesis()) {
            final var genesisNetwork = startupNetworks.genesisNetworkOrThrow();
            final var roster = new Roster(genesisNetwork.nodeMetadata().stream()
                    .map(NodeMetadata::rosterEntryOrThrow)
                    .toList());
            final var addressBook = RosterUtils.buildAddressBook(roster);
            platformStateStore.bulkUpdate(v -> {
                v.setAddressBook(addressBook);
                // v.setCreationSoftwareVersion(appVersion); // TODO: pass app version
                v.setRound(0);
                v.setLegacyRunningEventHash(null);
                v.setConsensusTimestamp(Instant.ofEpochSecond(0L));

                final BasicConfig basicConfig = ctx.configuration().getConfigData(BasicConfig.class);

                final long genesisFreezeTime = basicConfig.genesisFreezeTime();
                if (genesisFreezeTime > 0) {
                    v.setFreezeTime(Instant.ofEpochSecond(genesisFreezeTime));
                }
            });
        } else {
            if (isUpgrade(ctx)) {
                final var candidateAddressBook = RosterUtils.buildAddressBook(activeRoster.get());
                final var previousAddressBook = platformStateStore.getAddressBook();
                platformStateStore.bulkUpdate(v -> {
                    v.setAddressBook(candidateAddressBook.copy());
                    v.setPreviousAddressBook(previousAddressBook == null ? null : previousAddressBook.copy());
                });
            }
        }
    }

    // TODO: Implement
    private boolean isUpgrade(@NonNull final MigrationContext ctx) {
        return false;
    }
}
