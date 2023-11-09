/*
 * Copyright (C) 2023 Hedera Hashgraph, LLC
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

package com.hedera.node.app.service.networkadmin.impl.handlers;

import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_TRANSACTION_ID;
import static com.hedera.hapi.node.base.ResponseCodeEnum.OK;
import static com.hedera.hapi.node.base.ResponseCodeEnum.RECEIPT_NOT_FOUND;
import static com.hedera.hapi.node.base.ResponseType.COST_ANSWER;
import static java.util.Objects.requireNonNull;

import com.hedera.hapi.node.base.HederaFunctionality;
import com.hedera.hapi.node.base.QueryHeader;
import com.hedera.hapi.node.base.ResponseHeader;
import com.hedera.hapi.node.transaction.Query;
import com.hedera.hapi.node.transaction.Response;
import com.hedera.hapi.node.transaction.TransactionGetReceiptResponse;
import com.hedera.hapi.node.transaction.TransactionRecord;
import com.hedera.node.app.spi.workflows.FreeQueryHandler;
import com.hedera.node.app.spi.workflows.PreCheckException;
import com.hedera.node.app.spi.workflows.QueryContext;
import edu.umd.cs.findbugs.annotations.NonNull;
import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * This class contains all workflow-related functionality regarding {@link HederaFunctionality#TRANSACTION_GET_RECEIPT}.
 */
// use RecordCache for this
@Singleton
public class NetworkTransactionGetReceiptHandler extends FreeQueryHandler {
    @Inject
    public NetworkTransactionGetReceiptHandler() {
        // Exists for injection
    }

    @Override
    public QueryHeader extractHeader(@NonNull final Query query) {
        requireNonNull(query);
        return query.transactionGetReceiptOrThrow().header();
    }

    @Override
    public Response createEmptyResponse(@NonNull final ResponseHeader header) {
        requireNonNull(header);
        final var response = TransactionGetReceiptResponse.newBuilder().header(header);
        return Response.newBuilder().transactionGetReceipt(response).build();
    }

    @Override
    public void validate(@NonNull final QueryContext context) throws PreCheckException {
        requireNonNull(context);
        final var op = context.query().transactionGetReceiptOrThrow();

        // The transaction ID must be specified
        if (!op.hasTransactionID() || !op.transactionID().hasAccountID()) {
            throw new PreCheckException(INVALID_TRANSACTION_ID);
        }
    }

    @Override
    public Response findResponse(@NonNull final QueryContext context, @NonNull final ResponseHeader header) {
        requireNonNull(context);
        requireNonNull(header);
        final var query = context.query();
        final var recordCache = context.recordCache();
        final var op = query.transactionGetReceiptOrThrow();
        final var responseBuilder = TransactionGetReceiptResponse.newBuilder();
        final var transactionId = op.transactionIDOrThrow();

        responseBuilder.header(header);
        final var responseType = op.headerOrElse(QueryHeader.DEFAULT).responseType();
        if (header.nodeTransactionPrecheckCode() == OK && responseType != COST_ANSWER) {
            final var history = recordCache.getHistory(transactionId);
            if (history == null) {
                // Unlike with records, we only return RECEIPT_NOT_FOUND if we have never heard of this transaction.
                responseBuilder.header(header.copyBuilder()
                        .nodeTransactionPrecheckCode(RECEIPT_NOT_FOUND)
                        .build());
            } else {
                responseBuilder.receipt(history.userTransactionReceipt());
                if (op.includeDuplicates()) {
                    responseBuilder.duplicateTransactionReceipts(history.duplicateRecords().stream()
                            .map(TransactionRecord::receiptOrThrow)
                            .toList());
                }
                if (op.includeChildReceipts()) {
                    responseBuilder.childTransactionReceipts(history.childRecords().stream()
                            .map(TransactionRecord::receiptOrThrow)
                            .toList());
                }
            }
        }

        return Response.newBuilder().transactionGetReceipt(responseBuilder).build();
    }
}
