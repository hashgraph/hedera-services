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
import static com.hedera.hapi.node.base.ResponseCodeEnum.OK;
import static com.hedera.hapi.node.base.ResponseType.COST_ANSWER;
import static com.hedera.node.app.spi.validation.Validations.mustExist;
import static java.util.Objects.requireNonNull;

import com.hedera.hapi.node.addressbook.NodeGetInfoQuery;
import com.hedera.hapi.node.addressbook.NodeGetInfoResponse;
import com.hedera.hapi.node.addressbook.NodeInfo;
import com.hedera.hapi.node.base.HederaFunctionality;
import com.hedera.hapi.node.base.QueryHeader;
import com.hedera.hapi.node.base.ResponseHeader;
import com.hedera.hapi.node.transaction.Query;
import com.hedera.hapi.node.transaction.Response;
import com.hedera.node.app.service.addressbook.ReadableNodeStore;
import com.hedera.node.app.spi.fees.Fees;
import com.hedera.node.app.spi.workflows.PaidQueryHandler;
import com.hedera.node.app.spi.workflows.PreCheckException;
import com.hedera.node.app.spi.workflows.QueryContext;
import com.hedera.node.config.data.LedgerConfig;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Optional;
import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * This class contains all workflow-related functionality regarding {@link HederaFunctionality#NODE_GET_INFO}.
 */
@Singleton
public class NodeGetInfoHandler extends PaidQueryHandler {

    @Inject
    public NodeGetInfoHandler() {
        // Dagger 2
    }

    @Override
    public QueryHeader extractHeader(@NonNull final Query query) {
        requireNonNull(query);
        return query.nodeGetInfoOrThrow().header();
    }

    @Override
    public Response createEmptyResponse(@NonNull final ResponseHeader header) {
        requireNonNull(header);
        final var response = NodeGetInfoResponse.newBuilder().header(header);
        return Response.newBuilder().nodeGetInfo(response).build();
    }

    @Override
    public void validate(@NonNull final QueryContext context) throws PreCheckException {
        requireNonNull(context);
        final var query = context.query();
        final var nodeStore = context.createStore(ReadableNodeStore.class);
        final NodeGetInfoQuery op = query.nodeGetInfoOrThrow();
        final long nodeId = op.nodeId();
        if (nodeId >= 0) {
            // The node must exist
            final var node = nodeStore.get(nodeId);
            mustExist(node, INVALID_NODE_ID);
        } else {
            throw new PreCheckException(INVALID_NODE_ID);
        }
    }

    @Override
    public Response findResponse(@NonNull final QueryContext context, @NonNull final ResponseHeader header) {
        requireNonNull(context);
        requireNonNull(header);
        final var query = context.query();
        final var config = context.configuration().getConfigData(LedgerConfig.class);
        final var nodeStore = context.createStore(ReadableNodeStore.class);
        final var op = query.nodeGetInfoOrThrow();
        final var response = NodeGetInfoResponse.newBuilder();
        final var node = NodeInfo.newBuilder().nodeId(op.nodeId()).build();
        response.nodeInfo(node);

        final var responseType = op.headerOrElse(QueryHeader.DEFAULT).responseType();
        response.header(header);
        if (header.nodeTransactionPrecheckCode() == OK && responseType != COST_ANSWER) {
            final var optionalInfo = infoForNode(op.nodeId(), nodeStore, config);
            optionalInfo.ifPresent(response::nodeInfo);
        }

        return Response.newBuilder().nodeGetInfo(response).build();
    }

    /**
     * Provides information about a node.
     * @param nodeId the node to get information about
     * @param nodeStore the node store
     * @param config the LedgerConfig
     * @return the information about the node
     */
    private Optional<NodeInfo> infoForNode(
            @NonNull final long nodeId,
            @NonNull final ReadableNodeStore nodeStore,
            @NonNull final LedgerConfig config) {
        final var meta = nodeStore.get(nodeId);
        if (meta == null) {
            return Optional.empty();
        } else {
            final var info = NodeInfo.newBuilder();
            info.nodeId(meta.nodeId());
            if (meta.accountId() != null) info.accountId(meta.accountId());
            info.description(meta.description());
            info.gossipEndpoint(meta.gossipEndpoint());
            info.serviceEndpoint(meta.serviceEndpoint());
            info.gossipCaCertificate(meta.gossipCaCertificate());
            info.grpcCertificateHash(meta.grpcCertificateHash());
            info.weight(meta.weight());
            info.deleted(meta.deleted());
            info.ledgerId(config.id());
            return Optional.of(info.build());
        }
    }

    @NonNull
    @Override
    public Fees computeFees(@NonNull QueryContext queryContext) {
        final var query = queryContext.query();
        final var nodeStore = queryContext.createStore(ReadableNodeStore.class);
        final var op = query.nodeGetInfoOrThrow();
        return queryContext.feeCalculator().calculate();
    }
}
