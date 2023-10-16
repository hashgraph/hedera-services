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
import static com.hedera.node.app.hapi.utils.fee.FeeBuilder.BASIC_QUERY_HEADER;
import static com.hedera.node.app.hapi.utils.fee.FeeBuilder.BASIC_QUERY_RES_HEADER;
import static com.hedera.node.app.hapi.utils.fee.FeeBuilder.BASIC_TX_ID_SIZE;
import static com.hedera.node.app.hapi.utils.fee.FeeBuilder.BASIC_TX_RECORD_SIZE;
import static com.hedera.node.app.hapi.utils.fee.FeeBuilder.FEE_MATRICES_CONST;
import static com.hedera.node.app.hapi.utils.fee.FeeBuilder.STATE_PROOF_SIZE;
import static java.util.Objects.requireNonNull;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.HederaFunctionality;
import com.hedera.hapi.node.base.QueryHeader;
import com.hedera.hapi.node.base.ResponseCodeEnum;
import com.hedera.hapi.node.base.ResponseHeader;
import com.hedera.hapi.node.base.ResponseType;
import com.hedera.hapi.node.base.TransactionID;
import com.hedera.hapi.node.transaction.Query;
import com.hedera.hapi.node.transaction.Response;
import com.hedera.hapi.node.transaction.TransactionGetRecordQuery;
import com.hedera.hapi.node.transaction.TransactionGetRecordResponse;
import com.hedera.hapi.node.transaction.TransactionRecord;
import com.hedera.node.app.service.mono.fees.calculation.FeeCalcUtils;
import com.hedera.node.app.spi.fees.Fees;
import com.hedera.node.app.spi.records.RecordCache;
import com.hedera.node.app.spi.workflows.PaidQueryHandler;
import com.hedera.node.app.spi.workflows.PreCheckException;
import com.hedera.node.app.spi.workflows.QueryContext;
import com.hederahashgraph.api.proto.java.FeeComponents;
import com.hederahashgraph.api.proto.java.FeeData;
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
        if (!op.hasTransactionID()) {
            throw new PreCheckException(INVALID_TRANSACTION_ID);
        }

        // The record must exist for that transaction ID
        final var txnId = op.transactionIDOrThrow();

        // verify that the account id exist and not default
        final var accountID = txnId.accountID();
        if (accountID == null || accountID.equals(AccountID.DEFAULT)) {
            throw new PreCheckException(INVALID_ACCOUNT_ID);
        }
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
            final var recordCache = context.recordCache();
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

    private static List<TransactionRecord> transformedChildrenOf(
            final TransactionID transactionID, final RecordCache recordCache) {
        final List<TransactionRecord> children = new ArrayList<>();
        // In a transaction id if nonce is 0 it is a parent and if we have any other number it is a child
        for (int nonce = 1; ; nonce++) {
            final var childTransactionId =
                    transactionID.copyBuilder().nonce(nonce).build();
            final var maybeChildRecord = recordCache.getRecord(childTransactionId);
            if (maybeChildRecord == null) {
                break;
            } else {
                children.add(maybeChildRecord);
            }
        }
        return children;
    }

    @NonNull
    @Override
    public Fees computeFees(@NonNull final QueryContext queryContext) {
        final RecordCache recordCache = queryContext.recordCache();
        final TransactionGetRecordQuery op = queryContext.query().transactionGetRecordOrThrow();

        // fees are the same for all records for a given response type
        // so we calculate them once and multiply by the number of records found

        // calculate per-record fees
        final ResponseType responseType = op.header().responseType();
        final int stateProofSize =
                responseType == ResponseType.ANSWER_STATE_PROOF || responseType == ResponseType.COST_ANSWER_STATE_PROOF
                        ? STATE_PROOF_SIZE
                        : 0;
        final FeeComponents feeMatricesForTxNode = FeeComponents.newBuilder()
                .setConstant(FEE_MATRICES_CONST)
                .setBpt(BASIC_QUERY_HEADER + BASIC_TX_ID_SIZE)
                .setBpr(BASIC_QUERY_RES_HEADER + BASIC_TX_RECORD_SIZE + stateProofSize)
                .build();
        final FeeData perRecordFeeData = FeeData.newBuilder()
                .setNetworkdata(FeeComponents.getDefaultInstance())
                .setNodedata(feeMatricesForTxNode)
                .setServicedata(FeeComponents.getDefaultInstance())
                .build();

        int recordCount = 1; // total num of primary record + duplicate records + child records
        if (op.hasTransactionID()) {
            if (op.includeDuplicates()) {
                // count all the records for the transaction id, then subtract 1 for the primary record
                recordCount += recordCache.getRecords(op.transactionIDOrThrow()).size() - 1;
            }
            if (op.includeChildRecords()) {
                recordCount += transformedChildrenOf(op.transactionIDOrThrow(), recordCache)
                        .size();
            }
        } // else if no tx id found, use per record fee for one record

        // multiply node fees to include duplicate and/or child records
        final FeeData feeData = FeeCalcUtils.multiplierOfUsages(perRecordFeeData, recordCount);
        return queryContext.feeCalculator().legacyCalculate(sigValueObj -> feeData);
    }
}
