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

import com.hedera.hapi.node.state.addressbook.Node;
import com.hedera.hapi.node.state.common.EntityNumber;
import com.hedera.node.app.service.addressbook.AddressBookService;
import com.hedera.node.internal.network.Network;
import com.hedera.node.internal.network.NodeMetadata;
import com.swirlds.platform.config.AddressBookConfig;
import com.swirlds.state.lifecycle.MigrationContext;
import com.swirlds.state.lifecycle.Schema;
import com.swirlds.state.spi.WritableKVState;
import com.swirlds.state.spi.WritableStates;
import edu.umd.cs.findbugs.annotations.NonNull;

/**
 * The {@link Schema#restart(MigrationContext)} implementation whereby the {@link AddressBookService} ensures that any
 * node metadata overrides in the startup assets are copied into the state.
 * <p>
 * <b>Important:</b> The latest {@link AddressBookService} schema should always implement this interface.
 */
public interface AddressBookTransplantSchema {
    default void restart(@NonNull final MigrationContext ctx) {
        requireNonNull(ctx);
        if (!ctx.appConfig().getConfigData(AddressBookConfig.class).useRosterLifecycle()) {
            return;
        }
        ctx.startupNetworks()
                .overrideNetworkFor(ctx.roundNumber())
                .ifPresent(network -> setNodeMetadata(network, ctx.newStates()));
    }

    /**
     * Set the node metadata in the state from the provided network, for whatever nodes they are available.
     * @param network the network from which to extract the node metadata
     * @param writableStates the state in which to store the node metadata
     */
    default void setNodeMetadata(@NonNull final Network network, @NonNull final WritableStates writableStates) {
        final WritableKVState<EntityNumber, Node> nodes = writableStates.get(NODES_KEY);
        network.nodeMetadata().stream()
                .filter(NodeMetadata::hasNode)
                .map(NodeMetadata::nodeOrThrow)
                .forEach(node -> nodes.put(new EntityNumber(node.nodeId()), node));
    }
}
