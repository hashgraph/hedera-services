/*
 * Copyright (C) 2023-2024 Hedera Hashgraph, LLC
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

package com.hedera.node.app.service.token.impl.handlers.transfer;

import static com.hedera.hapi.node.base.ResponseCodeEnum.FAIL_INVALID;
import static com.hedera.node.app.service.token.AliasUtils.asKeyFromAlias;
import static com.hedera.node.app.service.token.AliasUtils.isOfEvmAddressSize;
import static com.hedera.node.app.service.token.impl.TokenServiceImpl.AUTO_MEMO;
import static com.hedera.node.app.service.token.impl.TokenServiceImpl.LAZY_MEMO;
import static com.hedera.node.app.service.token.impl.TokenServiceImpl.THREE_MONTHS_IN_SECONDS;
import static com.hedera.node.app.service.token.impl.handlers.BaseTokenHandler.UNLIMITED_AUTOMATIC_ASSOCIATIONS;
import static com.hedera.node.app.spi.key.KeyUtils.IMMUTABILITY_SENTINEL_KEY;
import static com.hedera.node.app.spi.workflows.HandleException.validateTrue;
import static java.util.Objects.requireNonNull;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.Duration;
import com.hedera.hapi.node.base.Key;
import com.hedera.hapi.node.base.ResponseCodeEnum;
import com.hedera.hapi.node.token.CryptoCreateTransactionBody;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.node.app.service.token.impl.WritableAccountStore;
import com.hedera.node.app.service.token.records.CryptoCreateStreamBuilder;
import com.hedera.node.app.spi.workflows.HandleContext;
import com.hedera.node.config.data.AccountsConfig;
import com.hedera.node.config.data.EntitiesConfig;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import edu.umd.cs.findbugs.annotations.NonNull;

/**
 * Functionality needed for auto-creating accounts when a CryptoTransfer transaction sends hbar or tokens to an
 * alias that does not yet have an account.
 */
public class AutoAccountCreator {
    private WritableAccountStore accountStore;
    private HandleContext handleContext;

    /**
     * Constructs an {@link AutoAccountCreator} with the given {@link HandleContext}.
     * @param handleContext the context to use for the creation
     */
    public AutoAccountCreator(@NonNull final HandleContext handleContext) {
        this.handleContext = requireNonNull(handleContext);
        this.accountStore = handleContext.storeFactory().writableStore(WritableAccountStore.class);
    }

    /**
     * Creates an account for the given alias.
     *
     * @param alias                      the alias to create the account for
     * @param requiredAutoAssociations   the requiredAutoAssociations to set on the account
     * @return the account ID of the created account
     */
    public AccountID create(@NonNull final Bytes alias, int requiredAutoAssociations) {
        requireNonNull(alias);

        final var accountsConfig = handleContext.configuration().getConfigData(AccountsConfig.class);

        validateTrue(
                accountStore.sizeOfAccountState() + 1 <= accountsConfig.maxNumber(),
                ResponseCodeEnum.MAX_ENTITIES_IN_PRICE_REGIME_HAVE_BEEN_CREATED);

        final TransactionBody.Builder syntheticCreation;
        String memo;

        final var isAliasEVMAddress = isOfEvmAddressSize(alias);
        final var entitiesConfig = handleContext.configuration().getConfigData(EntitiesConfig.class);
        final int autoAssociations = entitiesConfig.unlimitedAutoAssociationsEnabled()
                ? UNLIMITED_AUTOMATIC_ASSOCIATIONS
                : requiredAutoAssociations;
        if (isAliasEVMAddress) {
            syntheticCreation = createHollowAccount(alias, 0L, autoAssociations);
            memo = LAZY_MEMO;
        } else {
            final var key = asKeyFromAlias(alias);
            syntheticCreation = createAccount(alias, key, 0L, autoAssociations);
            memo = AUTO_MEMO;
        }

        // Dispatch the auto-creation record as a preceding record; note we pass null for the
        // "verification assistant" since we have no non-payer signatures to verify here
        final var childRecord = handleContext.dispatchRemovablePrecedingTransaction(
                syntheticCreation.build(),
                CryptoCreateStreamBuilder.class,
                null,
                handleContext.payer(),
                HandleContext.ConsensusThrottling.ON);
        childRecord.memo(memo);

        // If the child transaction failed, we should fail the parent transaction as well and propagate the failure.
        validateTrue(childRecord.status() == ResponseCodeEnum.SUCCESS, childRecord.status());

        // Since we succeeded, we can now look up the account ID of the created account. This really should always
        // work, since the child transaction succeeded. If it did not work for some reason, we have a bug in our
        // code (rather than a bad transaction), so we will fail with FAIL_INVALID.
        final var createdAccountId = accountStore.getAccountIDByAlias(alias);
        validateTrue(createdAccountId != null, FAIL_INVALID);
        return createdAccountId;
    }

    /**
     * Create a transaction body for new hollow-account with the given alias.
     * @param alias alias of the account
     * @param balance initial balance of the account
     * @param maxAutoAssociations maxAutoAssociations of the account
     * @return transaction body for new hollow-account
     */
    public TransactionBody.Builder createHollowAccount(
            @NonNull final Bytes alias, final long balance, final int maxAutoAssociations) {
        final var baseBuilder = createAccountBase(balance, maxAutoAssociations);
        baseBuilder.key(IMMUTABILITY_SENTINEL_KEY).alias(alias).memo(LAZY_MEMO);
        return TransactionBody.newBuilder().cryptoCreateAccount(baseBuilder.build());
    }

    /**
     * Create a transaction body for new account with the given balance and other common fields.
     * @param balance initial balance of the account
     * @param maxAutoAssociations maxAutoAssociations of the account
     * @return transaction body for new account
     */
    private CryptoCreateTransactionBody.Builder createAccountBase(final long balance, final int maxAutoAssociations) {
        return CryptoCreateTransactionBody.newBuilder()
                .initialBalance(balance)
                .maxAutomaticTokenAssociations(maxAutoAssociations)
                .autoRenewPeriod(Duration.newBuilder().seconds(THREE_MONTHS_IN_SECONDS));
    }

    /**
     * Create a transaction body for new account with the given alias, key, balance and maxAutoAssociations.
     * @param alias alias of the account
     * @param key key of the account
     * @param balance initial balance of the account
     * @param maxAutoAssociations maxAutoAssociations of the account
     * @return transaction body for new account
     */
    private TransactionBody.Builder createAccount(
            @NonNull final Bytes alias, @NonNull final Key key, final long balance, final int maxAutoAssociations) {
        final var baseBuilder = createAccountBase(balance, maxAutoAssociations);
        baseBuilder.key(key).alias(alias).memo(AUTO_MEMO).receiverSigRequired(false);
        return TransactionBody.newBuilder().cryptoCreateAccount(baseBuilder.build());
    }
}
