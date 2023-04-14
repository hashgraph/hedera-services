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

import static com.hedera.hapi.node.base.ResponseCodeEnum.EXPIRATION_REDUCTION_NOT_ALLOWED;
import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_AUTORENEW_ACCOUNT;
import static com.hedera.node.app.spi.workflows.HandleException.validateFalse;
import static com.hedera.node.app.spi.workflows.HandleException.validateTrue;

import com.hedera.node.app.service.mono.config.HederaNumbers;
import com.hedera.node.app.service.mono.store.models.Id;
import com.hedera.node.app.spi.validation.AttributeValidator;
import com.hedera.node.app.spi.validation.ExpiryMeta;
import com.hedera.node.app.spi.validation.ExpiryValidator;
import com.hedera.node.app.spi.workflows.HandleException;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.LongSupplier;

public class StandardizedExpiryValidator implements ExpiryValidator {
    private final Consumer<Id> idValidator;
    private final LongSupplier consensusSecondNow;
    private final AttributeValidator attributeValidator;
    private final HederaNumbers numbers;

    public StandardizedExpiryValidator(
            @NonNull final Consumer<Id> idValidator,
            @NonNull final AttributeValidator attributeValidator,
            @NonNull final LongSupplier consensusSecondNow,
            @NonNull final HederaNumbers numbers) {
        this.attributeValidator = Objects.requireNonNull(attributeValidator);
        this.consensusSecondNow = Objects.requireNonNull(consensusSecondNow);
        this.numbers = Objects.requireNonNull(numbers);
        this.idValidator = Objects.requireNonNull(idValidator);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ExpiryMeta resolveCreationAttempt(final boolean entityCanSelfFundRenewal, final ExpiryMeta creationMeta) {
        if (creationMeta.hasAutoRenewNum()) {
            validateAutoRenewAccount(
                    creationMeta.autoRenewShard(), creationMeta.autoRenewRealm(), creationMeta.autoRenewNum());
        }

        final var thisSecond = consensusSecondNow.getAsLong();
        long effectiveExpiry = creationMeta.expiry();
        // We prioritize the expiry implied by auto-renew configuration, if it is present
        // and complete (meaning either both auto-renew period and auto-renew account are
        // present; or auto-renew period is present, and the entity can self-fund)
        if (hasCompleteAutoRenewSpec(entityCanSelfFundRenewal, creationMeta)) {
            effectiveExpiry = thisSecond + creationMeta.autoRenewPeriod();
        }
        attributeValidator.validateExpiry(effectiveExpiry);

        // Even if the effective expiry is valid, we still also require any explicit auto-renew period to be valid
        if (creationMeta.hasAutoRenewPeriod()) {
            attributeValidator.validateAutoRenewPeriod(creationMeta.autoRenewPeriod());
        }
        return new ExpiryMeta(effectiveExpiry, creationMeta.autoRenewPeriod(), creationMeta.autoRenewNum());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ExpiryMeta resolveUpdateAttempt(final ExpiryMeta currentMeta, final ExpiryMeta updateMeta) {
        if (updateMeta.hasAutoRenewNum()) {
            validateAutoRenewAccount(
                    updateMeta.autoRenewShard(), updateMeta.autoRenewRealm(), updateMeta.autoRenewNum());
        }

        var resolvedExpiry = currentMeta.expiry();
        if (updateMeta.hasExplicitExpiry()) {
            validateFalse(updateMeta.expiry() < currentMeta.expiry(), EXPIRATION_REDUCTION_NOT_ALLOWED);
            attributeValidator.validateExpiry(updateMeta.expiry());
            resolvedExpiry = updateMeta.expiry();
        }

        var resolvedAutoRenewPeriod = currentMeta.autoRenewPeriod();
        if (updateMeta.hasAutoRenewPeriod()) {
            attributeValidator.validateAutoRenewPeriod(updateMeta.autoRenewPeriod());
            resolvedAutoRenewPeriod = updateMeta.autoRenewPeriod();
        }

        var resolvedAutoRenewNum = currentMeta.autoRenewNum();
        if (updateMeta.hasAutoRenewNum()) {
            // If just now adding an auto-renew account, confirm the resolved auto-renew period is valid
            if (!currentMeta.hasAutoRenewNum()) {
                attributeValidator.validateAutoRenewPeriod(resolvedAutoRenewPeriod);
            }
            resolvedAutoRenewNum = updateMeta.autoRenewNum();
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
     * @param shard the account shard to validate
     * @param realm the account realm to validate
     * @param num the account number to validate
     * @throws HandleException if the account number is invalid
     */
    private void validateAutoRenewAccount(final long shard, final long realm, final long num) {
        validateTrue(shard == numbers.shard() && realm == numbers.realm(), INVALID_AUTORENEW_ACCOUNT);
        if (num == 0L) {
            // 0L is a sentinel number that says to remove the current auto-renew account
            return;
        }
        final var autoRenewId = new Id(numbers.shard(), numbers.realm(), num);
        idValidator.accept(autoRenewId);
    }
}
