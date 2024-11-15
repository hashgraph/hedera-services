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

import static com.hedera.node.app.service.addressbook.impl.schemas.V053AddressBookSchema.NODES_KEY;
import static com.swirlds.state.lifecycle.MigrationContext.ROUND_NUMBER_KEY;
import static java.util.Objects.requireNonNull;

import com.hedera.hapi.node.base.SemanticVersion;
import com.hedera.hapi.node.state.Network;
import com.hedera.hapi.node.state.NodeMetadata;
import com.hedera.hapi.node.state.addressbook.Node;
import com.hedera.hapi.node.state.common.EntityNumber;
import com.swirlds.state.lifecycle.MigrationContext;
import com.swirlds.state.lifecycle.Schema;
import com.swirlds.state.spi.WritableKVState;
import com.swirlds.state.spi.WritableStates;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * A restart-only schema that ensures address book state reflects any overrides in
 * {@link com.hedera.hapi.node.state.Network} files on disk at startup.
 */
public class V057AddressBookSchema extends Schema {
    private static final Logger log = LogManager.getLogger(V057AddressBookSchema.class);

    private static final Long ZERO_ROUND = 0L;

    private static final SemanticVersion VERSION =
            SemanticVersion.newBuilder().major(0).minor(57).patch(0).build();

    public V057AddressBookSchema() {
        super(VERSION);
    }

    @Override
    public void restart(@NonNull final MigrationContext ctx) {
        requireNonNull(ctx);
        final AtomicReference<Network> network = new AtomicReference<>();
        final var isGenesis = ctx.previousVersion() == null;
        if (isGenesis) {
            try {
                network.set(ctx.startupNetworks().genesisNetworkOrThrow());
            } catch (Exception e) {
                // Until the roster proposal is fully adopted, we don't fail hard here
                log.error("Unable to load genesis network metadata", e);
                return;
            }
        } else {
            ctx.startupNetworks()
                    .overrideNetworkFor((Long) ctx.sharedValues().getOrDefault(ROUND_NUMBER_KEY, ZERO_ROUND))
                    .ifPresent(network::set);
        }
        if (network.get() != null) {
            setNodeMetadata(network.get(), ctx.newStates());
        }
    }

    private void setNodeMetadata(@NonNull final Network network, @NonNull final WritableStates writableStates) {
        final WritableKVState<EntityNumber, Node> nodes = writableStates.get(NODES_KEY);
        network.nodeMetadata().stream()
                .filter(NodeMetadata::hasNode)
                .map(NodeMetadata::nodeOrThrow)
                .forEach(node -> nodes.put(new EntityNumber(node.nodeId()), node));
    }
}
