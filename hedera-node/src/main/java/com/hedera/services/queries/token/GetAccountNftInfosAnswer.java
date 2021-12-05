package com.hedera.services.queries.token;

/*-
 * ‌
 * Hedera Services Node
 * ​
 * Copyright (C) 2018 - 2021 Hedera Hashgraph, LLC
 * ​
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
 * ‍
 */

import com.hedera.services.context.primitives.StateView;
import com.hedera.services.queries.AnswerService;
import com.hedera.services.txns.validation.OptionValidator;
import com.hedera.services.utils.EntityNum;
import com.hedera.services.utils.SignedTxnAccessor;
import com.hederahashgraph.api.proto.java.HederaFunctionality;
import com.hederahashgraph.api.proto.java.Query;
import com.hederahashgraph.api.proto.java.Response;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.TokenGetAccountNftInfosQuery;
import com.hederahashgraph.api.proto.java.TokenGetAccountNftInfosResponse;
import com.hederahashgraph.api.proto.java.TokenNftInfo;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static com.hedera.services.utils.SignedTxnAccessor.uncheckedFrom;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_ACCOUNT_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_QUERY_RANGE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static com.hederahashgraph.api.proto.java.ResponseType.COST_ANSWER;

@Singleton
public class GetAccountNftInfosAnswer implements AnswerService {
    public static final String ACCOUNT_NFT_INFO_CTX_KEY = GetAccountNftInfosAnswer.class.getSimpleName() + "_accountNftInfos";
    private OptionValidator validator;

    @Inject
    public GetAccountNftInfosAnswer(OptionValidator validator) {
        this.validator = validator;
    }

    @Override
    public boolean needsAnswerOnlyCost(Query query) {
        return COST_ANSWER == query.getTokenGetAccountNftInfos().getHeader().getResponseType();
    }

    @Override
    public boolean requiresNodePayment(Query query) {
        return typicallyRequiresNodePayment(query.getTokenGetAccountNftInfos().getHeader().getResponseType());
    }

    @Override
    public Response responseGiven(Query query, StateView view, ResponseCodeEnum validity, long cost) {
        return responseFor(query, view, validity, cost, NO_QUERY_CTX);
    }

    @Override
    public Response responseGiven(
            Query query,
            StateView view,
            ResponseCodeEnum validity,
            long cost,
            Map<String, Object> queryCtx
    ) {
        return responseFor(query, view, validity, cost, Optional.of(queryCtx));
    }

    @Override
    public ResponseCodeEnum checkValidity(Query query, StateView view) {
        var accountNftInfoQuery = query.getTokenGetAccountNftInfos();
        final var id = EntityNum.fromAccountId(accountNftInfoQuery.getAccountID());
        if (accountNftInfoQuery.getStart() >= accountNftInfoQuery.getEnd()) {
            return INVALID_QUERY_RANGE;
        }
        var validity = validator.nftMaxQueryRangeCheck(accountNftInfoQuery.getStart(), accountNftInfoQuery.getEnd());
        if (validity != OK) {
            return validity;
        }
        final var currentAccounts = view.accounts();
        validity = validator.queryableAccountStatus(id.toGrpcAccountId(), currentAccounts);
        if (validity != OK) {
            final var fallback = validator.queryableContractStatus(id.toGrpcContractId(), currentAccounts);
            if (fallback != OK) {
                return validity;
            }
        }

        var nftCount = view.numNftsOwnedBy(id);
        if (
                accountNftInfoQuery.getStart() < 0 ||
                accountNftInfoQuery.getEnd() < 0 ||
                accountNftInfoQuery.getStart() >= nftCount ||
                accountNftInfoQuery.getEnd() > nftCount
        ) {
            return INVALID_QUERY_RANGE;
        }

        return OK;
    }

    @Override
    public HederaFunctionality canonicalFunction() {
        return HederaFunctionality.TokenGetAccountNftInfos;
    }

    @Override
    public ResponseCodeEnum extractValidityFrom(Response response) {
        return response.getTokenGetAccountNftInfos().getHeader().getNodeTransactionPrecheckCode();
    }

    @Override
    public Optional<SignedTxnAccessor> extractPaymentFrom(Query query) {
        var paymentTxn = query.getTokenGetAccountNftInfos().getHeader().getPayment();
        return Optional.ofNullable(uncheckedFrom(paymentTxn));
    }

    private Response responseFor(
            Query query,
            StateView view,
            ResponseCodeEnum validity,
            long cost,
            Optional<Map<String, Object>> queryCtx
    ) {
        var op = query.getTokenGetAccountNftInfos();
        var response = TokenGetAccountNftInfosResponse.newBuilder();

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

        return Response.newBuilder()
                .setTokenGetAccountNftInfos(response)
                .build();
    }

    private void setAnswerOnly(
            TokenGetAccountNftInfosResponse.Builder response,
            StateView view,
            TokenGetAccountNftInfosQuery op,
            long cost,
            Optional<Map<String, Object>> queryCtx
    ) {
        if (queryCtx.isPresent()) {
            var ctx = queryCtx.get();
            if (!ctx.containsKey(ACCOUNT_NFT_INFO_CTX_KEY)) {
                response.setHeader(answerOnlyHeader(INVALID_ACCOUNT_ID));
            } else {
                response.setHeader(answerOnlyHeader(OK, cost));
                response.addAllNfts((List<TokenNftInfo>)ctx.get(ACCOUNT_NFT_INFO_CTX_KEY));
            }
        } else {
        	final var accountId = EntityNum.fromAccountId(op.getAccountID());
            var info = view.infoForAccountNfts(accountId, op.getStart(), op.getEnd());
            if (info.isEmpty()) {
                response.setHeader(answerOnlyHeader(INVALID_ACCOUNT_ID));
            } else {
                response.setHeader(answerOnlyHeader(OK, cost));
                response.addAllNfts(info.get());
            }
        }
    }
}
