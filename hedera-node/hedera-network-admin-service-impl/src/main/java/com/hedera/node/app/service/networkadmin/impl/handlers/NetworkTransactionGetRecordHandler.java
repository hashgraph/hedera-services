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

import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_ACCOUNT_ID;
import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_TRANSACTION_ID;
import static com.hedera.hapi.node.base.ResponseCodeEnum.RECORD_NOT_FOUND;
import static com.hedera.hapi.node.base.ResponseType.COST_ANSWER;
import static com.hedera.node.app.spi.validation.Validations.mustExist;
import static java.util.Objects.requireNonNull;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.HederaFunctionality;
import com.hedera.hapi.node.base.QueryHeader;
import com.hedera.hapi.node.base.ResponseCodeEnum;
import com.hedera.hapi.node.base.ResponseHeader;
import com.hedera.hapi.node.base.TransactionID;
import com.hedera.hapi.node.transaction.Query;
import com.hedera.hapi.node.transaction.Response;
import com.hedera.hapi.node.transaction.TransactionGetRecordResponse;
import com.hedera.hapi.node.transaction.TransactionRecord;
import com.hedera.node.app.spi.records.RecordCache;
import com.hedera.node.app.spi.workflows.PaidQueryHandler;
import com.hedera.node.app.spi.workflows.PreCheckException;
import com.hedera.node.app.spi.workflows.QueryContext;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.ArrayList;
import java.util.List;
import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * This class contains all workflow-related functionality regarding {@link HederaFunctionality#TRANSACTION_GET_RECORD}.
 */
// use RecordCache for this
@Singleton
public class NetworkTransactionGetRecordHandler extends PaidQueryHandler {
    @Inject
    public NetworkTransactionGetRecordHandler() {
        // Exists for injection
    }

    @Override
    public QueryHeader extractHeader(@NonNull final Query query) {
        requireNonNull(query);
        return query.transactionGetRecordOrThrow().header();
    }

    @Override
    public Response createEmptyResponse(@NonNull final ResponseHeader header) {
        requireNonNull(header);
        final var response = TransactionGetRecordResponse.newBuilder().header(header);
        return Response.newBuilder().transactionGetRecord(response).build();
    }

    @Override
    public void validate(@NonNull final QueryContext context) throws PreCheckException {
        requireNonNull(context);
        final var op = context.query().transactionGetRecordOrThrow();

        // The transaction ID must be specified
        if (!op.hasTransactionID()) throw new PreCheckException(INVALID_TRANSACTION_ID);

        // The record must exist for that transaction ID
        final var txnId = op.transactionIDOrThrow();

        // verify that the account id exist and not default
        final var accountID = txnId.accountID();
        if (accountID == null || accountID.equals(AccountID.DEFAULT)) {
            throw new PreCheckException(INVALID_ACCOUNT_ID);
        }

        final var recordCache = context.createStore(RecordCache.class);
        final var record = recordCache.getReceipt(txnId);
        mustExist(record, INVALID_TRANSACTION_ID);
    }

    @Override
    public Response findResponse(@NonNull final QueryContext context, @NonNull final ResponseHeader header) {
        requireNonNull(context);
        requireNonNull(header);
        final var query = context.query();
        final var op = query.transactionGetRecordOrThrow();
        final var responseBuilder = TransactionGetRecordResponse.newBuilder();
        final var transactionId = op.transactionIDOrThrow();
        final var responseType = op.headerOrElse(QueryHeader.DEFAULT).responseType();
        responseBuilder.header(header);
        if (header.nodeTransactionPrecheckCode() == ResponseCodeEnum.OK && responseType != COST_ANSWER) {
            final var recordCache = context.createStore(RecordCache.class);
            final var transactionRecordPrimary = recordCache.getRecord(transactionId);
            if (transactionRecordPrimary == null) {
                responseBuilder.header(header.copyBuilder()
                        .nodeTransactionPrecheckCode(RECORD_NOT_FOUND)
                        .build());
            } else {
                responseBuilder.transactionRecord(transactionRecordPrimary);
                if (op.includeDuplicates()) {
                    final List<TransactionRecord> allTransactionRecords = recordCache.getRecords(transactionId);

                    // remove the primary record from the list
                    final List<TransactionRecord> duplicateTransactionRecords =
                            allTransactionRecords.subList(1, allTransactionRecords.size());
                    responseBuilder.duplicateTransactionRecords(duplicateTransactionRecords);
                }
                if (op.includeChildRecords()) {
                    responseBuilder.childTransactionRecords(transformedChildrenOf(transactionId, recordCache));
                }
            }
        }

        return Response.newBuilder().transactionGetRecord(responseBuilder).build();
    }

    private List<TransactionRecord> transformedChildrenOf(TransactionID transactionID, RecordCache recordCache) {
        final List<TransactionRecord> children = new ArrayList<>();
        // In a transaction id if nonce is 0 it is a parent and if we have any other number it is a child
        for (int nonce = 1; ; nonce++) {
            var childTransactionId = transactionID.copyBuilder().nonce(nonce).build();
            var maybeChildRecord = recordCache.getRecord(childTransactionId);
            if (maybeChildRecord == null) {
                break;
            } else {
                children.add(maybeChildRecord);
            }
        }
        return children;
    }
}
