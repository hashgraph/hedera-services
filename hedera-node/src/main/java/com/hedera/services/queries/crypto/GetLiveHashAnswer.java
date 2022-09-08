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
package com.hedera.services.queries.crypto;

import static com.hederahashgraph.api.proto.java.HederaFunctionality.CryptoGetLiveHash;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.NOT_SUPPORTED;
import static com.hederahashgraph.api.proto.java.ResponseType.COST_ANSWER;

import com.hedera.services.context.primitives.StateView;
import com.hedera.services.queries.AnswerService;
import com.hedera.services.utils.accessors.SignedTxnAccessor;
import com.hederahashgraph.api.proto.java.CryptoGetLiveHashQuery;
import com.hederahashgraph.api.proto.java.CryptoGetLiveHashResponse;
import com.hederahashgraph.api.proto.java.HederaFunctionality;
import com.hederahashgraph.api.proto.java.Query;
import com.hederahashgraph.api.proto.java.Response;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.ResponseType;
import java.util.Optional;
import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class GetLiveHashAnswer implements AnswerService {
    @Inject
    public GetLiveHashAnswer() {
        // Default Constructor
    }

    @Override
    public Response responseGiven(
            Query query, @Nullable StateView view, ResponseCodeEnum validity, long cost) {
        CryptoGetLiveHashQuery op = query.getCryptoGetLiveHash();
        ResponseType type = op.getHeader().getResponseType();

        CryptoGetLiveHashResponse.Builder response = CryptoGetLiveHashResponse.newBuilder();
        if (type == COST_ANSWER) {
            response.setHeader(costAnswerHeader(NOT_SUPPORTED, 0L));
        } else {
            response.setHeader(answerOnlyHeader(NOT_SUPPORTED));
        }
        return Response.newBuilder().setCryptoGetLiveHash(response).build();
    }

    @Override
    public ResponseCodeEnum extractValidityFrom(Response response) {
        return response.getCryptoGetLiveHash().getHeader().getNodeTransactionPrecheckCode();
    }

    @Override
    public ResponseCodeEnum checkValidity(Query query, StateView view) {
        return NOT_SUPPORTED;
    }

    @Override
    public HederaFunctionality canonicalFunction() {
        return CryptoGetLiveHash;
    }

    @Override
    public Optional<SignedTxnAccessor> extractPaymentFrom(Query query) {
        return Optional.empty();
    }

    @Override
    public boolean needsAnswerOnlyCost(Query query) {
        return false;
    }

    @Override
    public boolean requiresNodePayment(Query query) {
        return false;
    }
}
