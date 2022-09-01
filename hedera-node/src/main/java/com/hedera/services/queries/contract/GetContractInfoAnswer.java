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
package com.hedera.services.queries.contract;

import static com.hedera.services.utils.EntityIdUtils.unaliased;
import static com.hedera.services.utils.accessors.SignedTxnAccessor.uncheckedFrom;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.ContractGetInfo;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.CONTRACT_DELETED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_CONTRACT_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static com.hederahashgraph.api.proto.java.ResponseType.COST_ANSWER;

import com.hedera.services.context.primitives.StateView;
import com.hedera.services.context.properties.GlobalDynamicProperties;
import com.hedera.services.ledger.accounts.AliasManager;
import com.hedera.services.ledger.accounts.staking.RewardCalculator;
import com.hedera.services.queries.AnswerService;
import com.hedera.services.txns.validation.OptionValidator;
import com.hedera.services.utils.accessors.SignedTxnAccessor;
import com.hederahashgraph.api.proto.java.ContractGetInfoQuery;
import com.hederahashgraph.api.proto.java.ContractGetInfoResponse;
import com.hederahashgraph.api.proto.java.HederaFunctionality;
import com.hederahashgraph.api.proto.java.Query;
import com.hederahashgraph.api.proto.java.Response;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import javax.annotation.Nullable;
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
    public boolean needsAnswerOnlyCost(Query query) {
        return COST_ANSWER == query.getContractGetInfo().getHeader().getResponseType();
    }

    @Override
    public boolean requiresNodePayment(Query query) {
        return typicallyRequiresNodePayment(
                query.getContractGetInfo().getHeader().getResponseType());
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
    public ResponseCodeEnum extractValidityFrom(Response response) {
        return response.getContractGetInfo().getHeader().getNodeTransactionPrecheckCode();
    }

    @Override
    public Optional<SignedTxnAccessor> extractPaymentFrom(Query query) {
        var paymentTxn = query.getContractGetInfo().getHeader().getPayment();
        return Optional.of(uncheckedFrom(paymentTxn));
    }

    private Response responseFor(
            Query query,
            StateView view,
            ResponseCodeEnum validity,
            long cost,
            Optional<Map<String, Object>> queryCtx) {
        var op = query.getContractGetInfo();
        var response = ContractGetInfoResponse.newBuilder();

        var type = op.getHeader().getResponseType();
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
            ContractGetInfoResponse.Builder response,
            StateView view,
            ContractGetInfoQuery op,
            long cost,
            Optional<Map<String, Object>> queryCtx) {
        if (queryCtx.isPresent()) {
            var ctx = queryCtx.get();
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
