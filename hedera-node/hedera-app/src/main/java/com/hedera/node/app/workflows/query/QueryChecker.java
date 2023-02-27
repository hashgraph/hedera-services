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
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.NOT_SUPPORTED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static java.util.Objects.requireNonNull;

import com.hedera.node.app.SessionContext;
import com.hedera.node.app.authorization.Authorizer;
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
import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * This class contains all checks related to instances of {@link
 * com.hederahashgraph.api.proto.java.Query}
 */
@Singleton
public class QueryChecker {

    private final WorkflowOnset onset;
    private final HederaAccountNumbers accountNumbers;
    private final QueryFeeCheck queryFeeCheck;
    private final Authorizer authorizer;
    private final CryptoTransferHandler cryptoTransferHandler;

    /**
     * Constructor of {@code QueryChecker}
     *
     * @param onset the {@link WorkflowOnset} that (eventually) pre-processes the CryptoTransfer
     * @param accountNumbers the {@link HederaAccountNumbers} that contains a list of special
     *     accounts
     * @param queryFeeCheck the {@link QueryFeeCheck} that checks if fees can be paid
     * @param authorizer the {@link Authorizer} that checks, if the caller is authorized
     * @param cryptoTransferHandler the {@link CryptoTransferHandler} that validates a contained
     *     {@link com.hederahashgraph.api.proto.java.CryptoTransfer}
     * @throws NullPointerException if one of the arguments is {@code null}
     */
    @Inject
    public QueryChecker(
            @NonNull final WorkflowOnset onset,
            @NonNull final HederaAccountNumbers accountNumbers,
            @NonNull final QueryFeeCheck queryFeeCheck,
            @NonNull final Authorizer authorizer,
            @NonNull final CryptoTransferHandler cryptoTransferHandler) {
        this.onset = requireNonNull(onset);
        this.accountNumbers = requireNonNull(accountNumbers);
        this.queryFeeCheck = requireNonNull(queryFeeCheck);
        this.authorizer = requireNonNull(authorizer);
        this.cryptoTransferHandler = requireNonNull(cryptoTransferHandler);
    }

    /**
     * Validates the {@link com.hederahashgraph.api.proto.java.CryptoTransfer} that is contained in
     * a query
     *
     * @param session the {@link SessionContext} with all parsers
     * @param txn the {@link Transaction} that needs to be checked
     * @return the {@link TransactionBody} that was found in the transaction
     * @throws PreCheckException if validation fails
     * @throws NullPointerException if one of the arguments is {@code null}
     */
    public TransactionBody validateCryptoTransfer(@NonNull final SessionContext session, @NonNull final Transaction txn)
            throws PreCheckException {
        requireNonNull(session);
        requireNonNull(txn);
        final var onsetResult = onset.doParseAndCheck(session, txn);
        if (onsetResult.functionality() != HederaFunctionality.CryptoTransfer) {
            throw new PreCheckException(INSUFFICIENT_TX_FEE);
        }
        final var txBody = onsetResult.txBody();
        cryptoTransferHandler.validate(txBody);
        return txBody;
    }

    /**
     * Validates the account balances needed in a query
     *
     * @param payer the {@link AccountID} of the query's payer
     * @param txBody the {@link TransactionBody} of the {@link
     *     com.hederahashgraph.api.proto.java.CryptoTransfer}
     * @param fee the fee that needs to be paid
     * @throws InsufficientBalanceException if validation fails
     * @throws NullPointerException if one of the arguments is {@code null}
     */
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

        if (accountNumbers.isSuperuser(payer.getAccountNum())) {
            return;
        }

        final var xfers = txBody.getCryptoTransfer().getTransfers().getAccountAmountsList();
        final var feeStatus = queryFeeCheck.nodePaymentValidity(xfers, fee, txBody.getNodeAccountID());
        if (feeStatus != OK) {
            throw new InsufficientBalanceException(feeStatus, fee);
        }
    }

    /**
     * Checks the permission required for a query
     *
     * @param payer the {@link AccountID} of the payer and whose permissions are checked
     * @param functionality the {@link HederaFunctionality} of the query
     * @throws PreCheckException if permissions are not sufficient
     * @throws NullPointerException if one of the arguments is {@code null}
     */
    public void checkPermissions(@NonNull final AccountID payer, @NonNull final HederaFunctionality functionality)
            throws PreCheckException {
        requireNonNull(payer);
        requireNonNull(functionality);

        if (!authorizer.isAuthorized(payer, functionality)) {
            throw new PreCheckException(NOT_SUPPORTED);
        }
    }
}
