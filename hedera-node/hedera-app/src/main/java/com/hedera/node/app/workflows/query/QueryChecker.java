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
package com.hedera.node.app.workflows.query;

import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INSUFFICIENT_TX_FEE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static java.util.Objects.requireNonNull;

import com.hedera.node.app.SessionContext;
import com.hedera.node.app.service.mono.context.domain.security.HapiOpPermissions;
import com.hedera.node.app.service.mono.queries.validation.QueryFeeCheck;
import com.hedera.node.app.service.token.impl.handlers.CryptoTransferHandler;
import com.hedera.node.app.spi.numbers.HederaAccountNumbers;
import com.hedera.node.app.spi.workflows.InsufficientBalanceException;
import com.hedera.node.app.spi.workflows.PreCheckException;
import com.hedera.node.app.workflows.onset.WorkflowOnset;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.HederaFunctionality;
import com.hederahashgraph.api.proto.java.Transaction;
import com.hederahashgraph.api.proto.java.TransactionBody;
import edu.umd.cs.findbugs.annotations.NonNull;

/**
 * This class contains all checks related to instances of {@link
 * com.hederahashgraph.api.proto.java.Query}
 */
public class QueryChecker {

    private final WorkflowOnset onset;
    private final HederaAccountNumbers accountNums;
    private final QueryFeeCheck queryFeeCheck;
    private final HapiOpPermissions hapiOpPermissions;
    private final CryptoTransferHandler cryptoTransferHandler;

    /**
     * Constructor of {@code QueryChecker}
     *
     * @param onset the {@link WorkflowOnset} that (eventually) pre-processes the CryptoTransfer
     * @param accountNums
     * @param queryFeeCheck
     * @param hapiOpPermissions
     * @param cryptoTransferHandler
     */
    public QueryChecker(
            @NonNull final WorkflowOnset onset,
            @NonNull final HederaAccountNumbers accountNums,
            @NonNull final QueryFeeCheck queryFeeCheck,
            @NonNull final HapiOpPermissions hapiOpPermissions,
            @NonNull final CryptoTransferHandler cryptoTransferHandler) {
        this.onset = onset;
        this.accountNums = requireNonNull(accountNums);
        this.queryFeeCheck = requireNonNull(queryFeeCheck);
        this.hapiOpPermissions = requireNonNull(hapiOpPermissions);
        this.cryptoTransferHandler = requireNonNull(cryptoTransferHandler);
    }

    public TransactionBody validateCryptoTransfer(
            final SessionContext session, final Transaction txn) throws PreCheckException {
        final var onsetResult = onset.doParseAndCheck(session, txn);
        if (onsetResult.functionality() != HederaFunctionality.CryptoTransfer) {
            throw new PreCheckException(INSUFFICIENT_TX_FEE);
        }
        final var txBody = onsetResult.txBody();
        cryptoTransferHandler.validate(txBody);
        return txBody;
    }

    public void validateAccountBalances(
            @NonNull final AccountID payer, @NonNull final TransactionBody txBody, final long fee)
            throws InsufficientBalanceException {
        requireNonNull(payer);
        requireNonNull(txBody);

        // TODO: Migrate functionality from the following call (#4207):
        //  solvencyPrecheck.validate(txBody);

        final var xfersStatus = queryFeeCheck.validateQueryPaymentTransfers(txBody);
        if (xfersStatus != OK) {
            throw new InsufficientBalanceException(xfersStatus, fee);
        }

        if (accountNums.isSuperuser(payer.getAccountNum())) {
            return;
        }

        final var xfers = txBody.getCryptoTransfer().getTransfers().getAccountAmountsList();
        final var feeStatus =
                queryFeeCheck.nodePaymentValidity(xfers, fee, txBody.getNodeAccountID());
        if (feeStatus != OK) {
            throw new InsufficientBalanceException(feeStatus, fee);
        }
    }

    public void checkPermissions(
            @NonNull final HederaFunctionality functionality, @NonNull final AccountID payer)
            throws PreCheckException {
        final var permissionStatus = hapiOpPermissions.permissibilityOf(functionality, payer);
        if (permissionStatus != OK) {
            throw new PreCheckException(permissionStatus);
        }
    }
}
