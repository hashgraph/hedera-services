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

import static com.hedera.services.queries.meta.GetTxnRecordAnswer.CHILD_RECORDS_CTX_KEY;
import static com.hedera.services.queries.meta.GetTxnRecordAnswer.DUPLICATE_RECORDS_CTX_KEY;
import static com.hedera.services.queries.meta.GetTxnRecordAnswer.PRIORITY_RECORD_CTX_KEY;
import static com.hedera.services.utils.MiscUtils.putIfNotNull;

import com.hedera.services.context.primitives.StateView;
import com.hedera.services.fees.calculation.FeeCalcUtils;
import com.hedera.services.fees.calculation.QueryResourceUsageEstimator;
import com.hedera.services.queries.answering.AnswerFunctions;
import com.hedera.services.records.RecordCache;
import com.hederahashgraph.api.proto.java.FeeData;
import com.hederahashgraph.api.proto.java.Query;
import com.hederahashgraph.api.proto.java.ResponseType;
import com.hederahashgraph.api.proto.java.TransactionRecord;
import com.hederahashgraph.fee.CryptoFeeBuilder;
import java.util.List;
import java.util.Map;
import java.util.function.BinaryOperator;
import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public final class GetTxnRecordResourceUsage implements QueryResourceUsageEstimator {
    static final TransactionRecord MISSING_RECORD_STANDIN = TransactionRecord.getDefaultInstance();

    private final RecordCache recordCache;
    private final AnswerFunctions answerFunctions;
    private final CryptoFeeBuilder usageEstimator;

    private static final BinaryOperator<FeeData> sumFn = FeeCalcUtils::sumOfUsages;

    @Inject
    public GetTxnRecordResourceUsage(
            final RecordCache recordCache,
            final AnswerFunctions answerFunctions,
            final CryptoFeeBuilder usageEstimator) {
        this.recordCache = recordCache;
        this.usageEstimator = usageEstimator;
        this.answerFunctions = answerFunctions;
    }

    @Override
    public boolean applicableTo(final Query query) {
        return query.hasTransactionGetRecord();
    }

    @Override
    public FeeData usageGiven(
            final Query query, final StateView view, @Nullable final Map<String, Object> queryCtx) {
        return usageFor(
                query, query.getTransactionGetRecord().getHeader().getResponseType(), queryCtx);
    }

    @Override
    public FeeData usageGivenType(
            final Query query, final StateView view, final ResponseType type) {
        return usageFor(query, type, null);
    }

    private FeeData usageFor(
            final Query query,
            final ResponseType stateProofType,
            @Nullable final Map<String, Object> queryCtx) {
        final var op = query.getTransactionGetRecord();
        final var txnRecord =
                answerFunctions.txnRecord(recordCache, op).orElse(MISSING_RECORD_STANDIN);
        var usages = usageEstimator.getTransactionRecordQueryFeeMatrices(txnRecord, stateProofType);
        if (txnRecord != MISSING_RECORD_STANDIN) {
            putIfNotNull(queryCtx, PRIORITY_RECORD_CTX_KEY, txnRecord);
            if (op.getIncludeDuplicates()) {
                final var duplicateRecords = recordCache.getDuplicateRecords(op.getTransactionID());
                putIfNotNull(queryCtx, DUPLICATE_RECORDS_CTX_KEY, duplicateRecords);
                usages = accumulatedUsage(usages, stateProofType, duplicateRecords);
            }
            if (op.getIncludeChildRecords()) {
                final var childRecords = recordCache.getChildRecords(op.getTransactionID());
                putIfNotNull(queryCtx, CHILD_RECORDS_CTX_KEY, childRecords);
                usages = accumulatedUsage(usages, stateProofType, childRecords);
            }
        }
        return usages;
    }

    private FeeData accumulatedUsage(
            FeeData usages,
            final ResponseType stateProofType,
            final List<TransactionRecord> extraRecords) {
        for (final var extra : extraRecords) {
            final var extraUsage =
                    usageEstimator.getTransactionRecordQueryFeeMatrices(extra, stateProofType);
            usages = sumFn.apply(usages, extraUsage);
        }
        return usages;
    }
}
