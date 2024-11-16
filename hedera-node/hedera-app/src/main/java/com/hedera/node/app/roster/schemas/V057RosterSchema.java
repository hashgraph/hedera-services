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

import static com.swirlds.state.lifecycle.MigrationContext.ROUND_NUMBER_KEY;
import static java.util.Objects.requireNonNull;

import com.hedera.hapi.node.base.SemanticVersion;
import com.hedera.hapi.node.state.Network;
import com.hedera.hapi.node.state.NodeMetadata;
import com.hedera.hapi.node.state.roster.Roster;
import com.hedera.node.app.version.ServicesSoftwareVersion;
import com.swirlds.platform.config.AddressBookConfig;
import com.swirlds.platform.state.service.WritableRosterStore;
import com.swirlds.state.lifecycle.MigrationContext;
import com.swirlds.state.lifecycle.Schema;
import edu.umd.cs.findbugs.annotations.NonNull;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * A restart-only schema that ensures state has a current roster if {@link AddressBookConfig#useRosterLifecycle()} is
 * set; the logic is currently simplified to use the genesis roster from the genesis {@link Network}s returned by
 * {@link MigrationContext#startupNetworks()}, and to promote any candidate roster in state to the current roster on
 * an upgrade.
 * <p>
 * The full logic will be done in <a href="https://github.com/hashgraph/hedera-services/issues/16552">this</a> issue.
 */
public class V057RosterSchema extends Schema {
    private static final Logger log = LogManager.getLogger(V057RosterSchema.class);

    private static final Long ZERO_ROUND = 0L;

    private static final SemanticVersion VERSION =
            SemanticVersion.newBuilder().major(0).minor(57).build();

    public V057RosterSchema() {
        super(VERSION);
    }

    @Override
    public void restart(@NonNull final MigrationContext ctx) {
        requireNonNull(ctx);
        if (ctx.configuration().getConfigData(AddressBookConfig.class).useRosterLifecycle()) {
            System.out.println("HERE");
            if (ctx.isGenesis() || isUpgrade(ctx)) {
                final var rosterStore = new WritableRosterStore(ctx.newStates());
                if (ctx.isGenesis()) {
                    final var network = ctx.startupNetworks().genesisNetworkOrThrow();
                    final var roster = new Roster(network.nodeMetadata().stream()
                            .map(NodeMetadata::rosterEntryOrThrow)
                            .toList());
                    rosterStore.putActiveRoster(requireNonNull(roster), 0L);
                } else {
                    rosterStore.adoptCandidateRoster(
                            (Long) ctx.sharedValues().getOrDefault(ROUND_NUMBER_KEY, ZERO_ROUND));
                }
            }
        }
    }

    private boolean isUpgrade(@NonNull final MigrationContext ctx) {
        return ServicesSoftwareVersion.from(ctx.configuration())
                        .compareTo(new ServicesSoftwareVersion(requireNonNull(ctx.previousVersion())))
                > 0;
    }
}
