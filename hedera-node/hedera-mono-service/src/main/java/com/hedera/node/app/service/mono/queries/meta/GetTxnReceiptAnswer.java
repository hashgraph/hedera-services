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
package com.hedera.node.app.service.mono.queries.meta;

import static com.hederahashgraph.api.proto.java.HederaFunctionality.TransactionGetReceipt;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TRANSACTION_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.RECEIPT_NOT_FOUND;

import com.hedera.node.app.service.mono.context.primitives.StateView;
import com.hedera.node.app.service.mono.queries.AnswerService;
import com.hedera.node.app.service.mono.records.RecordCache;
import com.hedera.node.app.service.mono.utils.accessors.SignedTxnAccessor;
import com.hederahashgraph.api.proto.java.HederaFunctionality;
import com.hederahashgraph.api.proto.java.Query;
import com.hederahashgraph.api.proto.java.Response;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.TransactionGetReceiptQuery;
import com.hederahashgraph.api.proto.java.TransactionGetReceiptResponse;
import com.hederahashgraph.api.proto.java.TransactionID;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.util.Optional;
import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class GetTxnReceiptAnswer implements AnswerService {
    private final TransactionID defaultTxnId = TransactionID.getDefaultInstance();
    private final RecordCache recordCache;

    @Inject
    public GetTxnReceiptAnswer(final RecordCache recordCache) {
        this.recordCache = recordCache;
    }

    @Override
    public boolean needsAnswerOnlyCost(final Query query) {
        return false;
    }

    @Override
    public boolean requiresNodePayment(final Query query) {
        return false;
    }

    @Override
    public Response responseGiven(
            final Query query,
            @Nullable final StateView view,
            ResponseCodeEnum validity,
            final long cost) {
        final TransactionGetReceiptQuery op = query.getTransactionGetReceipt();
        final TransactionGetReceiptResponse.Builder opResponse =
                TransactionGetReceiptResponse.newBuilder();

        if (validity == OK) {
            final var txnId = op.getTransactionID();
            final var receipt = recordCache.getPriorityReceipt(txnId);
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
    public ResponseCodeEnum checkValidity(final Query query, final StateView view) {
        final boolean isOk =
                (!defaultTxnId.equals(query.getTransactionGetReceipt().getTransactionID()));

        return isOk ? OK : INVALID_TRANSACTION_ID;
    }

    @Override
    public ResponseCodeEnum extractValidityFrom(final Response response) {
        return response.getTransactionGetReceipt().getHeader().getNodeTransactionPrecheckCode();
    }

    @Override
    public HederaFunctionality canonicalFunction() {
        return TransactionGetReceipt;
    }

    @Override
    public Optional<SignedTxnAccessor> extractPaymentFrom(final Query query) {
        return Optional.empty();
    }
}
