/*
 * Copyright (C) 2022-2023 Hedera Hashgraph, LLC
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

package com.hedera.node.app.service.token.impl.handlers;

import static com.hedera.hapi.node.base.ResponseCodeEnum.ACCOUNT_DELETED;
import static com.hedera.hapi.node.base.ResponseCodeEnum.ACCOUNT_EXPIRED_AND_PENDING_REMOVAL;
import static com.hedera.hapi.node.base.ResponseCodeEnum.ACCOUNT_ID_DOES_NOT_EXIST;
import static com.hedera.hapi.node.base.ResponseCodeEnum.ACCOUNT_IS_TREASURY;
import static com.hedera.hapi.node.base.ResponseCodeEnum.INSUFFICIENT_ACCOUNT_BALANCE;
import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_ACCOUNT_ID;
import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_TRANSFER_ACCOUNT_ID;
import static com.hedera.hapi.node.base.ResponseCodeEnum.TRANSACTION_REQUIRES_ZERO_TOKEN_BALANCES;
import static com.hedera.hapi.node.base.ResponseCodeEnum.TRANSFER_ACCOUNT_SAME_AS_DELETE_ACCOUNT;
import static com.hedera.node.app.spi.workflows.HandleException.validateFalse;
import static com.hedera.node.app.spi.workflows.HandleException.validateTrue;
import static java.util.Objects.requireNonNull;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.HederaFunctionality;
import com.hedera.hapi.node.state.token.Account;
import com.hedera.hapi.node.token.CryptoDeleteTransactionBody;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.node.app.service.token.impl.WritableAccountStore;
import com.hedera.node.app.spi.validation.EntityType;
import com.hedera.node.app.spi.validation.ExpiryValidator;
import com.hedera.node.app.spi.workflows.HandleContext;
import com.hedera.node.app.spi.workflows.PreCheckException;
import com.hedera.node.app.spi.workflows.PreHandleContext;
import com.hedera.node.app.spi.workflows.TransactionHandler;
import edu.umd.cs.findbugs.annotations.NonNull;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.apache.commons.lang3.tuple.Pair;

/**
 * This class contains all workflow-related functionality regarding {@link
 * HederaFunctionality#CRYPTO_DELETE}.
 */
@Singleton
public class CryptoDeleteHandler implements TransactionHandler {
    @Inject
    public CryptoDeleteHandler() {
        // Exists for injection
    }

    @Override
    public void pureChecks(@NonNull final TransactionBody txn) throws PreCheckException {
        final var op = txn.cryptoDeleteOrThrow();

        if (!op.hasDeleteAccountID() || !op.hasTransferAccountID()) {
            throw new PreCheckException(ACCOUNT_ID_DOES_NOT_EXIST);
        }

        if (op.deleteAccountIDOrThrow().equals(op.transferAccountIDOrThrow())) {
            throw new PreCheckException(TRANSFER_ACCOUNT_SAME_AS_DELETE_ACCOUNT);
        }
    }

    @Override
    public void preHandle(@NonNull final PreHandleContext context) throws PreCheckException {
        requireNonNull(context);
        pureChecks(context.body());
        final var op = context.body().cryptoDeleteOrThrow();
        final var deleteAccountId = op.deleteAccountIDOrElse(AccountID.DEFAULT);
        final var transferAccountId = op.transferAccountIDOrElse(AccountID.DEFAULT);
        context.requireKeyOrThrow(deleteAccountId, INVALID_ACCOUNT_ID)
                .requireKeyIfReceiverSigRequired(transferAccountId, INVALID_TRANSFER_ACCOUNT_ID);
    }

    @Override
    public void handle(@NonNull final HandleContext context) {
        requireNonNull(context);

        final var txn = context.body();
        final var accountStore = context.writableStore(WritableAccountStore.class);

        final var op = txn.cryptoDelete();

        // validate the semantics involving dynamic properties and state.
        // Gets delete and transfer accounts from state
        final var deleteAndTransferAccounts = validateSemantics(op, accountStore, context.expiryValidator());
        transferToBeneficiary(context.expiryValidator(), deleteAndTransferAccounts, accountStore);

        // get the account from account store that has all balance changes
        // commit the account with deleted flag set to true
        final var updatedDeleteAccount = accountStore.get(op.deleteAccountID()).get();
        accountStore.put(updatedDeleteAccount.copyBuilder().deleted(true).build());
    }

