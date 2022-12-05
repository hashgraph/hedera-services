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
package com.hedera.node.app.service.mono.queries.token;

import static com.hedera.node.app.service.mono.utils.accessors.SignedTxnAccessor.uncheckedFrom;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_NFT_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TOKEN_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TOKEN_NFT_SERIAL_NUMBER;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static com.hederahashgraph.api.proto.java.ResponseType.COST_ANSWER;

import com.hedera.node.app.service.mono.context.primitives.StateView;
import com.hedera.node.app.service.mono.queries.AnswerService;
import com.hedera.node.app.service.mono.utils.accessors.SignedTxnAccessor;
import com.hederahashgraph.api.proto.java.HederaFunctionality;
import com.hederahashgraph.api.proto.java.Query;
import com.hederahashgraph.api.proto.java.Response;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.TokenGetNftInfoQuery;
import com.hederahashgraph.api.proto.java.TokenGetNftInfoResponse;
import com.hederahashgraph.api.proto.java.TokenNftInfo;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.util.Map;
import java.util.Optional;
import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class GetTokenNftInfoAnswer implements AnswerService {
    public static final String NFT_INFO_CTX_KEY =
            GetTokenNftInfoAnswer.class.getSimpleName() + "_nftInfo";

    @Inject
    public GetTokenNftInfoAnswer() {
        // Default constructor
    }

    @Override
    public boolean needsAnswerOnlyCost(final Query query) {
        return COST_ANSWER == query.getTokenGetNftInfo().getHeader().getResponseType();
    }

    @Override
    public boolean requiresNodePayment(final Query query) {
        return typicallyRequiresNodePayment(
                query.getTokenGetNftInfo().getHeader().getResponseType());
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
        final var nftID = query.getTokenGetNftInfo().getNftID();
        if (!nftID.hasTokenID()) {
            return INVALID_TOKEN_ID;
        }

        if (nftID.getSerialNumber() <= 0) {
            return INVALID_TOKEN_NFT_SERIAL_NUMBER;
        }

        return view.nftExists(nftID) ? OK : INVALID_NFT_ID;
    }

    @Override
    public HederaFunctionality canonicalFunction() {
        return HederaFunctionality.TokenGetNftInfo;
    }

    @Override
    public ResponseCodeEnum extractValidityFrom(final Response response) {
        return response.getTokenGetNftInfo().getHeader().getNodeTransactionPrecheckCode();
    }

    @Override
    public Optional<SignedTxnAccessor> extractPaymentFrom(final Query query) {
        final var paymentTxn = query.getTokenGetNftInfo().getHeader().getPayment();
        return Optional.ofNullable(uncheckedFrom(paymentTxn));
    }

    private Response responseFor(
            final Query query,
            final StateView view,
            final ResponseCodeEnum validity,
            final long cost,
            final Optional<Map<String, Object>> queryCtx) {
        final var op = query.getTokenGetNftInfo();
        final var response = TokenGetNftInfoResponse.newBuilder();

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

        return Response.newBuilder().setTokenGetNftInfo(response).build();
    }

    @SuppressWarnings("unchecked")
    private void setAnswerOnly(
            final TokenGetNftInfoResponse.Builder response,
            final StateView view,
            final TokenGetNftInfoQuery op,
            final long cost,
            final Optional<Map<String, Object>> queryCtx) {
        if (queryCtx.isPresent()) {
            final var ctx = queryCtx.get();
            if (!ctx.containsKey(NFT_INFO_CTX_KEY)) {
                response.setHeader(answerOnlyHeader(INVALID_NFT_ID));
            } else {
                response.setHeader(answerOnlyHeader(OK, cost));
                response.setNft((TokenNftInfo) ctx.get(NFT_INFO_CTX_KEY));
            }
        } else {
            final var info = view.infoForNft(op.getNftID());
            if (info.isEmpty()) {
                response.setHeader(answerOnlyHeader(INVALID_NFT_ID));
            } else {
                response.setHeader(answerOnlyHeader(OK, cost));
                response.setNft(info.get());
            }
        }
    }
}
