/*
 * Copyright (C) 2025 Hedera Hashgraph, LLC
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
import com.hedera.hapi.node.state.addressbook.Node;
import com.hedera.hapi.node.state.common.EntityNumber;
import com.hedera.hapi.node.state.token.StakingNodeInfo;
import com.swirlds.state.lifecycle.MigrationContext;
import com.swirlds.state.lifecycle.Schema;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.SortedMap;
import java.util.function.Supplier;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * A schema that ensures any token service mappings with
 * {@link com.hedera.hapi.node.state.token.StakingNodeInfo#deleted()} have an equivalent deleted
 * {@link com.hedera.hapi.node.state.addressbook.Node} in the address book service state.
 * <p>
 * This allows us to behave as if the DAB invariant holds; that is,
 * <ul>
 *     <Li>Information about network topology is a one-way information flow, <b>from</b>the DAB transactions
 *     handled in the {@link com.hedera.node.app.service.addressbook.AddressBookService}, <b>to</b> all
 *     other services' states.</Li>
 * </ul>
 */
public class V058StakingInfoReconciliationSchema extends Schema implements AddressBookTransplantSchema {
    private static final Logger log = LogManager.getLogger(V058StakingInfoReconciliationSchema.class);

    private static final SemanticVersion VERSION =
            SemanticVersion.newBuilder().major(0).minor(58).build();

    private final Supplier<SortedMap<Long, StakingNodeInfo>> stakingNodeInfos;

    public V058StakingInfoReconciliationSchema(
            @NonNull final Supplier<SortedMap<Long, StakingNodeInfo>> stakingNodeInfos) {
        super(VERSION);
        this.stakingNodeInfos = requireNonNull(stakingNodeInfos);
    }

    @Override
    public void migrate(@NonNull final MigrationContext ctx) {
        final var nodes = ctx.newStates().<EntityNumber, Node>get(V053AddressBookSchema.NODES_KEY);
        stakingNodeInfos.get().forEach((number, info) -> {
            final var key = new EntityNumber(number);
            final var node = nodes.get(key);
            if (node == null) {
                if (!info.deleted()) {
                    // Will not happen with known mainnet state, but added for completeness
                    log.error("Cannot reconcile missing node{} with non-deleted staking info {}", key.number(), info);
                } else {
                    log.info("Creating a missing node{} to reconcile with deleted staking info {}", key.number(), info);
                    final var deletedNode =
                            Node.newBuilder().nodeId(key.number()).deleted(true).build();
                    nodes.put(key, deletedNode);
                }
            }
        });
    }
}
