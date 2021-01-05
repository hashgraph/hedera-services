package com.hedera.services.queries.schedule;

/*-
 * ‌
 * Hedera Services Node
 * ​
 * Copyright (C) 2018 - 2020 Hedera Hashgraph, LLC
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
import com.hedera.services.utils.SignedTxnAccessor;
import com.hederahashgraph.api.proto.java.HederaFunctionality;
import com.hederahashgraph.api.proto.java.Query;
import com.hederahashgraph.api.proto.java.Response;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.ScheduleGetInfoQuery;
import com.hederahashgraph.api.proto.java.ScheduleGetInfoResponse;
import com.hederahashgraph.api.proto.java.ScheduleInfo;

import java.util.Map;
import java.util.Optional;

import static com.hedera.services.utils.SignedTxnAccessor.uncheckedFrom;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.ScheduleGetInfo;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_SCHEDULE_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static com.hederahashgraph.api.proto.java.ResponseType.COST_ANSWER;

public class GetScheduleInfoAnswer implements AnswerService {
    public static final String SCHEDULE_INFO_CTX_KEY = GetScheduleInfoAnswer.class.getSimpleName() + "_scheduleInfo";

    @Override
    public boolean needsAnswerOnlyCost(Query query) {
        return COST_ANSWER == query.getScheduleGetInfo().getHeader().getResponseType();
    }

    @Override
    public boolean requiresNodePayment(Query query) {
        return typicallyRequiresNodePayment(query.getScheduleGetInfo().getHeader().getResponseType());
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
        var schedule = query.getScheduleGetInfo().getScheduleID();

        return view.scheduleExists(schedule) ? OK : INVALID_SCHEDULE_ID;
    }

    @Override
    public HederaFunctionality canonicalFunction() {
        return ScheduleGetInfo;
    }

    @Override
    public ResponseCodeEnum extractValidityFrom(Response response) {
        return response.getScheduleGetInfo().getHeader().getNodeTransactionPrecheckCode();
    }

    @Override
    public Optional<SignedTxnAccessor> extractPaymentFrom(Query query) {
        var paymentTxn = query.getScheduleGetInfo().getHeader().getPayment();
        return Optional.ofNullable(uncheckedFrom(paymentTxn));
    }

    private Response responseFor(
            Query query,
            StateView view,
            ResponseCodeEnum validity,
            long cost,
            Optional<Map<String, Object>> queryCtx
    ) {
        var op = query.getScheduleGetInfo();
        var response = ScheduleGetInfoResponse.newBuilder();

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
                .setScheduleGetInfo(response)
                .build();
    }

    @SuppressWarnings("unchecked")
    private void setAnswerOnly(
            ScheduleGetInfoResponse.Builder response,
            StateView view,
            ScheduleGetInfoQuery op,
            long cost,
            Optional<Map<String, Object>> queryCtx
    ) {
        if (queryCtx.isPresent()) {
            var ctx = queryCtx.get();
            if (!ctx.containsKey(SCHEDULE_INFO_CTX_KEY)) {
                response.setHeader(answerOnlyHeader(INVALID_SCHEDULE_ID));
            } else {
                response.setHeader(answerOnlyHeader(OK, cost));
                response.setScheduleInfo((ScheduleInfo) ctx.get(SCHEDULE_INFO_CTX_KEY));
            }
        } else {
            var info = view.infoForSchedule(op.getScheduleID());
            if (info.isEmpty()) {
                response.setHeader(answerOnlyHeader(INVALID_SCHEDULE_ID));
            } else {
                response.setHeader(answerOnlyHeader(OK, cost));
                response.setScheduleInfo(info.get());
            }
        }
    }
}
