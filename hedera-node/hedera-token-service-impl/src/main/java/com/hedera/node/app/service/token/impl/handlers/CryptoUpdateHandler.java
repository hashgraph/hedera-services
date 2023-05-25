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

import static com.hedera.hapi.node.base.ResponseCodeEnum.ACCOUNT_EXPIRED_AND_PENDING_REMOVAL;
import static com.hedera.hapi.node.base.ResponseCodeEnum.EXISTING_AUTOMATIC_ASSOCIATIONS_EXCEED_GIVEN_LIMIT;
import static com.hedera.hapi.node.base.ResponseCodeEnum.EXPIRATION_REDUCTION_NOT_ALLOWED;
import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_ACCOUNT_ID;
import static com.hedera.hapi.node.base.ResponseCodeEnum.PROXY_ACCOUNT_ID_FIELD_IS_DEPRECATED;
import static com.hedera.hapi.node.base.ResponseCodeEnum.REQUESTED_NUM_AUTOMATIC_ASSOCIATIONS_EXCEEDS_ASSOCIATION_LIMIT;
import static com.hedera.node.app.spi.validation.ExpiryMeta.NA;
import static com.hedera.node.app.spi.workflows.HandleException.validateFalse;
import static com.hedera.node.app.spi.workflows.HandleException.validateTrue;
import static java.util.Objects.requireNonNull;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.HederaFunctionality;
import com.hedera.hapi.node.state.token.Account;
import com.hedera.hapi.node.state.token.Account.Builder;
import com.hedera.hapi.node.token.CryptoUpdateTransactionBody;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.node.app.service.token.CryptoSignatureWaivers;
import com.hedera.node.app.service.token.impl.WritableAccountStore;
import com.hedera.node.app.spi.meta.HandleContext;
import com.hedera.node.app.spi.validation.ExpiryMeta;
import com.hedera.node.app.spi.workflows.PreCheckException;
import com.hedera.node.app.spi.workflows.PreHandleContext;
import com.hedera.node.app.spi.workflows.TransactionHandler;
import com.hedera.node.config.data.AutoRenewConfig;
import com.hedera.node.config.data.EntitiesConfig;
import com.hedera.node.config.data.LedgerConfig;
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

    @Inject
    public CryptoUpdateHandler(@NonNull final CryptoSignatureWaivers waivers) {
        this.waivers = requireNonNull(waivers, "The supplied argument 'waivers' must not be null");
    }

    public void pureChecks(@NonNull final TransactionBody txn) throws PreCheckException {
        final var op = txn.cryptoUpdateAccountOrThrow();
        if (op.hasProxyAccountID() && !op.proxyAccountID().equals(AccountID.DEFAULT)) {
            throw new PreCheckException(PROXY_ACCOUNT_ID_FIELD_IS_DEPRECATED);
        }
    }

    @Override
    public void preHandle(@NonNull final PreHandleContext context) throws PreCheckException {
        requireNonNull(context);
        requireNonNull(waivers);
        final var txn = context.body();
        pureChecks(txn);
        final var payer = context.payer();
        final var op = txn.cryptoUpdateAccountOrThrow();
        final var updateAccountId = op.accountIDToUpdateOrElse(AccountID.DEFAULT);

        final var newAccountKeyMustSign = !waivers.isNewKeySignatureWaived(txn, payer);
        final var targetAccountKeyMustSign = !waivers.isTargetAccountSignatureWaived(txn, payer);
        if (targetAccountKeyMustSign) {
            context.requireKeyOrThrow(updateAccountId, INVALID_ACCOUNT_ID);
        }
        if (newAccountKeyMustSign && op.hasKey()) {
            context.requireKey(op.keyOrThrow());
        }
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
        final var op = txn.cryptoUpdateAccount();
        final var target = op.accountIDToUpdateOrThrow();

        // Customize the account based on fields set in transaction body
        final var builder = updateBuilder(op);

        // validate all checks that involve config and state
        validateSemantics(context, accountStore, op, builder);

        if (context.expiryValidator().isDetached(target)) {
            builder.expiredAndPendingRemoval(false);
        }

        // Add account to the modifications in state
        accountStore.put(builder.build());
    }

    private Account.Builder updateBuilder(@NonNull final CryptoUpdateTransactionBody op) {
        final var builder = Account.newBuilder();
        if (op.hasKey()) {
            /* Note that {@code this.validateSemantics} will have rejected any txn with an invalid key. */
            builder.key(op.key());
        }
        if (op.hasExpirationTime()) {
            builder.expiry(op.expirationTime().seconds());
        }
        if (op.hasReceiverSigRequiredWrapper()) {
            builder.receiverSigRequired(op.receiverSigRequired().booleanValue());
        } else if (op.receiverSigRequired()) {
            builder.receiverSigRequired(true);
        }
        if (op.hasAutoRenewPeriod()) {
            builder.autoRenewSecs(op.autoRenewPeriod().seconds());
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
        if (op.hasStakedAccountId() || op.hasStakedNodeId()) {
            builder.stakedNumber(getStakedId(op.stakedId().kind().toString(), op.stakedNodeId(), op.stakedAccountId()));
        }
        return builder;
    }

    private void validateSemantics(
            @NonNull final HandleContext context,
            @NonNull final WritableAccountStore accountStore,
            @NonNull final CryptoUpdateTransactionBody op,
            @NonNull Builder builder) {
        final var expiryValidator = context.expiryValidator();
        final var builderAccount = builder.build();

        final var target = op.accountIDToUpdateOrThrow();
        final var optionalAccount = accountStore.get(target);
        validateTrue(optionalAccount.isPresent(), INVALID_ACCOUNT_ID);

        final var updateAccount = optionalAccount.get();
        validateTrue(!updateAccount.smartContract(), INVALID_ACCOUNT_ID);

        final var tokensConfig = context.getConfiguration().getConfigData(TokensConfig.class);
        final var autoRenewConfig = context.getConfiguration().getConfigData(AutoRenewConfig.class);
        final var ledgerConfig = context.getConfiguration().getConfigData(LedgerConfig.class);
        final var entitiesConfig = context.getConfiguration().getConfigData(EntitiesConfig.class);

        final var currentMetadata = new ExpiryMeta(
                updateAccount.expiry(), updateAccount.autoRenewSecs(), updateAccount.autoRenewAccountNumber());
        final var updateMeta = new ExpiryMeta(
                op.expirationTime().seconds(), op.autoRenewPeriod().seconds(), NA);
        final var resolvedMeta = context.expiryValidator().resolveUpdateAttempt(currentMetadata, updateMeta);

        validateTrue(
                expiryValidator.isDetached(
                                updateAccount,
                                autoRenewConfig.isAutoRenewEnabled(),
                                autoRenewConfig.expireContracts(),
                                autoRenewConfig.expireAccounts())
                        && builderAccount.expiry() != 0,
                ACCOUNT_EXPIRED_AND_PENDING_REMOVAL);

        if (builderAccount.expiry() != 0) {
            validateFalse(op.expirationTime().seconds() < updateAccount.expiry(), EXPIRATION_REDUCTION_NOT_ALLOWED);
        }

        if (builderAccount.maxAutoAssociations() != 0) {
            final long newMax = builderAccount.maxAutoAssociations();
            validateFalse(
                    newMax < optionalAccount.get().usedAutoAssociations(),
                    EXISTING_AUTOMATIC_ASSOCIATIONS_EXCEED_GIVEN_LIMIT);
            validateFalse(
                    newMax > ledgerConfig.maxAutoAssociations(),
                    REQUESTED_NUM_AUTOMATIC_ASSOCIATIONS_EXCEEDS_ASSOCIATION_LIMIT);
            validateFalse(
                    entitiesConfig.limitTokenAssociations() && newMax > tokensConfig.maxPerAccount(),
                    REQUESTED_NUM_AUTOMATIC_ASSOCIATIONS_EXCEEDS_ASSOCIATION_LIMIT);
        }
    }
}
