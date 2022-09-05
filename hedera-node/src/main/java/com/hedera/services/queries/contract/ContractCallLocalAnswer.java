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
package com.hedera.services.queries.contract;

import static com.hedera.services.utils.EntityIdUtils.unaliased;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.ContractCallLocal;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.BUSY;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.CONTRACT_NEGATIVE_GAS;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.FAIL_INVALID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.MAX_GAS_LIMIT_EXCEEDED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static com.hederahashgraph.api.proto.java.ResponseType.COST_ANSWER;

import com.hedera.services.context.primitives.StateView;
import com.hedera.services.context.properties.GlobalDynamicProperties;
import com.hedera.services.context.properties.NodeLocalProperties;
import com.hedera.services.contracts.execution.CallLocalEvmTxProcessor;
import com.hedera.services.contracts.execution.CallLocalExecutor;
import com.hedera.services.contracts.execution.StaticBlockMetaProvider;
import com.hedera.services.ledger.accounts.AliasManager;
import com.hedera.services.ledger.ids.EntityIdSource;
import com.hedera.services.queries.AbstractAnswer;
import com.hedera.services.store.AccountStore;
import com.hedera.services.store.contracts.CodeCache;
import com.hedera.services.store.contracts.EntityAccess;
import com.hedera.services.store.contracts.HederaWorldState;
import com.hedera.services.store.contracts.StaticEntityAccess;
import com.hedera.services.txns.validation.OptionValidator;
import com.hedera.services.utils.EntityIdUtils;
import com.hederahashgraph.api.proto.java.ContractCallLocalQuery;
import com.hederahashgraph.api.proto.java.ContractCallLocalResponse;
import com.hederahashgraph.api.proto.java.ContractID;
import com.hederahashgraph.api.proto.java.Query;
import com.hederahashgraph.api.proto.java.Response;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import javax.annotation.Nullable;
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
    private final CallLocalEvmTxProcessor evmTxProcessor;
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
            final CallLocalEvmTxProcessor evmTxProcessor,
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
                    var op = query.getContractCallLocal();
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
        this.evmTxProcessor = evmTxProcessor;
        this.blockMetaProvider = blockMetaProvider;
    }

    @Override
    public Response responseGiven(
            Query query, @Nullable StateView view, ResponseCodeEnum validity, long cost) {
        return responseFor(query, view, validity, cost, NO_QUERY_CTX);
    }

    @Override
    public Response responseGiven(
            Query query,
            StateView view,
            ResponseCodeEnum validity,
            long cost,
            Map<String, Object> queryCtx) {
        return responseFor(query, view, validity, cost, Optional.of(queryCtx));
    }

    private Response responseFor(
            Query query,
            StateView view,
            ResponseCodeEnum validity,
            long cost,
            Optional<Map<String, Object>> queryCtx) {
        var op = query.getContractCallLocal();
        var response = ContractCallLocalResponse.newBuilder();

        var type = op.getHeader().getResponseType();
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
            ContractCallLocalResponse.Builder response,
            StateView view,
            ContractCallLocalQuery op,
            long cost,
            Optional<Map<String, Object>> queryCtx) {
        if (queryCtx.isPresent()) {
            var ctx = queryCtx.get();
            if (!ctx.containsKey(CONTRACT_CALL_LOCAL_CTX_KEY)) {
                log.warn("Usage estimator did not set response used in cost calculation");
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
                    final var worldState =
                            new HederaWorldState(ids, entityAccess, codeCache, dynamicProperties);
                    evmTxProcessor.setWorldState(worldState);
                    evmTxProcessor.setBlockMetaSource(blockMetaSource.get());
                    final var opResponse =
                            CallLocalExecutor.execute(
                                    accountStore, evmTxProcessor, op, aliasManager, entityAccess);
                    response.mergeFrom(withCid(opResponse, op.getContractID()));
                }
            } catch (Exception e) {
                log.warn("Unable to answer ContractCallLocal", e);
                response.setHeader(answerOnlyHeader(FAIL_INVALID, cost));
            }
        }
    }

    private ContractCallLocalResponse withCid(
            ContractCallLocalResponse response, ContractID target) {
        return response.toBuilder()
                .setFunctionResult(response.getFunctionResult().toBuilder().setContractID(target))
                .build();
    }
}
