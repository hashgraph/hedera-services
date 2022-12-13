/*
 * Copyright (C) 2021-2022 Hedera Hashgraph, LLC
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
package com.hedera.node.app.service.mono.queries.contract;

import static com.hedera.node.app.service.mono.utils.EntityIdUtils.unaliased;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.ContractCallLocal;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.BUSY;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.CONTRACT_NEGATIVE_GAS;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.FAIL_INVALID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.MAX_GAS_LIMIT_EXCEEDED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static com.hederahashgraph.api.proto.java.ResponseType.COST_ANSWER;

import com.hedera.node.app.service.mono.context.primitives.StateView;
import com.hedera.node.app.service.mono.context.properties.GlobalDynamicProperties;
import com.hedera.node.app.service.mono.context.properties.NodeLocalProperties;
import com.hedera.node.app.service.mono.contracts.execution.CallLocalEvmTxProcessor;
import com.hedera.node.app.service.mono.contracts.execution.CallLocalExecutor;
import com.hedera.node.app.service.mono.contracts.execution.StaticBlockMetaProvider;
import com.hedera.node.app.service.mono.ledger.accounts.AliasManager;
import com.hedera.node.app.service.mono.ledger.ids.EntityIdSource;
import com.hedera.node.app.service.mono.queries.AbstractAnswer;
import com.hedera.node.app.service.mono.store.AccountStore;
import com.hedera.node.app.service.mono.store.contracts.CodeCache;
import com.hedera.node.app.service.mono.store.contracts.EntityAccess;
import com.hedera.node.app.service.mono.store.contracts.HederaWorldState;
import com.hedera.node.app.service.mono.store.contracts.StaticEntityAccess;
import com.hedera.node.app.service.mono.txns.validation.OptionValidator;
import com.hedera.node.app.service.mono.utils.EntityIdUtils;
import com.hederahashgraph.api.proto.java.ContractCallLocalQuery;
import com.hederahashgraph.api.proto.java.ContractCallLocalResponse;
import com.hederahashgraph.api.proto.java.ContractID;
import com.hederahashgraph.api.proto.java.Query;
import com.hederahashgraph.api.proto.java.Response;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Singleton
public class ContractCallLocalAnswer extends AbstractAnswer {
    private static final Logger log = LogManager.getLogger(ContractCallLocalAnswer.class);

    public static final String CONTRACT_CALL_LOCAL_CTX_KEY =
            ContractCallLocalAnswer.class.getSimpleName() + "_localCallResponse";

    private final AccountStore accountStore;
    private final AliasManager aliasManager;
    private final EntityIdSource ids;
    private final OptionValidator validator;
    private final GlobalDynamicProperties dynamicProperties;
    private final NodeLocalProperties nodeProperties;
    private final Supplier<CallLocalEvmTxProcessor> evmTxProcessorProvider;
    private final StaticBlockMetaProvider blockMetaProvider;

    @Inject
    public ContractCallLocalAnswer(
            final EntityIdSource ids,
            final AliasManager aliasManager,
            final AccountStore accountStore,
            final OptionValidator validator,
            final EntityAccess entityAccess,
            final GlobalDynamicProperties dynamicProperties,
            final NodeLocalProperties nodeProperties,
            final Supplier<CallLocalEvmTxProcessor> evmTxProcessorProvider,
            final StaticBlockMetaProvider blockMetaProvider) {
        super(
                ContractCallLocal,
                query -> query.getContractCallLocal().getHeader().getPayment(),
                query -> query.getContractCallLocal().getHeader().getResponseType(),
                response ->
                        response.getContractCallLocal()
                                .getHeader()
                                .getNodeTransactionPrecheckCode(),
                (query, view) -> {
                    final var op = query.getContractCallLocal();
                    if (op.getGas() < 0) {
                        return CONTRACT_NEGATIVE_GAS;
                    } else if (op.getGas() > dynamicProperties.maxGasPerSec()) {
                        return MAX_GAS_LIMIT_EXCEEDED;
                    } else {
                        if (entityAccess.isTokenAccount(
                                EntityIdUtils.asTypedEvmAddress(op.getContractID()))) {
                            return OK;
                        } else {
                            final var target = unaliased(op.getContractID(), aliasManager);
                            return validator.queryableContractStatus(target, view.contracts());
                        }
                    }
                });

        this.ids = ids;
        this.validator = validator;
        this.aliasManager = aliasManager;
        this.accountStore = accountStore;
        this.dynamicProperties = dynamicProperties;
        this.nodeProperties = nodeProperties;
        this.evmTxProcessorProvider = evmTxProcessorProvider;
        this.blockMetaProvider = blockMetaProvider;
    }

    @Override
    public Response responseGiven(
            final Query query,
            @Nullable final StateView view,
            final ResponseCodeEnum validity,
            final long cost) {
        return responseFor(query, view, validity, cost, NO_QUERY_CTX);
    }

    @Override
    public Response responseGiven(
            final Query query,
            final StateView view,
            final ResponseCodeEnum validity,
            final long cost,
            final Map<String, Object> queryCtx) {
        return responseFor(query, view, validity, cost, Optional.of(queryCtx));
    }

    private Response responseFor(
            final Query query,
            final StateView view,
            final ResponseCodeEnum validity,
            final long cost,
            final Optional<Map<String, Object>> queryCtx) {
        final var op = query.getContractCallLocal();
        final var response = ContractCallLocalResponse.newBuilder();

        final var type = op.getHeader().getResponseType();
        if (validity != OK) {
            response.setHeader(header(validity, type, cost));
        } else {
            if (type == COST_ANSWER) {
                response.setHeader(costAnswerHeader(OK, cost));
            } else {
                setAnswerOnly(response, view, op, cost, queryCtx);
            }
        }

        return Response.newBuilder().setContractCallLocal(response).build();
    }

    @SuppressWarnings("unchecked")
    private void setAnswerOnly(
            final ContractCallLocalResponse.Builder response,
            final StateView view,
            final ContractCallLocalQuery op,
            final long cost,
            final Optional<Map<String, Object>> queryCtx) {
        if (queryCtx.isPresent()) {
            final var ctx = queryCtx.get();
            if (!ctx.containsKey(CONTRACT_CALL_LOCAL_CTX_KEY)) {
                log.error("Usage estimator did not set response used in cost calculation");
                response.setHeader(answerOnlyHeader(FAIL_INVALID, cost));
            } else {
                response.mergeFrom(
                        withCid(
                                (ContractCallLocalResponse) ctx.get(CONTRACT_CALL_LOCAL_CTX_KEY),
                                op.getContractID()));
            }
        } else {
            // If answering from a zero-stake node, there are no node payments, and the
            // usage estimator won't have cached the result it got from the local call
            try {
                final var blockMetaSource = blockMetaProvider.getSource();
                if (blockMetaSource.isEmpty()) {
                    // Should happen rarely if ever, but signal clients they can retry
                    response.setHeader(answerOnlyHeader(BUSY, cost));
                } else {
                    final var entityAccess =
                            new StaticEntityAccess(
                                    Objects.requireNonNull(view), aliasManager, validator);
                    final var codeCache = new CodeCache(nodeProperties, entityAccess);
                    try (final var worldState =
                            new HederaWorldState(ids, entityAccess, codeCache, dynamicProperties)) {
                        final var evmTxProcessor = evmTxProcessorProvider.get();
                        evmTxProcessor.setWorldState(worldState);
                        evmTxProcessor.setBlockMetaSource(blockMetaSource.get());
                        final var opResponse =
                                CallLocalExecutor.execute(
                                        accountStore,
                                        evmTxProcessor,
                                        op,
                                        aliasManager,
                                        entityAccess);
                        response.mergeFrom(withCid(opResponse, op.getContractID()));
                    }
                }
            } catch (final Exception e) {
                log.error("Unable to answer ContractCallLocal", e);
                response.setHeader(answerOnlyHeader(FAIL_INVALID, cost));
            }
        }
    }

    private ContractCallLocalResponse withCid(
            final ContractCallLocalResponse response, final ContractID target) {
        return response.toBuilder()
                .setFunctionResult(response.getFunctionResult().toBuilder().setContractID(target))
                .build();
    }
}
