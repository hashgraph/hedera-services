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
package com.hedera.services.queries.meta;

import static com.hederahashgraph.api.proto.java.HederaFunctionality.TransactionGetReceipt;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TRANSACTION_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.RECEIPT_NOT_FOUND;

import com.hedera.services.context.primitives.StateView;
import com.hedera.services.queries.AnswerService;
import com.hedera.services.records.RecordCache;
import com.hedera.services.utils.accessors.SignedTxnAccessor;
import com.hederahashgraph.api.proto.java.HederaFunctionality;
import com.hederahashgraph.api.proto.java.Query;
import com.hederahashgraph.api.proto.java.Response;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.TransactionGetReceiptQuery;
import com.hederahashgraph.api.proto.java.TransactionGetReceiptResponse;
import com.hederahashgraph.api.proto.java.TransactionID;
import java.util.Optional;
import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class GetTxnReceiptAnswer implements AnswerService {
    private final TransactionID defaultTxnId = TransactionID.getDefaultInstance();
    private final RecordCache recordCache;

    @Inject
    public GetTxnReceiptAnswer(RecordCache recordCache) {
        this.recordCache = recordCache;
    }

    @Override
    public boolean needsAnswerOnlyCost(Query query) {
        return false;
    }

    @Override
    public boolean requiresNodePayment(Query query) {
        return false;
    }

    @Override
    public Response responseGiven(
            Query query, @Nullable StateView view, ResponseCodeEnum validity, long cost) {
        TransactionGetReceiptQuery op = query.getTransactionGetReceipt();
        TransactionGetReceiptResponse.Builder opResponse =
                TransactionGetReceiptResponse.newBuilder();

        if (validity == OK) {
            var txnId = op.getTransactionID();
            var receipt = recordCache.getPriorityReceipt(txnId);
            if (receipt == null) {
                validity = RECEIPT_NOT_FOUND;
            } else {
                opResponse.setReceipt(receipt.toGrpc());
                if (op.getIncludeDuplicates()) {
                    opResponse.addAllDuplicateTransactionReceipts(
                            recordCache.getDuplicateReceipts(txnId));
                }
                if (op.getIncludeChildReceipts()) {
                    opResponse.addAllChildTransactionReceipts(recordCache.getChildReceipts(txnId));
                }
            }
        }
        opResponse.setHeader(answerOnlyHeader(validity));

        return Response.newBuilder().setTransactionGetReceipt(opResponse).build();
    }

    @Override
    public ResponseCodeEnum checkValidity(Query query, StateView view) {
        boolean isOk = (!defaultTxnId.equals(query.getTransactionGetReceipt().getTransactionID()));

        return isOk ? OK : INVALID_TRANSACTION_ID;
    }

    @Override
    public ResponseCodeEnum extractValidityFrom(Response response) {
        return response.getTransactionGetReceipt().getHeader().getNodeTransactionPrecheckCode();
    }

    @Override
    public HederaFunctionality canonicalFunction() {
        return TransactionGetReceipt;
    }

    @Override
    public Optional<SignedTxnAccessor> extractPaymentFrom(Query query) {
        return Optional.empty();
    }
}
