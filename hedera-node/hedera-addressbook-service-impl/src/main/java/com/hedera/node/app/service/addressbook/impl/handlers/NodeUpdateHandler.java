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

import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_ADMIN_KEY;
import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_GOSSIP_CA_CERTIFICATE;
import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_NODE_ACCOUNT_ID;
import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_NODE_ID;
import static com.hedera.node.app.spi.validation.Validations.validateAccountID;
import static com.hedera.node.app.spi.workflows.HandleException.validateFalse;
import static com.hedera.node.app.spi.workflows.HandleException.validateTrue;
import static com.hedera.node.app.spi.workflows.PreCheckException.validateFalsePreCheck;
import static java.util.Objects.requireNonNull;

import com.hedera.hapi.node.addressbook.NodeUpdateTransactionBody;
import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.HederaFunctionality;
import com.hedera.hapi.node.state.addressbook.Node;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.node.app.service.addressbook.ReadableNodeStore;
import com.hedera.node.app.service.addressbook.impl.WritableNodeStore;
import com.hedera.node.app.service.addressbook.impl.validators.AddressBookValidator;
import com.hedera.node.app.service.token.ReadableAccountStore;
import com.hedera.node.app.spi.workflows.HandleContext;
import com.hedera.node.app.spi.workflows.PreCheckException;
import com.hedera.node.app.spi.workflows.PreHandleContext;
import com.hedera.node.app.spi.workflows.TransactionHandler;
import com.hedera.node.config.data.NodesConfig;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import edu.umd.cs.findbugs.annotations.NonNull;
import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * This class contains all workflow-related functionality regarding {@link HederaFunctionality#NODE_UPDATE}.
 */
@Singleton
public class NodeUpdateHandler implements TransactionHandler {
    private final AddressBookValidator addressBookValidator;

    @Inject
    public NodeUpdateHandler(@NonNull final AddressBookValidator addressBookValidator) {
        this.addressBookValidator =
                requireNonNull(addressBookValidator, "The supplied argument 'addressBookValidator' must not be null");
    }

    @Override
    public void pureChecks(@NonNull final TransactionBody txn) throws PreCheckException {
        final var op = txn.nodeUpdate();
        validateFalsePreCheck(op.nodeId() < 0, INVALID_NODE_ID);
        if (op.hasAccountId()) {
            final var accountId = validateAccountID(op.accountIdOrElse(AccountID.DEFAULT), INVALID_NODE_ACCOUNT_ID);
            validateFalsePreCheck(!accountId.hasAccountNum() && accountId.hasAlias(), INVALID_NODE_ACCOUNT_ID);
        }
        if (op.hasGossipCaCertificate())
            validateFalsePreCheck(op.gossipCaCertificate().equals(Bytes.EMPTY), INVALID_GOSSIP_CA_CERTIFICATE);
        if (op.hasAdminKey()) {
            final var adminKey = op.adminKey();
            addressBookValidator.validateAdminKey(adminKey);
        }
    }

    @Override
    public void preHandle(@NonNull final PreHandleContext context) throws PreCheckException {
        requireNonNull(context);
        final var op = context.body().nodeUpdateOrThrow();
        final var nodeStore = context.createStore(ReadableNodeStore.class);

        final var existingNode = nodeStore.get(op.nodeId());
        validateFalse(existingNode == null, INVALID_NODE_ID);
        validateFalsePreCheck(existingNode.deleted(), INVALID_NODE_ID);

        context.requireKeyOrThrow(existingNode.adminKey(), INVALID_ADMIN_KEY);
        if (op.hasAdminKey()) {
            context.requireKeyOrThrow(op.adminKeyOrThrow(), INVALID_ADMIN_KEY);
        }
    }

    @Override
    public void handle(@NonNull final HandleContext handleContext) {
        requireNonNull(handleContext);
        final var op = handleContext.body().nodeUpdate();

        final var configuration = handleContext.configuration();
        final var nodeConfig = configuration.getConfigData(NodesConfig.class);
        final var storeFactory = handleContext.storeFactory();
        final var nodeStore = storeFactory.writableStore(WritableNodeStore.class);
        final var accountStore = storeFactory.readableStore(ReadableAccountStore.class);

        final var existingNode = nodeStore.get(op.nodeId());
        validateFalse(existingNode == null, INVALID_NODE_ID);
        if (op.hasAccountId()) {
            final var accountId = op.accountIdOrThrow();
            validateTrue(accountStore.contains(accountId), INVALID_NODE_ACCOUNT_ID);
        }
        if (op.hasDescription()) addressBookValidator.validateDescription(op.description(), nodeConfig);
        if (!op.gossipEndpoint().isEmpty())
            addressBookValidator.validateGossipEndpoint(op.gossipEndpoint(), nodeConfig);
        if (!op.serviceEndpoint().isEmpty())
            addressBookValidator.validateServiceEndpoint(op.serviceEndpoint(), nodeConfig);
        if (op.hasAdminKey()) addressBookValidator.validateAdminKeyInHandle(handleContext, op.adminKeyOrThrow());

        final var nodeBuilder = updateNode(op, existingNode);
        nodeStore.put(nodeBuilder.build());
    }

    private Node.Builder updateNode(@NonNull final NodeUpdateTransactionBody op, @NonNull final Node node) {
        final var nodeBuilder = node.copyBuilder();
        if (op.hasAccountId()) {
            nodeBuilder.accountId(op.accountId());
        }
        if (op.hasDescription()) {
            nodeBuilder.description(op.description());
        }
        if (!op.gossipEndpoint().isEmpty()) {
            nodeBuilder.gossipEndpoint(op.gossipEndpoint());
        }
        if (!op.serviceEndpoint().isEmpty()) {
            nodeBuilder.serviceEndpoint(op.serviceEndpoint());
        }
        if (op.hasGossipCaCertificate()) {
            nodeBuilder.gossipCaCertificate(op.gossipCaCertificate());
        }
        if (op.hasGrpcCertificateHash()) {
            nodeBuilder.grpcCertificateHash(op.grpcCertificateHash());
        }
        if (op.hasAdminKey()) {
            nodeBuilder.adminKey(op.adminKey());
        }
        return nodeBuilder;
    }
}
