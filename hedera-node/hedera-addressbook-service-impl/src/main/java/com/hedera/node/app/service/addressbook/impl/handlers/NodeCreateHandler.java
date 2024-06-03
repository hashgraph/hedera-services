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

import static com.hedera.hapi.node.base.ResponseCodeEnum.FQDN_SIZE_TOO_LARGE;
import static com.hedera.hapi.node.base.ResponseCodeEnum.GOSSIP_ENDPOINT_CANNOT_HAVE_FQDN;
import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_ENDPOINT;
import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_GOSSIP_CAE_CERTIFICATE;
import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_GOSSIP_ENDPOINT;
import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_NODE_ACCOUNT_ID;
import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_NODE_DESCRIPTION;
import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_SERVICE_ENDPOINT;
import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_TRANSACTION_BODY;
import static com.hedera.hapi.node.base.ResponseCodeEnum.IP_FQDN_CANNOT_BE_SET_FOR_SAME_ENDPOINT;
import static com.hedera.hapi.node.base.ResponseCodeEnum.MAX_NODE_HAVE_BEEN_CREATED;
import static com.hedera.node.app.spi.workflows.HandleException.validateFalse;
import static com.hedera.node.app.spi.workflows.PreCheckException.validateFalsePreCheck;
import static com.hedera.node.app.spi.workflows.PreCheckException.validateTruePreCheck;
import static java.util.Objects.requireNonNull;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.HederaFunctionality;
import com.hedera.hapi.node.base.ServiceEndpoint;
import com.hedera.hapi.node.state.addressbook.Node;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.node.app.service.addressbook.impl.WritableNodeStore;
import com.hedera.node.app.service.addressbook.impl.records.NodeCreateRecordBuilder;
import com.hedera.node.app.service.token.ReadableAccountStore;
import com.hedera.node.app.spi.workflows.HandleContext;
import com.hedera.node.app.spi.workflows.PreCheckException;
import com.hedera.node.app.spi.workflows.PreHandleContext;
import com.hedera.node.app.spi.workflows.TransactionHandler;
import com.hedera.node.app.workflows.handle.validation.AttributeValidatorImpl;
import com.hedera.node.config.data.NodesConfig;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.nio.charset.StandardCharsets;
import java.util.List;
import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * This class contains all workflow-related functionality regarding {@link HederaFunctionality#NODE_CREATE}.
 */
@Singleton
public class NodeCreateHandler implements TransactionHandler {
    @Inject
    public NodeCreateHandler() {
        // Exists for injection
    }

    @Override
    public void pureChecks(@NonNull final TransactionBody txn) throws PreCheckException {
        final var op = txn.nodeCreate();
        validateTruePreCheck(op != null, INVALID_TRANSACTION_BODY);
        final var accountId = op.accountIdOrElse(AccountID.DEFAULT);
        validateFalsePreCheck(accountId.equals(AccountID.DEFAULT), INVALID_NODE_ACCOUNT_ID);
        validateFalsePreCheck(!accountId.hasAccountNum() && accountId.hasAlias(), INVALID_NODE_ACCOUNT_ID);
        validateFalsePreCheck(op.gossipEndpoint().isEmpty(), INVALID_GOSSIP_ENDPOINT);
        validateFalsePreCheck(op.serviceEndpoint().isEmpty(), INVALID_SERVICE_ENDPOINT);
        validateFalsePreCheck(op.gossipCaCertificate().length() == 0, INVALID_GOSSIP_CAE_CERTIFICATE);
    }

    @Override
    public void preHandle(@NonNull final PreHandleContext context) throws PreCheckException {
        requireNonNull(context);
        final var op = context.body().nodeCreate();
        final var accountStore = context.createStore(ReadableAccountStore.class);
        final var accountId = op.accountIdOrElse(AccountID.DEFAULT);

        validateTruePreCheck(accountStore.contains(accountId), INVALID_NODE_ACCOUNT_ID);
    }

    /**
     * Given the appropriate context, creates a new topic.
     *
     * @param handleContext the {@link HandleContext} for the active transaction
     * @throws NullPointerException if one of the arguments is {@code null}
     */
    @Override
    public void handle(@NonNull final HandleContext handleContext) {
        requireNonNull(handleContext);

        final var op = handleContext.body().nodeCreate();

        final var configuration = handleContext.configuration();
        final var nodeConfig = configuration.getConfigData(NodesConfig.class);
        final var nodeStore = handleContext.writableStore(WritableNodeStore.class);

        final var nodeBuilder = new Node.Builder();

        validateFalse(nodeStore.sizeOfState() >= nodeConfig.maxNumber(), MAX_NODE_HAVE_BEEN_CREATED);
        validateDescription(op.description(), nodeConfig);
        validateGossipEndpoint(op.gossipEndpoint(), nodeConfig);
        validateServiceEndpoint(op.serviceEndpoint(), nodeConfig);
        validateFalse(op.gossipCaCertificate().length() == 0, INVALID_GOSSIP_CAE_CERTIFICATE);

        nodeBuilder
                .accountId(op.accountId())
                .description(op.description())
                .gossipEndpoint(op.gossipEndpoint())
                .serviceEndpoint(op.serviceEndpoint())
                .gossipEndpoint(op.gossipEndpoint())
                .serviceEndpoint(op.serviceEndpoint())
                .gossipCaCertificate(op.gossipCaCertificate())
                .grpcCertificateHash(op.grpcCertificateHash())
                .build();
        final var node = nodeBuilder.nodeId(handleContext.newEntityNum()).build();

        nodeStore.put(node);

        final var recordBuilder = handleContext.recordBuilder(NodeCreateRecordBuilder.class);

        recordBuilder.nodeID(node.nodeId());
    }

    private void validateDescription(@Nullable final String description, @NonNull final NodesConfig nodesConfig) {
        if (description == null || description.isEmpty()) {
            return;
        }
        final var raw = description.getBytes(StandardCharsets.UTF_8);
        final var maxUtf8Bytes = nodesConfig.nodeMaxDescriptionUtf8Bytes();
        validateFalse(raw.length > maxUtf8Bytes, INVALID_NODE_DESCRIPTION);
        validateFalse(AttributeValidatorImpl.containsZeroByte(raw), INVALID_NODE_DESCRIPTION);
    }

    private void validateGossipEndpoint(
            @Nullable final List<ServiceEndpoint> endpointLst, @NonNull final NodesConfig nodesConfig) {
        validateFalse(endpointLst == null || endpointLst.isEmpty(), INVALID_GOSSIP_ENDPOINT);
        validateFalse(endpointLst.size() > nodesConfig.maxGossipEndpoint(), INVALID_GOSSIP_ENDPOINT);
        // for phase 2: The first in the list is used as the Internal IP address in config.txt,
        // the second in the list is used as the External IP address in config.txt
        validateFalse(endpointLst.size() < 2, INVALID_GOSSIP_ENDPOINT);

        for (final var endpoint : endpointLst) {
            validateFalse(
                    nodesConfig.gossipFqdnRestricted() && !endpoint.domainName().isEmpty(),
                    GOSSIP_ENDPOINT_CANNOT_HAVE_FQDN);
            validateEndpoint(endpoint, nodesConfig);
        }
    }

    private void validateServiceEndpoint(
            @Nullable final List<ServiceEndpoint> endpointLst, @NonNull final NodesConfig nodesConfig) {
        validateFalse(endpointLst == null || endpointLst.isEmpty(), INVALID_SERVICE_ENDPOINT);
        validateFalse(endpointLst.size() > nodesConfig.maxServiceEndpoint(), INVALID_SERVICE_ENDPOINT);
        for (final var endpoint : endpointLst) {
            validateEndpoint(endpoint, nodesConfig);
        }
    }

    private void validateEndpoint(@NonNull final ServiceEndpoint endpoint, @NonNull final NodesConfig nodesConfig) {
        validateFalse(endpoint.port() == 0, INVALID_ENDPOINT);
        validateFalse(
                endpoint.ipAddressV4().length() == 0
                        && endpoint.domainName().trim().isEmpty(),
                INVALID_ENDPOINT);
        validateFalse(
                endpoint.ipAddressV4().length() != 0
                        && !endpoint.domainName().trim().isEmpty(),
                IP_FQDN_CANNOT_BE_SET_FOR_SAME_ENDPOINT);
        validateFalse(endpoint.domainName().trim().length() > nodesConfig.maxFqdnSize(), FQDN_SIZE_TOO_LARGE);
    }
}
