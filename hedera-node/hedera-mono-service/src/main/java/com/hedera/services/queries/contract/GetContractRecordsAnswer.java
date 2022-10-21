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

import static com.hederahashgraph.api.proto.java.HederaFunctionality.ContractGetRecords;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.NOT_SUPPORTED;
import static com.hederahashgraph.api.proto.java.ResponseType.COST_ANSWER;

import com.hedera.services.context.primitives.StateView;
import com.hedera.services.queries.AbstractAnswer;
import com.hederahashgraph.api.proto.java.ContractGetRecordsQuery;
import com.hederahashgraph.api.proto.java.ContractGetRecordsResponse;
import com.hederahashgraph.api.proto.java.Query;
import com.hederahashgraph.api.proto.java.Response;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.ResponseType;
import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class GetContractRecordsAnswer extends AbstractAnswer {
    @Inject
    public GetContractRecordsAnswer() {
        super(
                ContractGetRecords,
                query -> query.getContractGetRecords().getHeader().getPayment(),
                query -> query.getContractGetRecords().getHeader().getResponseType(),
                response ->
                        response.getContractGetRecordsResponse()
                                .getHeader()
                                .getNodeTransactionPrecheckCode(),
                (query, view) -> NOT_SUPPORTED);
    }

    @Override
    public Response responseGiven(
            Query query, @Nullable StateView view, ResponseCodeEnum validity, long cost) {
        ContractGetRecordsQuery op = query.getContractGetRecords();
        ContractGetRecordsResponse.Builder response = ContractGetRecordsResponse.newBuilder();

        ResponseType type = op.getHeader().getResponseType();
        if (type == COST_ANSWER) {
            response.setHeader(costAnswerHeader(NOT_SUPPORTED, 0L));
        } else {
            response.setHeader(answerOnlyHeader(NOT_SUPPORTED));
        }
        return Response.newBuilder().setContractGetRecordsResponse(response).build();
    }
}
