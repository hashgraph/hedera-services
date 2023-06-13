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
import static com.hedera.node.app.spi.validation.Validations.mustExist;
import static java.util.Objects.requireNonNull;

import com.hedera.hapi.node.base.HederaFunctionality;
import com.hedera.hapi.node.base.QueryHeader;
import com.hedera.hapi.node.base.ResponseHeader;
import com.hedera.hapi.node.base.TransactionID;
import com.hedera.hapi.node.transaction.Query;
import com.hedera.hapi.node.transaction.Response;
import com.hedera.hapi.node.transaction.TransactionGetReceiptResponse;
import com.hedera.hapi.node.transaction.TransactionReceipt;
import com.hedera.node.app.spi.records.RecordCache;
import com.hedera.node.app.spi.workflows.FreeQueryHandler;
import com.hedera.node.app.spi.workflows.PreCheckException;
import com.hedera.node.app.spi.workflows.QueryContext;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.ArrayList;
import java.util.List;
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
        if (!op.hasTransactionID()) throw new PreCheckException(INVALID_TRANSACTION_ID);

        // The receipt must exist for that transaction ID
        final var recordCache = context.createStore(RecordCache.class);
        final var receipt = recordCache.getReceipt(op.transactionIDOrThrow());
        mustExist(receipt, INVALID_TRANSACTION_ID);
    }

    @Override
    public Response findResponse(@NonNull final QueryContext context, @NonNull final ResponseHeader header) {
        requireNonNull(context);
        requireNonNull(header);
        final var query = context.query();
        final var recordCache = context.createStore(RecordCache.class);
        final var transactionGetReceiptQuery = query.transactionGetReceiptOrThrow();
        final var responseBuilder = TransactionGetReceiptResponse.newBuilder();
        final var transactionId = transactionGetReceiptQuery.transactionIDOrThrow();

        final var responseType =
                transactionGetReceiptQuery.headerOrElse(QueryHeader.DEFAULT).responseType();
        responseBuilder.header(header);
        if (header.nodeTransactionPrecheckCode() == OK && responseType != COST_ANSWER) {
            final var transactionReceiptPrimary = recordCache.getReceipt(transactionId);
            if (transactionReceiptPrimary == null) {
                responseBuilder.header(header.copyBuilder()
                        .nodeTransactionPrecheckCode(RECEIPT_NOT_FOUND)
                        .build());
            } else {
                responseBuilder.receipt(transactionReceiptPrimary);
                if (transactionGetReceiptQuery.includeDuplicates()) {
                    final List<TransactionReceipt> allTransactionReceipts = recordCache.getReceipts(transactionId);

                    // remove the primary receipt from the list
                    final List<TransactionReceipt> duplicateTransactionReceipts =
                            allTransactionReceipts.subList(1, allTransactionReceipts.size());
                    responseBuilder.duplicateTransactionReceipts(duplicateTransactionReceipts);
                }
                if (transactionGetReceiptQuery.includeChildReceipts()) {
                    responseBuilder.childTransactionReceipts(transformedChildrenOf(transactionId, recordCache));
                }
            }
        }

        return Response.newBuilder().transactionGetReceipt(responseBuilder).build();
    }

    private List<TransactionReceipt> transformedChildrenOf(TransactionID transactionID, RecordCache recordCache) {
        final List<TransactionReceipt> children = new ArrayList<>();
        // In a transaction id if nonce is 0 it is a parent and if we have any other number it is a child
        for (int nonce = 1; ; nonce++) {
            var childTransactionId = transactionID.copyBuilder().nonce(nonce).build();
            var maybeChildRecord = recordCache.getRecord(childTransactionId);
            if (maybeChildRecord == null) {
                break;
            } else {
                if (maybeChildRecord.receipt() != null) children.add(maybeChildRecord.receipt());
            }
        }
        return children;
    }
}