    /* ----------------------------- Helper methods -------------------------------- */
    /**
     * Validate the expiration on delete and transfer accounts. Transfer balance from delete account
     * to transfer account if valid.
     * @param expiryValidator expiry validator
     * @param deleteAndTransferAccounts pair of delete and transfer accounts
     * @param accountStore writable account store
     */
    private void transferToBeneficiary(
            @NonNull final ExpiryValidator expiryValidator,
            @NonNull final Pair<Account, Account> deleteAndTransferAccounts,
            @NonNull final WritableAccountStore accountStore) {
        final var fromAccount = deleteAndTransferAccounts.getLeft();
        final var toAccount = deleteAndTransferAccounts.getRight();
        final var adjustment = fromAccount.tinybarBalance();
        //
        final long newFromBalance = computeNewBalance(expiryValidator, fromAccount, -1 * adjustment);
        final long newToBalance = computeNewBalance(expiryValidator, toAccount, adjustment);

        accountStore.put(
                fromAccount.copyBuilder().tinybarBalance(newFromBalance).build());
        accountStore.put(toAccount.copyBuilder().tinybarBalance(newToBalance).build());
    }

    /**
     * Computes new balance for the account based on adjustment. Also validates expiration checks.
     * @param expiryValidator expiry validator
     * @param account account whose balance should be adjusted
     * @param adjustment adjustment amount
     * @return new balance
     */
    private long computeNewBalance(
            final ExpiryValidator expiryValidator, final Account account, final long adjustment) {
        validateTrue(!account.deleted(), ACCOUNT_DELETED);
        validateTrue(
                !expiryValidator.isDetached(
                        EntityType.ACCOUNT, account.expiredAndPendingRemoval(), account.tinybarBalance()),
                ACCOUNT_EXPIRED_AND_PENDING_REMOVAL);
        final long balance = account.tinybarBalance();
        validateTrue(balance + adjustment >= 0, INSUFFICIENT_ACCOUNT_BALANCE);
        return balance + adjustment;
    }

    private Pair<Account, Account> validateSemantics(
            @NonNull final CryptoDeleteTransactionBody op,
            @NonNull final WritableAccountStore accountStore,
            @NonNull final ExpiryValidator expiryValidator) {
        final var deleteAccountId = op.deleteAccountID();
        final var transferAccountId = op.transferAccountID();

        // validate if accounts exist
        final var optDeleteAccount = accountStore.get(deleteAccountId);
        validateTrue(optDeleteAccount.isPresent(), INVALID_ACCOUNT_ID);

        final var optTransferAccount = accountStore.get(transferAccountId);
        validateTrue(optTransferAccount.isPresent(), INVALID_TRANSFER_ACCOUNT_ID);

        // if the account is treasury for any other token, it can't be deleted
        final var deletedAccount = optDeleteAccount.get();
        final var transferAccount = optTransferAccount.get();
        validateFalse(deletedAccount.numberTreasuryTitles() > 0, ACCOUNT_IS_TREASURY);

        // checks if accounts are detached
        final var isExpired = areAccountsDetached(deletedAccount, transferAccount, expiryValidator);
        validateFalse(isExpired, ACCOUNT_EXPIRED_AND_PENDING_REMOVAL);

        // An account can't be deleted if there are any tokens associated with this account
        validateTrue(deletedAccount.numberPositiveBalances() == 0, TRANSACTION_REQUIRES_ZERO_TOKEN_BALANCES);

        return Pair.of(deletedAccount, transferAccount);
    }

    /**
     * Checks if delete account and transfer account are detached
     * @param deleteAccount account to be deleted
     * @param transferAccount beneficiary account
     * @param expiryValidator expiry validator
     * @return true if any on of the accounts is detached, false otherwise
     */
    private boolean areAccountsDetached(
            @NonNull Account deleteAccount,
            @NonNull Account transferAccount,
            @NonNull final ExpiryValidator expiryValidator) {
        return expiryValidator.isDetached(
                        getEntityType(deleteAccount),
                        deleteAccount.expiredAndPendingRemoval(),
                        deleteAccount.tinybarBalance())
                || expiryValidator.isDetached(
                        getEntityType(transferAccount),
                        transferAccount.expiredAndPendingRemoval(),
                        transferAccount.tinybarBalance());
    }

    private EntityType getEntityType(@NonNull final Account account) {
        return account.smartContract() ? EntityType.CONTRACT : EntityType.ACCOUNT;
    }
}
