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
package com.hedera.services.fees.calculation.crypto.queries;

import static com.hedera.services.queries.meta.GetTxnRecordAnswer.PAYER_RECORDS_CTX_KEY;
import static com.hedera.services.utils.EntityNum.fromAccountId;
import static com.hedera.services.utils.MiscUtils.putIfNotNull;

import com.hedera.services.context.primitives.StateView;
import com.hedera.services.fees.calculation.QueryResourceUsageEstimator;
import com.hedera.services.queries.answering.AnswerFunctions;
import com.hederahashgraph.api.proto.java.FeeData;
import com.hederahashgraph.api.proto.java.Query;
import com.hederahashgraph.api.proto.java.ResponseType;
import com.hederahashgraph.fee.CryptoFeeBuilder;
import java.util.Map;
import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public final class GetAccountRecordsResourceUsage implements QueryResourceUsageEstimator {
    private final AnswerFunctions answerFunctions;
    private final CryptoFeeBuilder usageEstimator;

    @Inject
    public GetAccountRecordsResourceUsage(
            final AnswerFunctions answerFunctions, final CryptoFeeBuilder usageEstimator) {
        this.answerFunctions = answerFunctions;
        this.usageEstimator = usageEstimator;
    }

    @Override
    public boolean applicableTo(final Query query) {
        return query.hasCryptoGetAccountRecords();
    }

    @Override
    public FeeData usageGiven(
            final Query query, final StateView view, final Map<String, Object> queryCtx) {
        return usageFor(
                query,
                view,
                query.getCryptoGetAccountRecords().getHeader().getResponseType(),
                queryCtx);
    }

    @Override
    public FeeData usageGivenType(
            final Query query, final StateView view, final ResponseType type) {
        return usageFor(query, view, type, null);
    }

    private FeeData usageFor(
            final Query query,
            final StateView view,
            final ResponseType stateProofType,
            @Nullable final Map<String, Object> queryCtx) {
        final var op = query.getCryptoGetAccountRecords();
        final var target = fromAccountId(op.getAccountID());
        if (!view.accounts().containsKey(target)) {
            /* Given the test in {@code GetAccountRecordsAnswer.checkValidity}, this can only be
             * missing under the extraordinary circumstance that the desired account expired
             * during the query answer flow (which will now fail downstream with an appropriate
             * status code); so just return the default {@code FeeData} here. */
            return FeeData.getDefaultInstance();
        }
        final var records = answerFunctions.mostRecentRecords(view, op);
        putIfNotNull(queryCtx, PAYER_RECORDS_CTX_KEY, records);
        return usageEstimator.getCryptoAccountRecordsQueryFeeMatrices(records, stateProofType);
    }
}
