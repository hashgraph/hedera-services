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

package com.hedera.node.app.service.contract.impl.handlers;

import static com.hedera.hapi.node.base.ResponseCodeEnum.CONTRACT_EXPIRED_AND_PENDING_REMOVAL;
import static com.hedera.hapi.node.base.ResponseCodeEnum.EXISTING_AUTOMATIC_ASSOCIATIONS_EXCEED_GIVEN_LIMIT;
import static com.hedera.hapi.node.base.ResponseCodeEnum.EXPIRATION_REDUCTION_NOT_ALLOWED;
import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_ADMIN_KEY;
import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_AUTORENEW_ACCOUNT;
import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_CONTRACT_ID;
import static com.hedera.hapi.node.base.ResponseCodeEnum.MODIFYING_IMMUTABLE_CONTRACT;
import static com.hedera.hapi.node.base.ResponseCodeEnum.REQUESTED_NUM_AUTOMATIC_ASSOCIATIONS_EXCEEDS_ASSOCIATION_LIMIT;
import static com.hedera.node.app.service.token.api.AccountSummariesApi.SENTINEL_ACCOUNT_ID;
import static com.hedera.node.app.spi.HapiUtils.EMPTY_KEY_LIST;
import static com.hedera.node.app.spi.validation.ExpiryMeta.NA;
import static com.hedera.node.app.spi.workflows.HandleException.validateFalse;
import static com.hedera.node.app.spi.workflows.HandleException.validateTrue;
import static java.util.Objects.requireNonNull;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.ContractID;
import com.hedera.hapi.node.base.HederaFunctionality;
import com.hedera.hapi.node.base.Key.KeyOneOfType;
import com.hedera.hapi.node.contract.ContractUpdateTransactionBody;
import com.hedera.hapi.node.state.token.Account;
import com.hedera.node.app.service.token.ReadableAccountStore;
import com.hedera.node.app.service.token.api.TokenServiceApi;
import com.hedera.node.app.spi.key.KeyUtils;
import com.hedera.node.app.spi.validation.ExpiryMeta;
import com.hedera.node.app.spi.workflows.HandleContext;
import com.hedera.node.app.spi.workflows.HandleException;
import com.hedera.node.app.spi.workflows.PreCheckException;
import com.hedera.node.app.spi.workflows.PreHandleContext;
import com.hedera.node.app.spi.workflows.TransactionHandler;
import com.hedera.node.config.data.EntitiesConfig;
import com.hedera.node.config.data.LedgerConfig;
import com.hedera.node.config.data.TokensConfig;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Optional;
import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * This class contains all workflow-related functionality regarding {@link HederaFunctionality#CONTRACT_UPDATE}.
 */
@Singleton
public class ContractUpdateHandler implements TransactionHandler {

    @Inject
    public ContractUpdateHandler() {
        // Exists for injection
    }

    @Override
    public void preHandle(@NonNull final PreHandleContext context) throws PreCheckException {
        requireNonNull(context);
        final var op = context.body().contractUpdateInstanceOrThrow();

        if (isAdminSigRequired(op)) {
            context.requireKeyOrThrow(op.contractIDOrElse(ContractID.DEFAULT), INVALID_CONTRACT_ID);
        }
        if (hasCryptoAdminKey(op)) {
            context.requireKey(op.adminKeyOrThrow());
        }
        if (op.hasAutoRenewAccountId() && !op.autoRenewAccountIdOrThrow().equals(AccountID.DEFAULT)) {
            context.requireKeyOrThrow(op.autoRenewAccountIdOrThrow(), INVALID_AUTORENEW_ACCOUNT);
        }
    }

    private boolean isAdminSigRequired(final ContractUpdateTransactionBody op) {
        return !op.hasExpirationTime()
                || hasCryptoAdminKey(op)
                || op.hasProxyAccountID()
                || op.hasAutoRenewPeriod()
                || op.hasFileID()
                || op.memoOrElse("").length() > 0;
    }

    private boolean hasCryptoAdminKey(final ContractUpdateTransactionBody op) {
        return op.hasAdminKey() && !op.adminKeyOrThrow().hasContractID();
    }

    @Override
    public void handle(@NonNull final HandleContext context) throws HandleException {
        final var txn = requireNonNull(context).body();
        final var op = txn.contractUpdateInstanceOrThrow();
        final var target = op.contractIDOrThrow();

        final var accountStore = context.readableStore(ReadableAccountStore.class);
        final var toBeUpdated = accountStore.getContractById(target);
        validateSemantics(toBeUpdated, context, op);
        final var changed = update(toBeUpdated, context, op);

        context.serviceApi(TokenServiceApi.class).updateContract(changed);
    }

    public Account update(
            @NonNull final Account contract,
            @NonNull final HandleContext context,
            @NonNull final ContractUpdateTransactionBody op) {
        final var builder = contract.copyBuilder();
        if (op.hasAdminKey()) {
            if (EMPTY_KEY_LIST.equals(op.adminKey())) {
                builder.key(contract.key());
            } else {
                builder.key(op.adminKey());
            }
        }
        if (op.hasExpirationTime()) {
            if (contract.expiredAndPendingRemoval()) {
                builder.expiredAndPendingRemoval(false);
            }
            builder.expirationSecond(op.expirationTime().seconds());
        }
        if (op.hasAutoRenewPeriod()) {
            builder.autoRenewSeconds(op.autoRenewPeriod().seconds());
        }
        if (affectsMemo(op)) {
            final var newMemo = op.hasMemoWrapper() ? op.memoWrapper() : op.memo();
            context.attributeValidator().validateMemo(newMemo);
            builder.memo(newMemo);
        }
        if (op.hasStakedAccountId()) {
            if (SENTINEL_ACCOUNT_ID.equals(op.stakedAccountId())) {
                builder.stakedAccountId((AccountID) null);
            } else {
                builder.stakedAccountId(op.stakedAccountId());
            }
        } else if (op.hasStakedNodeId()) {
            builder.stakedNodeId(op.stakedNodeId());
        }
        if (op.hasDeclineReward()) {
            builder.declineReward(op.declineReward());
        }
        if (op.hasAutoRenewAccountId()) {
            builder.autoRenewAccountId(op.autoRenewAccountId());
        }
        if (op.hasMaxAutomaticTokenAssociations()) {
            final var ledgerConfig = context.configuration().getConfigData(LedgerConfig.class);
            final var entitiesConfig = context.configuration().getConfigData(EntitiesConfig.class);
            final var tokensConfig = context.configuration().getConfigData(TokensConfig.class);

            validateFalse(
                    op.maxAutomaticTokenAssociations() > ledgerConfig.maxAutoAssociations(),
                    REQUESTED_NUM_AUTOMATIC_ASSOCIATIONS_EXCEEDS_ASSOCIATION_LIMIT);

            final long newMax = op.maxAutomaticTokenAssociations();
            validateFalse(newMax < contract.maxAutoAssociations(), EXISTING_AUTOMATIC_ASSOCIATIONS_EXCEED_GIVEN_LIMIT);
            validateFalse(
                    entitiesConfig.limitTokenAssociations() && newMax > tokensConfig.maxPerAccount(),
                    REQUESTED_NUM_AUTOMATIC_ASSOCIATIONS_EXCEEDS_ASSOCIATION_LIMIT);

            builder.maxAutoAssociations(op.maxAutomaticTokenAssociations());
        }
        return builder.build();
    }

    private void validateSemantics(Account contract, HandleContext context, ContractUpdateTransactionBody op) {
        validateTrue(contract != null, INVALID_CONTRACT_ID);

        if (op.hasAdminKey()) {
            boolean keyNotSentinel = !EMPTY_KEY_LIST.equals(op.adminKey());
            boolean keyIsUnset = op.adminKey().key().kind() == KeyOneOfType.UNSET;
            boolean keyIsNotValid = !KeyUtils.isValid(op.adminKey());
            validateFalse(keyNotSentinel && (keyIsUnset || keyIsNotValid), INVALID_ADMIN_KEY);
        }

        if (op.hasExpirationTime()) {
            try {
                context.attributeValidator().validateExpiry(op.expirationTime().seconds());
            } catch (HandleException e) {
                validateFalse(contract.expiredAndPendingRemoval(), CONTRACT_EXPIRED_AND_PENDING_REMOVAL);
                throw e;
            }
        }

        validateFalse(!onlyAffectsExpiry(op) && !isMutable(contract), MODIFYING_IMMUTABLE_CONTRACT);
        validateFalse(reducesExpiry(op, contract.expirationSecond()), EXPIRATION_REDUCTION_NOT_ALLOWED);

        // validate expiry metadata
        final var currentMetadata =
                new ExpiryMeta(contract.expirationSecond(), contract.autoRenewSeconds(), contract.autoRenewAccountId());
        final var updateMeta = new ExpiryMeta(
                op.hasExpirationTime() ? op.expirationTime().seconds() : NA,
                op.hasAutoRenewPeriod() ? op.autoRenewPeriod().seconds() : NA,
                null);
        context.expiryValidator().resolveUpdateAttempt(currentMetadata, updateMeta);
    }

    boolean onlyAffectsExpiry(ContractUpdateTransactionBody op) {
        return !(op.hasProxyAccountID()
                || op.hasFileID()
                || affectsMemo(op)
                || op.hasAutoRenewPeriod()
                || op.hasAdminKey());
    }

    boolean affectsMemo(ContractUpdateTransactionBody op) {
        return op.hasMemoWrapper() || (op.memo() != null && op.memo().length() > 0);
    }

    boolean isMutable(final Account contract) {
        return Optional.ofNullable(contract.key())
                .map(key -> !key.hasContractID())
                .orElse(false);
    }

    private boolean reducesExpiry(ContractUpdateTransactionBody op, long curExpiry) {
        return op.hasExpirationTime() && op.expirationTime().seconds() < curExpiry;
    }
}
