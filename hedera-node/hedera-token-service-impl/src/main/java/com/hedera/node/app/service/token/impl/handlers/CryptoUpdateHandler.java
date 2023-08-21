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
import static com.hedera.hapi.node.base.ResponseCodeEnum.EXISTING_AUTOMATIC_ASSOCIATIONS_EXCEED_GIVEN_LIMIT;
import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_ACCOUNT_ID;
import static com.hedera.hapi.node.base.ResponseCodeEnum.PROXY_ACCOUNT_ID_FIELD_IS_DEPRECATED;
import static com.hedera.hapi.node.base.ResponseCodeEnum.REQUESTED_NUM_AUTOMATIC_ASSOCIATIONS_EXCEEDS_ASSOCIATION_LIMIT;
import static com.hedera.node.app.spi.validation.ExpiryMeta.NA;
import static com.hedera.node.app.spi.workflows.HandleException.validateFalse;
import static com.hedera.node.app.spi.workflows.HandleException.validateTrue;
import static com.hedera.node.app.spi.workflows.PreCheckException.validateFalsePreCheck;
import static com.hedera.node.app.spi.workflows.PreCheckException.validateTruePreCheck;
import static java.util.Objects.requireNonNull;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.HederaFunctionality;
import com.hedera.hapi.node.state.token.Account;
import com.hedera.hapi.node.token.CryptoUpdateTransactionBody;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.node.app.service.token.CryptoSignatureWaivers;
import com.hedera.node.app.service.token.ReadableAccountStore;
import com.hedera.node.app.service.token.impl.WritableAccountStore;
import com.hedera.node.app.service.token.impl.validators.StakingValidator;
import com.hedera.node.app.spi.info.NetworkInfo;
import com.hedera.node.app.spi.validation.EntityType;
import com.hedera.node.app.spi.validation.ExpiryMeta;
import com.hedera.node.app.spi.workflows.HandleContext;
import com.hedera.node.app.spi.workflows.HandleException;
import com.hedera.node.app.spi.workflows.PreCheckException;
import com.hedera.node.app.spi.workflows.PreHandleContext;
import com.hedera.node.app.spi.workflows.TransactionHandler;
import com.hedera.node.config.data.EntitiesConfig;
import com.hedera.node.config.data.LedgerConfig;
import com.hedera.node.config.data.StakingConfig;
import com.hedera.node.config.data.TokensConfig;
import edu.umd.cs.findbugs.annotations.NonNull;
import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * This class contains all workflow-related functionality regarding {@link HederaFunctionality#CRYPTO_UPDATE}.
 */
@Singleton
public class CryptoUpdateHandler extends BaseCryptoHandler implements TransactionHandler {

    private final CryptoSignatureWaivers waivers;
    private StakingValidator stakingValidator;
    private NetworkInfo networkInfo;

    @Inject
    public CryptoUpdateHandler(
            @NonNull final CryptoSignatureWaivers waivers,
            @NonNull final StakingValidator stakingValidator,
            @NonNull final NetworkInfo networkInfo) {
        this.waivers = requireNonNull(waivers, "The supplied argument 'waivers' must not be null");
        this.stakingValidator =
                requireNonNull(stakingValidator, "The supplied argument 'stakingValidator' must not be null");
        this.networkInfo = requireNonNull(networkInfo, "The supplied argument 'networkInfo' must not be null");
    }

    @Override
    public void pureChecks(@NonNull final TransactionBody txn) throws PreCheckException {
        final var op = txn.cryptoUpdateAccountOrThrow();
        validateTruePreCheck(op.hasAccountIDToUpdate(), INVALID_ACCOUNT_ID);
        validateFalsePreCheck(
                op.hasProxyAccountID() && !op.proxyAccountID().equals(AccountID.DEFAULT),
                PROXY_ACCOUNT_ID_FIELD_IS_DEPRECATED);
    }

    @Override
    public void preHandle(@NonNull final PreHandleContext context) throws PreCheckException {
        requireNonNull(context);
        requireNonNull(waivers);
        final var txn = context.body();
        pureChecks(txn);

        final var payer = context.payer();
        final var op = txn.cryptoUpdateAccountOrThrow();

        // update account must exist. Validated in pureChecks
        final var updateAccountId = op.accountIDToUpdateOrThrow();

        // 1. When updating the treasury account 0.0.2 key, the target account must also sign the transaction
        // 2. When updating treasury account 0.0.2, even if system admin is the payer for the transaction,
        // the target account must sign the transaction
        // 3. If the payer for the transaction is 0.0.2 or system admin, then no signatures are needed for the update
        final var targetAccountKeyMustSign = !waivers.isTargetAccountSignatureWaived(txn, payer);
        // 4. Including above 3 conditions, if the target account is 0.0.2, new key must sign the transaction
        final var newAccountKeyMustSign = !waivers.isNewKeySignatureWaived(txn, payer);
        if (targetAccountKeyMustSign) {
            context.requireKeyOrThrow(updateAccountId, INVALID_ACCOUNT_ID);
        }
        if (newAccountKeyMustSign && op.hasKey()) {
            context.requireKey(op.keyOrThrow());
        }
    }

    /**
     * This method is called during the handle workflow. It executes the actual transaction.
     * @param context the {@link HandleContext} which collects all information
     * @throws HandleException if any of the checks fail
     * @throws NullPointerException if any of the arguments are null
     */
    @Override
    public void handle(@NonNull final HandleContext context) {
        final var txn = requireNonNull(context).body();
        final var op = txn.cryptoUpdateAccountOrThrow();
        final var target = op.accountIDToUpdateOrThrow();

        // validate update account exists
        final var accountStore = context.writableStore(WritableAccountStore.class);
        final var targetAccount = accountStore.get(target);
        validateTrue(targetAccount != null, INVALID_ACCOUNT_ID);

        // Customize the account based on fields set in transaction body
        final var builder = updateBuilder(op, targetAccount);

        // validate all checks that involve config and state
        validateSemantics(context, targetAccount, op, builder, accountStore);

        // If an account is detached and expired, validateSemantics would have thrown an exception
        // To reach here, this update transaction has extended the expiration of the account.
        // So, we need to set the expiredAndPendingRemoval flag to false
        if (targetAccount.expiredAndPendingRemoval()) {
            builder.expiredAndPendingRemoval(false);
        }

        // Add account to the modifications in state
        accountStore.put(builder.build());
    }

    /**
     * Add a builder from {@link CryptoUpdateTransactionBody} to create {@link Account.Builder} object
     * @param op Crypto update transaction body
     * @return builder
     */
    private Account.Builder updateBuilder(
            @NonNull final CryptoUpdateTransactionBody op, @NonNull final Account currentAccount) {
        final var builder = currentAccount.copyBuilder();
        if (op.hasKey()) {
            /* Note that {@code this.validateSemantics} will have rejected any txn with an invalid key. */
            builder.key(op.key());
        }
        if (op.hasExpirationTime()) {
            builder.expirationSecond(op.expirationTime().seconds());
        }
        if (op.hasReceiverSigRequiredWrapper()) {
            builder.receiverSigRequired(op.receiverSigRequiredWrapper().booleanValue());
        } else if (op.hasReceiverSigRequired()) {
            builder.receiverSigRequired(op.receiverSigRequired());
        }
        if (op.hasAutoRenewPeriod()) {
            builder.autoRenewSeconds(op.autoRenewPeriod().seconds());
        }
        if (op.hasMemo()) {
            builder.memo(op.memo());
        }
        if (op.hasMaxAutomaticTokenAssociations()) {
            builder.maxAutoAssociations(op.maxAutomaticTokenAssociations());
        }
        if (op.hasDeclineReward()) {
            builder.declineReward(op.declineReward().booleanValue());
        }
        if (op.hasStakedAccountId()) {
            if (AccountID.newBuilder().accountNum(0).build().equals(op.stakedAccountId())) {
                builder.stakedAccountId((AccountID) null);
            } else {
                builder.stakedAccountId(op.stakedAccountId());
            }
        } else if (op.hasStakedNodeId()) {
            builder.stakedNodeId(op.stakedNodeId());
        }
        return builder;
    }

    /**
     * Validate semantics of the transaction. This method is called during the handle workflow.
     * It validates any fields of the transaction that involves state or config.
     * @param context handle context
     * @param updateAccount account to be updated
     * @param op crypto update transaction body
     * @param builder account update builder
     * @param accountStore account store
     */
    private void validateSemantics(
            @NonNull final HandleContext context,
            @NonNull final Account updateAccount,
            @NonNull final CryptoUpdateTransactionBody op,
            @NonNull Account.Builder builder,
            @NonNull final ReadableAccountStore accountStore) {
        final var expiryValidator = context.expiryValidator();
        final var builderAccount = builder.build();
        validateFields(op, context, accountStore);

        validateTrue(!updateAccount.smartContract(), INVALID_ACCOUNT_ID);

        // get needed configs for validation
        final var tokensConfig = context.configuration().getConfigData(TokensConfig.class);
        final var ledgerConfig = context.configuration().getConfigData(LedgerConfig.class);
        final var entitiesConfig = context.configuration().getConfigData(EntitiesConfig.class);

        // validate expiry metadata
        final var currentMetadata = new ExpiryMeta(
                updateAccount.expirationSecond(), updateAccount.autoRenewSeconds(), updateAccount.autoRenewAccountId());
        final var updateMeta = new ExpiryMeta(
                op.hasExpirationTime() ? op.expirationTime().seconds() : NA,
                op.hasAutoRenewPeriod() ? op.autoRenewPeriod().seconds() : NA,
                null);
        context.expiryValidator().resolveUpdateAttempt(currentMetadata, updateMeta);

        // If an account is detached and pending removal, it cannot be updated
        // It can only be updated to extend expiration time
        if (expiryValidator.isDetached(
                EntityType.ACCOUNT, updateAccount.expiredAndPendingRemoval(), updateAccount.tinybarBalance())) {
            validateTrue(builderAccount.expirationSecond() != 0, ACCOUNT_EXPIRED_AND_PENDING_REMOVAL);
        }

        // validate auto associations
        if (builderAccount.maxAutoAssociations() != 0) {
            final long newMax = builderAccount.maxAutoAssociations();
            validateFalse(
                    newMax < updateAccount.usedAutoAssociations(), EXISTING_AUTOMATIC_ASSOCIATIONS_EXCEED_GIVEN_LIMIT);
            validateFalse(
                    newMax > ledgerConfig.maxAutoAssociations(),
                    REQUESTED_NUM_AUTOMATIC_ASSOCIATIONS_EXCEEDS_ASSOCIATION_LIMIT);
            validateFalse(
                    entitiesConfig.limitTokenAssociations() && newMax > tokensConfig.maxPerAccount(),
                    REQUESTED_NUM_AUTOMATIC_ASSOCIATIONS_EXCEEDS_ASSOCIATION_LIMIT);
        }

        // validate if account is not deleted
        validateFalse(updateAccount.deleted(), ACCOUNT_DELETED);
    }

    /**
     * Validate basic fields of the transaction that involves state or config.
     * @param op crypto update transaction body
     * @param context handle context
     * @param accountStore account store
     */
    private void validateFields(
            @NonNull final CryptoUpdateTransactionBody op,
            @NonNull final HandleContext context,
            @NonNull final ReadableAccountStore accountStore) {
        if (op.hasMemo()) {
            context.attributeValidator().validateMemo(op.memo());
        }
        // Empty key list is allowed and is used for immutable entities (e.g. system accounts)
        if (op.hasKey() && !context.attributeValidator().isImmutableKey(op.key())) {
            context.attributeValidator().validateKey(op.key());
        }

        if (op.hasAutoRenewPeriod()) {
            context.attributeValidator()
                    .validateAutoRenewPeriod(op.autoRenewPeriod().seconds());
        }

        stakingValidator.validateStakedIdForUpdate(
                context.configuration().getConfigData(StakingConfig.class).isEnabled(),
                op.hasDeclineReward(),
                op.stakedId().kind().name(),
                op.stakedAccountId(),
                op.stakedNodeId(),
                accountStore,
                networkInfo);
    }
}
