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
import static com.hedera.hapi.node.base.ResponseCodeEnum.ACCOUNT_ID_DOES_NOT_EXIST;
import static com.hedera.hapi.node.base.ResponseCodeEnum.INSUFFICIENT_PAYER_BALANCE;
import static com.hedera.hapi.node.base.ResponseCodeEnum.INSUFFICIENT_TX_FEE;
import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_ACCOUNT_AMOUNTS;
import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_RECEIVING_NODE_ACCOUNT;
import static com.hedera.hapi.node.base.ResponseCodeEnum.NOT_SUPPORTED;
import static com.hedera.hapi.node.base.ResponseCodeEnum.PAYER_ACCOUNT_NOT_FOUND;
import static java.util.Objects.requireNonNull;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.HederaFunctionality;
import com.hedera.hapi.node.transaction.Query;
import com.hedera.node.app.authorization.Authorizer;
import com.hedera.node.app.service.token.ReadableAccountStore;
import com.hedera.node.app.service.token.impl.handlers.CryptoTransferHandler;
import com.hedera.node.app.spi.workflows.InsufficientBalanceException;
import com.hedera.node.app.spi.workflows.PreCheckException;
import com.hedera.node.app.validation.ExpiryValidation;
import com.hedera.node.app.workflows.SolvencyPreCheck;
import com.hedera.node.app.workflows.TransactionInfo;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Objects;
import javax.inject.Inject;
import javax.inject.Singleton;

/** This class contains all checks related to instances of {@link Query} */
@Singleton
public class QueryChecker {

    private final Authorizer authorizer;
    private final CryptoTransferHandler cryptoTransferHandler;
    private final SolvencyPreCheck solvencyPreCheck;
    private final ExpiryValidation expiryValidation;

    /**
     * Constructor of {@code QueryChecker}
     *
     * @param authorizer the {@link Authorizer} that checks, if the caller is authorized
     * @param cryptoTransferHandler the {@link CryptoTransferHandler} that validates a contained
     * {@link HederaFunctionality#CRYPTO_TRANSFER}.
     * @param solvencyPreCheck the {@link SolvencyPreCheck} that checks if the payer has enough
     * @param expiryValidation the {@link ExpiryValidation} that checks if an account is expired
     * @throws NullPointerException if one of the arguments is {@code null}
     */
    @Inject
    public QueryChecker(
            @NonNull final Authorizer authorizer,
            @NonNull final CryptoTransferHandler cryptoTransferHandler,
            @NonNull final SolvencyPreCheck solvencyPreCheck,
            @NonNull final ExpiryValidation expiryValidation) {
        this.authorizer = requireNonNull(authorizer);
        this.cryptoTransferHandler = requireNonNull(cryptoTransferHandler);
        this.solvencyPreCheck = requireNonNull(solvencyPreCheck);
        this.expiryValidation = requireNonNull(expiryValidation);
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
        cryptoTransferHandler.pureChecks(txBody);
    }

    /**
     * Validates the account balances needed in a query
     *
     * @param accountStore the {@link ReadableAccountStore} used to access accounts
     * @param txInfo the {@link TransactionInfo} of the {@link HederaFunctionality#CRYPTO_TRANSFER}
     * @param fee the fee that needs to be paid
     * @throws PreCheckException if validation fails
     * @throws NullPointerException if one of the arguments is {@code null}
     */
    public void validateAccountBalances(
            @NonNull final ReadableAccountStore accountStore, @NonNull final TransactionInfo txInfo, final long fee)
            throws PreCheckException {
        requireNonNull(accountStore);
        requireNonNull(txInfo);

        final var payerID = txInfo.payerID();
        final var nodeAccountID = txInfo.txBody().nodeAccountIDOrThrow();
        final var transfers =
                txInfo.txBody().cryptoTransferOrThrow().transfersOrThrow().accountAmountsOrThrow();

        // Try to find account
        final var payer = accountStore.getAccountById(txInfo.payerID());
        if (payer == null) {
            // This should never happen, because the account is checked in the pure checks
            throw new PreCheckException(PAYER_ACCOUNT_NOT_FOUND);
        }

        // FUTURE: Currently we check the solvency twice: once with and once without service fees (in IngestChecker)
        // https://github.com/hashgraph/hedera-services/issues/8356
        solvencyPreCheck.checkSolvency(txInfo, payer, fee);

        if (transfers.isEmpty()) {
            throw new PreCheckException(INVALID_ACCOUNT_AMOUNTS);
        }

        boolean nodeReceivesSome = false;
        for (final var transfer : transfers) {
            final var accountID = transfer.accountIDOrThrow();
            final var amount = transfer.amount();
            // Need to figure out, what is special about this and replace with a constant
            if (amount == Long.MIN_VALUE) {
                throw new PreCheckException(INVALID_ACCOUNT_AMOUNTS);
            }

            // Only check non-payer accounts
            if (!Objects.equals(accountID, payerID)) {

                final var account = accountStore.getAccountById(accountID);
                if (account == null) {
                    throw new PreCheckException(ACCOUNT_ID_DOES_NOT_EXIST);
                }

                // The balance only needs to be checked for sent amounts (= negative values)
                if (amount < 0 && account.tinybarBalance() < -amount) {
                    // FUTURE: Expiry should probably be checked earlier
                    expiryValidation.checkAccountExpiry(account);
                    throw new InsufficientBalanceException(INSUFFICIENT_PAYER_BALANCE, fee);
                }

                // Make sure the node receives enough
                if (amount > 0 && nodeAccountID.equals(transfer.accountIDOrThrow())) {
                    nodeReceivesSome = true;
                    if (amount < fee) {
                        throw new InsufficientBalanceException(INSUFFICIENT_TX_FEE, fee);
                    }
                }
            }
        }

        if (!nodeReceivesSome) {
            throw new PreCheckException(INVALID_RECEIVING_NODE_ACCOUNT);
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
