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
import static java.util.Objects.requireNonNull;

import com.hedera.hapi.node.base.SemanticVersion;
import com.hedera.hapi.node.state.addressbook.Node;
import com.hedera.hapi.node.state.common.EntityNumber;
import com.hedera.node.internal.network.Network;
import com.hedera.node.internal.network.NodeMetadata;
import com.swirlds.state.lifecycle.MigrationContext;
import com.swirlds.state.lifecycle.Schema;
import com.swirlds.state.spi.WritableKVState;
import com.swirlds.state.spi.WritableStates;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.concurrent.atomic.AtomicReference;

/**
 * A restart-only schema that ensures address book state reflects any overrides in
 * {@link Network}s returned from {@link MigrationContext#startupNetworks()} on
 * disk at startup. (Note that once the network has written at least one state to
 * disk after restart, all such override {@link Network}s will be archived, and
 * hence are applied to at most one state after restart.)
 */
public class V057AddressBookSchema extends Schema {
    private static final SemanticVersion VERSION =
            SemanticVersion.newBuilder().major(0).minor(57).build();

    public V057AddressBookSchema() {
        super(VERSION);
    }

    @Override
    public void restart(@NonNull final MigrationContext ctx) {
        requireNonNull(ctx);
        final AtomicReference<Network> network = new AtomicReference<>();
        if (ctx.isGenesis()) {
            try {
                network.set(ctx.startupNetworks().genesisNetworkOrThrow());
            } catch (Exception ignore) {
                // FUTURE - fail hard here once the roster lifecycle is always enabled
                return;
            }
        } else {
            ctx.startupNetworks().overrideNetworkFor(ctx.roundNumber()).ifPresent(network::set);
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
