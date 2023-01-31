/*
 * Copyright (C) 2020-2023 Hedera Hashgraph, LLC
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
package com.hedera.node.app.service.mono.queries.token;

import static com.hedera.node.app.service.mono.utils.accessors.SignedTxnAccessor.uncheckedFrom;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.TokenGetInfo;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TOKEN_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static com.hederahashgraph.api.proto.java.ResponseType.COST_ANSWER;

import com.hedera.node.app.service.mono.context.primitives.StateView;
import com.hedera.node.app.service.mono.queries.AnswerService;
import com.hedera.node.app.service.mono.utils.accessors.SignedTxnAccessor;
import com.hederahashgraph.api.proto.java.HederaFunctionality;
import com.hederahashgraph.api.proto.java.Query;
import com.hederahashgraph.api.proto.java.Response;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.TokenGetInfoQuery;
import com.hederahashgraph.api.proto.java.TokenGetInfoResponse;
import com.hederahashgraph.api.proto.java.TokenInfo;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class GetTokenInfoAnswer implements AnswerService {
    public static final String TOKEN_INFO_CTX_KEY =
            GetTokenInfoAnswer.class.getSimpleName() + "_tokenInfo";

    @Inject
    public GetTokenInfoAnswer() {
        // Default Constructor
    }

    @Override
    public boolean needsAnswerOnlyCost(final Query query) {
        return COST_ANSWER == query.getTokenGetInfo().getHeader().getResponseType();
    }

    @Override
    public boolean requiresNodePayment(final Query query) {
        return typicallyRequiresNodePayment(query.getTokenGetInfo().getHeader().getResponseType());
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
        final var token = query.getTokenGetInfo().getToken();

        return view.tokenExists(token) ? OK : INVALID_TOKEN_ID;
    }

    @Override
    public HederaFunctionality canonicalFunction() {
        return TokenGetInfo;
    }

    @Override
    public ResponseCodeEnum extractValidityFrom(final Response response) {
        return response.getTokenGetInfo().getHeader().getNodeTransactionPrecheckCode();
    }

    @Override
    public Optional<SignedTxnAccessor> extractPaymentFrom(final Query query) {
        final var paymentTxn = query.getTokenGetInfo().getHeader().getPayment();
        return Optional.ofNullable(uncheckedFrom(paymentTxn));
    }

    private Response responseFor(
            final Query query,
            final StateView view,
            final ResponseCodeEnum validity,
            final long cost,
            final Optional<Map<String, Object>> queryCtx) {
        final var op = query.getTokenGetInfo();
        final var response = TokenGetInfoResponse.newBuilder();

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

        return Response.newBuilder().setTokenGetInfo(response).build();
    }

    @SuppressWarnings("unchecked")
    private void setAnswerOnly(
            final TokenGetInfoResponse.Builder response,
            final StateView view,
            final TokenGetInfoQuery op,
            final long cost,
            final Optional<Map<String, Object>> queryCtx) {
        if (queryCtx.isPresent()) {
            final var ctx = queryCtx.get();
            if (!ctx.containsKey(TOKEN_INFO_CTX_KEY)) {
                response.setHeader(answerOnlyHeader(INVALID_TOKEN_ID));
            } else {
                response.setHeader(answerOnlyHeader(OK, cost));
                response.setTokenInfo((TokenInfo) ctx.get(TOKEN_INFO_CTX_KEY));
            }
        } else {
            final var info = view.infoForToken(op.getToken());
            if (info.isEmpty()) {
                response.setHeader(answerOnlyHeader(INVALID_TOKEN_ID));
            } else {
                response.setHeader(answerOnlyHeader(OK, cost));
                response.setTokenInfo(info.get());
            }
        }
    }
}
