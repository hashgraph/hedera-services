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

package com.hedera.node.app.service.addressbook;

import com.hedera.hapi.node.base.Transaction;
import com.hedera.hapi.node.transaction.TransactionResponse;
import com.hedera.pbj.runtime.RpcMethodDefinition;
import com.hedera.pbj.runtime.RpcServiceDefinition;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Set;

/**
 * The Addressbook Service provides the ability for Hedera Hashgraph to facilitate changes to the nodes used
 * across the Hedera network.
 */
@SuppressWarnings("java:S6548")
public final class AddressBookServiceDefinition implements RpcServiceDefinition {
    public static final AddressBookServiceDefinition INSTANCE = new AddressBookServiceDefinition();

    private static final Set<RpcMethodDefinition<?, ?>> methods = Set.of(

            // Prepare to add a new node to the network.
            // When a valid council member initiates a HAPI transaction to add a new node,
            // then the network should acknowledge the transaction and update the network’s Address Book within 24
            // hours.
            // The added node will not be active until the network is upgraded
            // Request is [NodeCreateTransactionBody](#proto.NodeCreateTransactionBody)
            //
            new RpcMethodDefinition<>("createNode", Transaction.class, TransactionResponse.class),
            // Prepare to update the node to the network.
            // The node will not be updated until the network is upgraded.
            // Request is [NodeUpdateTransactionBody](#proto.NodeUpdateTransactionBody)
            //
            new RpcMethodDefinition<>("updateNode", Transaction.class, TransactionResponse.class),
            // Prepare to delete the node from the network.
            // The deleted node will not be deleted until the network is upgraded.
            // Such a deleted node can never be reused.
            // Request is [NodeDeleteTransactionBody](#proto.NodeDeleteTransactionBody)
            //
            new RpcMethodDefinition<>("deleteNode", Transaction.class, TransactionResponse.class));

    private AddressBookServiceDefinition() {
        // Forbid instantiation
    }

    @Override
    @NonNull
    public String basePath() {
        return "proto.AddressBookService";
    }

    @Override
    @NonNull
    public Set<RpcMethodDefinition<?, ?>> methods() {
        return methods;
    }
}
