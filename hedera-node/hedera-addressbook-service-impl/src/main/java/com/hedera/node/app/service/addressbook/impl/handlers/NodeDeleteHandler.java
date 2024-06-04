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

package com.hedera.node.app.service.addressbook.impl.handlers;

import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_NODE_ID;
import static com.hedera.hapi.node.base.ResponseCodeEnum.NODE_DELETED;
import static com.hedera.node.app.spi.validation.Validations.mustExist;
import static java.util.Objects.requireNonNull;

import com.hedera.hapi.node.addressbook.NodeDeleteTransactionBody;
import com.hedera.hapi.node.base.HederaFunctionality;
import com.hedera.hapi.node.base.SubType;
import com.hedera.hapi.node.state.addressbook.Node;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.node.app.service.addressbook.ReadableNodeStore;
import com.hedera.node.app.service.addressbook.impl.WritableNodeStore;
import com.hedera.node.app.spi.fees.FeeContext;
import com.hedera.node.app.spi.fees.Fees;
import com.hedera.node.app.spi.workflows.HandleContext;
import com.hedera.node.app.spi.workflows.HandleException;
import com.hedera.node.app.spi.workflows.PreCheckException;
import com.hedera.node.app.spi.workflows.PreHandleContext;
import com.hedera.node.app.spi.workflows.TransactionHandler;
import com.hedera.node.config.data.NodesConfig;
import edu.umd.cs.findbugs.annotations.NonNull;
import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * This class contains all workflow-related functionality regarding {@link HederaFunctionality#NODE_DELETE}.
 */
@Singleton
public class NodeDeleteHandler implements TransactionHandler {
    @Inject
    public NodeDeleteHandler() {
        // Exists for injection
    }

    @Override
    public void pureChecks(@NonNull final TransactionBody txn) throws PreCheckException {
        final NodeDeleteTransactionBody transactionBody = txn.nodeDeleteOrThrow();
        final long nodeId = transactionBody.nodeId();

        if (nodeId <= 0 || nodeId > Long.MAX_VALUE) {
            throw new PreCheckException(INVALID_NODE_ID);
        }
    }

    @Override
    public void preHandle(@NonNull final PreHandleContext context) throws PreCheckException {
        requireNonNull(context);

        final NodeDeleteTransactionBody transactionBody = context.body().nodeDeleteOrThrow();
        final ReadableNodeStore nodeStore = context.createStore(ReadableNodeStore.class);
        final long nodeId = transactionBody.nodeId();

        if (nodeId <= 0 || nodeId > Long.MAX_VALUE) {
            throw new PreCheckException(INVALID_NODE_ID);
        }

        Node node = nodeStore.get(nodeId);
        mustExist(node, INVALID_NODE_ID);
    }

    /**
     * Given the appropriate context, deletes a node.
     *
     * @param context the {@link HandleContext} of the active transaction
     * @throws NullPointerException if one of the arguments is {@code null}
     */
    @Override
    public void handle(@NonNull final HandleContext context) {
        requireNonNull(context);

        final NodeDeleteTransactionBody transactionBody = context.body().nodeDeleteOrThrow();
        var nodeId = transactionBody.nodeId();

        if (nodeId <= 0 || nodeId > Long.MAX_VALUE) {
            throw new HandleException(INVALID_NODE_ID);
        }

        final var nodeStore = context.writableStore(WritableNodeStore.class);

        Node node = nodeStore.get(nodeId);

        if (node == null) {
            throw new HandleException(INVALID_NODE_ID);
        }

        if (node.deleted()) {
            throw new HandleException(NODE_DELETED);
        }

        /* Copy all the fields from existing, and mark deleted flag  */
        final var nodeBuilder = new Node.Builder()
                .nodeId(node.nodeId())
                .accountId(node.accountId())
                .gossipEndpoint(node.gossipEndpoint())
                .serviceEndpoint(node.serviceEndpoint())
                .gossipCaCertificate(node.gossipCaCertificate())
                .grpcCertificateHash(node.grpcCertificateHash())
                .weight(node.weight())
                .deleted(true);

        /* --- Put the modified node. It will be in underlying state's modifications map.
        It will not be committed to state until commit is called on the state.--- */
        nodeStore.put(nodeBuilder.build());
    }

    @NonNull
    @Override
    public Fees calculateFees(@NonNull final FeeContext feeContext) {
        return feeContext.feeCalculator(SubType.DEFAULT).calculate();
    }
}
