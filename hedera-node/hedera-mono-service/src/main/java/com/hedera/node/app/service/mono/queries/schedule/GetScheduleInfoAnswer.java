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
package com.hedera.node.app.service.mono.queries.schedule;

import static com.hedera.node.app.service.mono.utils.accessors.SignedTxnAccessor.uncheckedFrom;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.ScheduleGetInfo;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_SCHEDULE_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static com.hederahashgraph.api.proto.java.ResponseType.COST_ANSWER;

import com.hedera.node.app.service.mono.context.primitives.StateView;
import com.hedera.node.app.service.mono.queries.AnswerService;
import com.hedera.node.app.service.mono.utils.accessors.SignedTxnAccessor;
import com.hederahashgraph.api.proto.java.HederaFunctionality;
import com.hederahashgraph.api.proto.java.Query;
import com.hederahashgraph.api.proto.java.Response;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.ScheduleGetInfoQuery;
import com.hederahashgraph.api.proto.java.ScheduleGetInfoResponse;
import com.hederahashgraph.api.proto.java.ScheduleInfo;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class GetScheduleInfoAnswer implements AnswerService {
    public static final String SCHEDULE_INFO_CTX_KEY =
            GetScheduleInfoAnswer.class.getSimpleName() + "_scheduleInfo";

    @Inject
    public GetScheduleInfoAnswer() {
        // Default Constructor
    }

    @Override
    public boolean needsAnswerOnlyCost(final Query query) {
        return COST_ANSWER == query.getScheduleGetInfo().getHeader().getResponseType();
    }

    @Override
    public boolean requiresNodePayment(final Query query) {
        return typicallyRequiresNodePayment(
                query.getScheduleGetInfo().getHeader().getResponseType());
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
        final var schedule = query.getScheduleGetInfo().getScheduleID();

        return view.scheduleExists(schedule) ? OK : INVALID_SCHEDULE_ID;
    }

    @Override
    public HederaFunctionality canonicalFunction() {
        return ScheduleGetInfo;
    }

    @Override
    public ResponseCodeEnum extractValidityFrom(final Response response) {
        return response.getScheduleGetInfo().getHeader().getNodeTransactionPrecheckCode();
    }

    @Override
    public Optional<SignedTxnAccessor> extractPaymentFrom(final Query query) {
        final var paymentTxn = query.getScheduleGetInfo().getHeader().getPayment();
        return Optional.ofNullable(uncheckedFrom(paymentTxn));
    }

    private Response responseFor(
            final Query query,
            final StateView view,
            final ResponseCodeEnum validity,
            final long cost,
            final Optional<Map<String, Object>> queryCtx) {
        final var op = query.getScheduleGetInfo();
        final var response = ScheduleGetInfoResponse.newBuilder();

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

        return Response.newBuilder().setScheduleGetInfo(response).build();
    }

    private void setAnswerOnly(
            final ScheduleGetInfoResponse.Builder response,
            final StateView view,
            final ScheduleGetInfoQuery op,
            final long cost,
            final Optional<Map<String, Object>> queryCtx) {
        if (queryCtx.isPresent()) {
            final var ctx = queryCtx.get();
            if (!ctx.containsKey(SCHEDULE_INFO_CTX_KEY)) {
                response.setHeader(answerOnlyHeader(INVALID_SCHEDULE_ID));
            } else {
                response.setHeader(answerOnlyHeader(OK, cost));
                response.setScheduleInfo((ScheduleInfo) ctx.get(SCHEDULE_INFO_CTX_KEY));
            }
        } else {
            final var info = view.infoForSchedule(op.getScheduleID());
            if (info.isEmpty()) {
                response.setHeader(answerOnlyHeader(INVALID_SCHEDULE_ID));
            } else {
                response.setHeader(answerOnlyHeader(OK, cost));
                response.setScheduleInfo(info.get());
            }
        }
    }
}
