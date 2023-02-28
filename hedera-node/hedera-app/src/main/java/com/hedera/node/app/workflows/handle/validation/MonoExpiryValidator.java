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

package com.hedera.node.app.workflows.handle.validation;

import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.AUTORENEW_DURATION_NOT_IN_RANGE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.EXPIRATION_REDUCTION_NOT_ALLOWED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_AUTORENEW_ACCOUNT;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_EXPIRATION_TIME;

import com.hedera.node.app.service.evm.exceptions.InvalidTransactionException;
import com.hedera.node.app.service.mono.config.HederaNumbers;
import com.hedera.node.app.service.mono.context.TransactionContext;
import com.hedera.node.app.service.mono.store.AccountStore;
import com.hedera.node.app.service.mono.store.models.Id;
import com.hedera.node.app.service.mono.txns.validation.OptionValidator;
import com.hedera.node.app.spi.exceptions.HandleStatusException;
import com.hedera.node.app.spi.validation.ExpiryMeta;
import com.hedera.node.app.spi.validation.ExpiryValidator;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Objects;
import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * An implementation of {@link ExpiryValidator} that encapsulates the current policies
 * of the Hedera network with the help of a {@code mono-service} {@link OptionValidator}.
 */
@Singleton
public class MonoExpiryValidator implements ExpiryValidator {
    private final AccountStore accountStore;
    private final OptionValidator validator;
    private final TransactionContext txnCtx;
    private final HederaNumbers numbers;

    @Inject
    public MonoExpiryValidator(
            @NonNull final AccountStore accountStore,
            @NonNull final OptionValidator validator,
            @NonNull final TransactionContext txnCtx,
            @NonNull final HederaNumbers numbers) {
        this.accountStore = Objects.requireNonNull(accountStore);
        this.validator = Objects.requireNonNull(validator);
        this.txnCtx = Objects.requireNonNull(txnCtx);
        this.numbers = Objects.requireNonNull(numbers);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void validateCreationAttempt(final boolean entityCanSelfFundRenewal, final ExpiryMeta creationMeta) {
        if (creationMeta.hasAutoRenewNum()) {
            validateAutoRenewAccount(creationMeta.autoRenewNum());
        }

        final var thisSecond = txnCtx.consensusTime().getEpochSecond();

        long effectiveExpiry = creationMeta.expiry();
        // We prioritize the expiry implied by auto-renew configuration, if it is present
        // and complete (meaning either both auto-renew period and auto-renew account are
        // present; or auto-renew period is present, and the entity can self-fund)
        if (hasCompleteAutoRenewSpec(entityCanSelfFundRenewal, creationMeta)) {
            effectiveExpiry = thisSecond + creationMeta.autoRenewPeriod();
        }
        if (!validator.isValidExpiry(effectiveExpiry)) {
            throw new HandleStatusException(INVALID_EXPIRATION_TIME);
        }

        // Even if the effective expiry is valid, we still also require any explicit
        // auto-renew period to be valid
        if (creationMeta.hasAutoRenewPeriod() && !validator.isValidAutoRenewPeriod(creationMeta.autoRenewPeriod())) {
            throw new HandleStatusException(AUTORENEW_DURATION_NOT_IN_RANGE);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ExpiryMeta resolveUpdateAttempt(final ExpiryMeta currentMeta, final ExpiryMeta updateMeta) {
        if (updateMeta.hasAutoRenewNum()) {
            validateAutoRenewAccount(updateMeta.autoRenewNum());
        }

        var resolvedExpiry = currentMeta.expiry();
        if (updateMeta.hasExplicitExpiry()) {
            if (updateMeta.expiry() < currentMeta.expiry()) {
                throw new HandleStatusException(EXPIRATION_REDUCTION_NOT_ALLOWED);
            } else if (!validator.isValidExpiry(updateMeta.expiry())) {
                throw new HandleStatusException(INVALID_EXPIRATION_TIME);
            } else {
                resolvedExpiry = updateMeta.expiry();
            }
        }

        var resolvedAutoRenewPeriod = currentMeta.autoRenewPeriod();
        if (updateMeta.hasAutoRenewPeriod()) {
            if (!validator.isValidAutoRenewPeriod(updateMeta.autoRenewPeriod())) {
                throw new HandleStatusException(AUTORENEW_DURATION_NOT_IN_RANGE);
            }
            resolvedAutoRenewPeriod = updateMeta.autoRenewPeriod();
        }

        var resolvedAutoRenewNum = currentMeta.autoRenewNum();
        if (updateMeta.hasAutoRenewNum()) {
            if (!currentMeta.hasAutoRenewNum() && !validator.isValidAutoRenewPeriod(resolvedAutoRenewPeriod)) {
                throw new HandleStatusException(AUTORENEW_DURATION_NOT_IN_RANGE);
            } else {
                resolvedAutoRenewNum = updateMeta.autoRenewNum();
            }
        }
        return new ExpiryMeta(resolvedExpiry, resolvedAutoRenewPeriod, resolvedAutoRenewNum);
    }

    /**
     * Helper to check if an entity with the given metadata has a completely specified
     * auto-renew configuration. This is true if either the {@link ExpiryMeta} includes
     * both an auto-renew period and an auto-renew account; or if the {@link ExpiryMeta}
     * includes only an auto-renew period, and the entity can self-fund its auto-renewal.
     *
     * @param entityCanSelfFundRenewal whether the entity can self-fund its auto-renewal
     * @param creationMetadata the entity's proposed {@link ExpiryMeta}
     * @return whether the entity has a complete auto-renew configuration
     */
    private boolean hasCompleteAutoRenewSpec(
            final boolean entityCanSelfFundRenewal, final ExpiryMeta creationMetadata) {
        return creationMetadata.hasFullAutoRenewSpec()
                || (!creationMetadata.hasExplicitExpiry() && entityCanSelfFundRenewal);
    }

    /**
     * Helper to validate that the given account number is a valid auto-renew account.
     *
     * @param autoRenewNum the account number to validate
     * @throws HandleStatusException if the account number is invalid
     */
    private void validateAutoRenewAccount(final long autoRenewNum) {
        final var autoRenewId = new Id(numbers.shard(), numbers.realm(), autoRenewNum);
        try {
            accountStore.loadAccountOrFailWith(autoRenewId, INVALID_AUTORENEW_ACCOUNT);
        } catch (final InvalidTransactionException e) {
            throw new HandleStatusException(e.getResponseCode());
        }
    }
}
