/*
 * Copyright (C) 2020-2022 Hedera Hashgraph, LLC
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
import static com.hedera.node.app.service.mono.utils.accessors.SignedTxnAccessor.uncheckedFrom;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.ContractGetInfo;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.CONTRACT_DELETED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_CONTRACT_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static com.hederahashgraph.api.proto.java.ResponseType.COST_ANSWER;

import com.hedera.node.app.service.mono.context.primitives.StateView;
import com.hedera.node.app.service.mono.context.properties.GlobalDynamicProperties;
import com.hedera.node.app.service.mono.ledger.accounts.AliasManager;
import com.hedera.node.app.service.mono.ledger.accounts.staking.RewardCalculator;
import com.hedera.node.app.service.mono.queries.AnswerService;
import com.hedera.node.app.service.mono.txns.validation.OptionValidator;
import com.hedera.node.app.service.mono.utils.accessors.SignedTxnAccessor;
import com.hederahashgraph.api.proto.java.ContractGetInfoQuery;
import com.hederahashgraph.api.proto.java.ContractGetInfoResponse;
import com.hederahashgraph.api.proto.java.HederaFunctionality;
import com.hederahashgraph.api.proto.java.Query;
import com.hederahashgraph.api.proto.java.Response;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class GetContractInfoAnswer implements AnswerService {
    public static final String CONTRACT_INFO_CTX_KEY =
            GetContractInfoAnswer.class.getSimpleName() + "_contractInfo";

    private final AliasManager aliasManager;
    private final OptionValidator validator;
    private final GlobalDynamicProperties dynamicProperties;
    private final RewardCalculator rewardCalculator;

    @Inject
    public GetContractInfoAnswer(
            final AliasManager aliasManager,
            final OptionValidator validator,
            final GlobalDynamicProperties dynamicProperties,
            final RewardCalculator rewardCalculator) {
        this.aliasManager = aliasManager;
        this.validator = validator;
        this.dynamicProperties = dynamicProperties;
        this.rewardCalculator = rewardCalculator;
    }

    @Override
    public boolean needsAnswerOnlyCost(final Query query) {
        return COST_ANSWER == query.getContractGetInfo().getHeader().getResponseType();
    }

    @Override
    public boolean requiresNodePayment(final Query query) {
        return typicallyRequiresNodePayment(
                query.getContractGetInfo().getHeader().getResponseType());
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

    @Override
    public ResponseCodeEnum checkValidity(final Query query, final StateView view) {
        final var id = unaliased(query.getContractGetInfo().getContractID(), aliasManager);

        final var validity =
                validator.queryableContractStatus(id.toGrpcContractID(), view.contracts());
        return (validity == CONTRACT_DELETED) ? OK : validity;
    }

    @Override
    public HederaFunctionality canonicalFunction() {
        return ContractGetInfo;
    }

    @Override
    public ResponseCodeEnum extractValidityFrom(final Response response) {
        return response.getContractGetInfo().getHeader().getNodeTransactionPrecheckCode();
    }

    @Override
    public Optional<SignedTxnAccessor> extractPaymentFrom(final Query query) {
        final var paymentTxn = query.getContractGetInfo().getHeader().getPayment();
        return Optional.of(uncheckedFrom(paymentTxn));
    }

    private Response responseFor(
            final Query query,
            final StateView view,
            final ResponseCodeEnum validity,
            final long cost,
            final Optional<Map<String, Object>> queryCtx) {
        final var op = query.getContractGetInfo();
        final var response = ContractGetInfoResponse.newBuilder();

        final var type = op.getHeader().getResponseType();
        if (validity != OK) {
            response.setHeader(header(validity, type, cost));
        } else {
            if (type == COST_ANSWER) {
                response.setHeader(costAnswerHeader(OK, cost));
            } else {
                setAnswerOnly(response, Objects.requireNonNull(view), op, cost, queryCtx);
            }
        }

        return Response.newBuilder().setContractGetInfo(response).build();
    }

    @SuppressWarnings("unchecked")
    private void setAnswerOnly(
            final ContractGetInfoResponse.Builder response,
            final StateView view,
            final ContractGetInfoQuery op,
            final long cost,
            final Optional<Map<String, Object>> queryCtx) {
        if (queryCtx.isPresent()) {
            final var ctx = queryCtx.get();
            if (!ctx.containsKey(CONTRACT_INFO_CTX_KEY)) {
                response.setHeader(answerOnlyHeader(INVALID_CONTRACT_ID));
            } else {
                response.setHeader(answerOnlyHeader(OK, cost));
                response.setContractInfo(
                        (ContractGetInfoResponse.ContractInfo) ctx.get(CONTRACT_INFO_CTX_KEY));
            }
        } else {
            final var info =
                    view.infoForContract(
                            op.getContractID(),
                            aliasManager,
                            dynamicProperties.maxTokensRelsPerInfoQuery(),
                            rewardCalculator);
            if (info.isEmpty()) {
                response.setHeader(answerOnlyHeader(INVALID_CONTRACT_ID));
            } else {
                response.setHeader(answerOnlyHeader(OK, cost));
                response.setContractInfo(info.get());
            }
        }
    }
}
