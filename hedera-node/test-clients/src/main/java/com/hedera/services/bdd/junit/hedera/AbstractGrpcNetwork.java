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

package com.hedera.services.bdd.junit.hedera;

import static java.util.Objects.requireNonNull;

import com.hedera.services.bdd.junit.hedera.utils.GrpcUtils;
import com.hedera.services.bdd.spec.infrastructure.HapiClients;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.HederaFunctionality;
import com.hederahashgraph.api.proto.java.Query;
import com.hederahashgraph.api.proto.java.Response;
import com.hederahashgraph.api.proto.java.Transaction;
import com.hederahashgraph.api.proto.java.TransactionResponse;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.util.List;

public abstract class AbstractGrpcNetwork extends AbstractNetwork implements HederaNetwork {
    /**
     * The clients for this network, if they are ready.
     */
    @Nullable
    protected HapiClients clients;

    protected AbstractGrpcNetwork(@NonNull final String networkName, @NonNull final List<HederaNode> nodes) {
        super(networkName, nodes);
    }

    protected AbstractGrpcNetwork(
            @NonNull final String networkName,
            @NonNull final List<HederaNode> nodes,
            @NonNull final HapiClients clients) {
        super(networkName, nodes);
        this.clients = requireNonNull(clients);
    }

    /**
     * {@inheritDoc}
     *
     * @throws NullPointerException if the clients are not ready
     */
    @Override
    public @NonNull Response send(
            @NonNull final Query query,
            @NonNull final HederaFunctionality functionality,
            @NonNull final com.hederahashgraph.api.proto.java.AccountID nodeAccountId) {
        requireNonNull(clients, "clients are not ready");
        requireNonNull(query);
        requireNonNull(functionality);
        requireNonNull(nodeAccountId);
        return GrpcUtils.send(query, clients, functionality, nodeAccountId);
    }

    /**
     * {@inheritDoc}
     *
     * @throws NullPointerException if the clients are not ready
     */
    @Override
    public TransactionResponse submit(
            @NonNull final Transaction transaction,
            @NonNull final HederaFunctionality functionality,
            @NonNull final SystemFunctionalityTarget target,
            @NonNull final AccountID nodeAccountId) {
        requireNonNull(clients, "clients are not ready");
        requireNonNull(transaction);
        requireNonNull(functionality);
        requireNonNull(nodeAccountId);
        return GrpcUtils.submit(transaction, clients, functionality, target, nodeAccountId);
    }
}
