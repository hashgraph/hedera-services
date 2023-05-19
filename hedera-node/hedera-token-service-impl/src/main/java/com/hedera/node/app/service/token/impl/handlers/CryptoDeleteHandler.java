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
import com.hedera.node.app.service.token.impl.config.TokenServiceConfig;
import com.hedera.node.app.spi.meta.HandleContext;
import com.hedera.node.app.spi.validation.ExpiryValidator;
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

    /**
     * This method is called during the handle workflow. It executes the actual transaction.
     *
     * <p>Please note: the method signature is just a placeholder which is most likely going to
     * change.
     *
     * @throws NullPointerException if one of the arguments is {@code null}
     */
    public void handle(
            @NonNull final HandleContext context,
            @NonNull final TransactionBody txn,
            @NonNull final WritableAccountStore accountStore) {
        requireNonNull(context);
        requireNonNull(txn);
        requireNonNull(accountStore);

        final var op = txn.cryptoDelete();
        // get the configuration for the token service
        final var config = context.getConfiguration().getConfigData(TokenServiceConfig.class);

        // validate the semantics involving dynamic properties and state. Get delete and transfer accounts
        final var deleteAndTransferAccounts = validateSemantics(op, accountStore, context.expiryValidator(), config);
        transferToBeneficiary(context.expiryValidator(), config, deleteAndTransferAccounts, accountStore);

        accountStore.remove(op.deleteAccountID());
    }

    private void transferToBeneficiary(
            @NonNull final ExpiryValidator expiryValidator,
            @NonNull final TokenServiceConfig config,
            @NonNull final Pair<Account, Account> deleteAndTransferAccounts,
            @NonNull final WritableAccountStore accountStore) {
        final var fromAccount = deleteAndTransferAccounts.getLeft();
        final var toAccount = deleteAndTransferAccounts.getRight();
        final var adjustment = fromAccount.tinybarBalance();

        final long newFromBalance = computeNewBalance(expiryValidator, config, fromAccount, -1 * adjustment);
        final long newToBalance = computeNewBalance(expiryValidator, config, toAccount, adjustment);

        accountStore.put(
                fromAccount.copyBuilder().tinybarBalance(newFromBalance).build());
        accountStore.put(toAccount.copyBuilder().tinybarBalance(newToBalance).build());
    }

    private long computeNewBalance(
            final ExpiryValidator expiryValidator,
            final TokenServiceConfig config,
            final Account account,
            final long adjustment) {
        validateTrue(!account.deleted(), ACCOUNT_DELETED);
        validateTrue(
                !expiryValidator.isDetached(
                        account, config.isAutoRenewEnabled(), config.expireContracts(), config.expireAccounts()),
                ACCOUNT_EXPIRED_AND_PENDING_REMOVAL);
        final long balance = account.tinybarBalance();
        validateTrue(balance + adjustment >= 0, INSUFFICIENT_ACCOUNT_BALANCE);
        return balance + adjustment;
    }

    private Pair<Account, Account> validateSemantics(
            @NonNull final CryptoDeleteTransactionBody op,
            @NonNull final WritableAccountStore accountStore,
            @NonNull final ExpiryValidator expiryValidator,
            @NonNull final TokenServiceConfig config) {
        final var deleteAccountId = op.deleteAccountID();
        final var transferAccountId = op.transferAccountID();

        final var optDeleteAccount = accountStore.get(deleteAccountId);
        validateTrue(optDeleteAccount.isPresent(), INVALID_ACCOUNT_ID);

        final var optTransferAccount = accountStore.get(transferAccountId);
        validateTrue(optTransferAccount.isPresent(), INVALID_TRANSFER_ACCOUNT_ID);

        final var deletedAccount = optDeleteAccount.get();
        final var transferAccount = optTransferAccount.get();
        validateFalse(deletedAccount.numberTreasuryTitles() > 0, ACCOUNT_IS_TREASURY);

        final var isExpired = areAccountsDetached(deletedAccount, transferAccount, config, expiryValidator);
        validateFalse(isExpired, ACCOUNT_EXPIRED_AND_PENDING_REMOVAL);

        validateTrue(deletedAccount.numberPositiveBalances() == 0, TRANSACTION_REQUIRES_ZERO_TOKEN_BALANCES);

        return Pair.of(deletedAccount, transferAccount);
    }

    private boolean areAccountsDetached(
            @NonNull Account deleteAccount,
            @NonNull Account transferAccount,
            @NonNull final TokenServiceConfig config,
            @NonNull final ExpiryValidator expiryValidator) {
        final var autoRenewEnabled = config.isAutoRenewEnabled();
        final var expireContracts = config.expireContracts();
        final var expireAccounts = config.expireAccounts();

        return expiryValidator.isDetached(deleteAccount, autoRenewEnabled, expireContracts, expireAccounts)
                || expiryValidator.isDetached(transferAccount, autoRenewEnabled, expireContracts, expireAccounts);
    }
}
