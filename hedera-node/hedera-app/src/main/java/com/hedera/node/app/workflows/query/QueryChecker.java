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

import static com.hedera.hapi.node.base.HederaFunctionality.CRYPTO_TRANSFER;
import static com.hedera.hapi.node.base.ResponseCodeEnum.INSUFFICIENT_TX_FEE;
import static com.hedera.hapi.node.base.ResponseCodeEnum.NOT_SUPPORTED;
import static java.util.Objects.requireNonNull;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.HederaFunctionality;
import com.hedera.hapi.node.base.TransferList;
import com.hedera.hapi.node.transaction.Query;
import com.hedera.node.app.authorization.Authorizer;
import com.hedera.node.app.fees.QueryFeeCheck;
import com.hedera.node.app.service.token.impl.handlers.CryptoTransferHandler;
import com.hedera.node.app.solvency.SolvencyPreCheck;
import com.hedera.node.app.spi.numbers.HederaAccountNumbers;
import com.hedera.node.app.spi.workflows.PreCheckException;
import com.hedera.node.app.workflows.TransactionInfo;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Collections;
import javax.inject.Inject;
import javax.inject.Singleton;

/** This class contains all checks related to instances of {@link Query} */
@Singleton
public class QueryChecker {

    private final HederaAccountNumbers accountNumbers;
    private final QueryFeeCheck queryFeeCheck;
    private final Authorizer authorizer;
    private final CryptoTransferHandler cryptoTransferHandler;
    private final SolvencyPreCheck solvencyPreCheck;

    /**
     * Constructor of {@code QueryChecker}
     *
     * @param accountNumbers the {@link HederaAccountNumbers} that contains a list of special accounts
     * @param queryFeeCheck the {@link QueryFeeCheck} that checks if fees can be paid
     * @param authorizer the {@link Authorizer} that checks, if the caller is authorized
     * @param cryptoTransferHandler the {@link CryptoTransferHandler} that validates a contained
     * {@link HederaFunctionality#CRYPTO_TRANSFER}.
     * @param solvencyPreCheck the {@link SolvencyPreCheck} that checks if the payer has enough
     * @throws NullPointerException if one of the arguments is {@code null}
     */
    @Inject
    public QueryChecker(
            @NonNull final HederaAccountNumbers accountNumbers,
            @NonNull final QueryFeeCheck queryFeeCheck,
            @NonNull final Authorizer authorizer,
            @NonNull final CryptoTransferHandler cryptoTransferHandler,
            @NonNull final SolvencyPreCheck solvencyPreCheck) {
        this.accountNumbers = requireNonNull(accountNumbers);
        this.queryFeeCheck = requireNonNull(queryFeeCheck);
        this.authorizer = requireNonNull(authorizer);
        this.cryptoTransferHandler = requireNonNull(cryptoTransferHandler);
        this.solvencyPreCheck = requireNonNull(solvencyPreCheck);
    }

    /**
     * Validates the {@link HederaFunctionality#CRYPTO_TRANSFER} that is contained in a query
     *
     * @param transactionInfo the {@link TransactionInfo} that contains all data about the transaction
     * @throws PreCheckException if validation fails
     * @throws NullPointerException if one of the arguments is {@code null}
     */
    public void validateCryptoTransfer(@NonNull final TransactionInfo transactionInfo) throws PreCheckException {
        requireNonNull(transactionInfo);
        if (transactionInfo.functionality() != CRYPTO_TRANSFER) {
            throw new PreCheckException(INSUFFICIENT_TX_FEE);
        }
        final var txBody = transactionInfo.txBody();
        cryptoTransferHandler.validate(txBody);
    }

    /**
     * Validates the account balances needed in a query
     *
     * @param payer the {@link AccountID} of the query's payer
     * @param transactionInfo the {@link TransactionInfo} of the {@link HederaFunctionality#CRYPTO_TRANSFER}
     * @param fee the fee that needs to be paid
     * @throws PreCheckException if validation fails
     * @throws NullPointerException if one of the arguments is {@code null}
     */
    public void validateAccountBalances(
            @NonNull final AccountID payer, @NonNull final TransactionInfo transactionInfo, final long fee)
            throws PreCheckException {
        requireNonNull(payer);
        requireNonNull(transactionInfo);

        final var transaction = transactionInfo.transaction();
        final var txBody = transactionInfo.txBody();

        solvencyPreCheck.assessWithSvcFees(transaction);

        queryFeeCheck.validateQueryPaymentTransfers(txBody, fee);

        // A super-user cannot use an alias. Sorry, Clark Kent.
        if (payer.hasAccountNum() && accountNumbers.isSuperuser(payer.accountNumOrThrow())) {
            return;
        }

        final var xfers = txBody.cryptoTransferOrThrow()
                .transfersOrElse(TransferList.DEFAULT)
                .accountAmountsOrElse(Collections.emptyList());
        queryFeeCheck.nodePaymentValidity(xfers, fee, txBody.nodeAccountID());
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
