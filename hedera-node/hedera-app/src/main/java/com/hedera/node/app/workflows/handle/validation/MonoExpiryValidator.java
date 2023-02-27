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
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_EXPIRATION_TIME;

import com.hedera.node.app.service.mono.context.TransactionContext;
import com.hedera.node.app.service.mono.txns.validation.OptionValidator;
import com.hedera.node.app.spi.exceptions.HandleStatusException;
import com.hedera.node.app.spi.validation.ExpiryMeta;
import com.hedera.node.app.spi.validation.ExpiryValidator;
import javax.inject.Inject;

/**
 * An implementation of {@link ExpiryValidator} that encapsulates the
 * current policies of the Hedera network with the help of a {@code mono-service}
 * {@link OptionValidator}.
 */
public class MonoExpiryValidator implements ExpiryValidator {
    private final OptionValidator validator;
    private final TransactionContext txnCtx;

    @Inject
    public MonoExpiryValidator(final OptionValidator validator, final TransactionContext txnCtx) {
        this.validator = validator;
        this.txnCtx = txnCtx;
    }

    @Override
    public void validateCreationAttempt(final boolean entityCanSelfFundRenewal, final ExpiryMeta creationMetadata) {
        final var thisSecond = txnCtx.consensusTime().getEpochSecond();

        long effectiveExpiry = creationMetadata.expiry();
        // We prioritize the expiry implied by auto-renew configuration, if it is present
        // and complete (meaning either both auto-renew period and auto-renew account are
        // present; or auto-renew period is present, and the entity can self-fund)
        if (hasCompleteAutoRenewSpec(entityCanSelfFundRenewal, creationMetadata)) {
            effectiveExpiry = thisSecond + creationMetadata.autoRenewPeriod();
        }
        if (!validator.isValidExpiry(effectiveExpiry)) {
            throw new HandleStatusException(INVALID_EXPIRATION_TIME);
        }

        // Even if the effective expiry is valid, we still also require any explicit
        // auto-renew period to be valid
        if (creationMetadata.hasAutoRenewPeriod()
                && !validator.isValidAutoRenewPeriod(creationMetadata.autoRenewPeriod())) {
            throw new HandleStatusException(AUTORENEW_DURATION_NOT_IN_RANGE);
        }
    }

    @Override
    public ExpiryMeta resolveUpdateAttempt(final ExpiryMeta currentMetadata, final ExpiryMeta updateMetadata) {
        var resolvedExpiry = currentMetadata.expiry();
        if (updateMetadata.hasExplicitExpiry()) {
            if (updateMetadata.expiry() < currentMetadata.expiry()) {
                throw new HandleStatusException(EXPIRATION_REDUCTION_NOT_ALLOWED);
            } else if (!validator.isValidExpiry(updateMetadata.expiry())) {
                throw new HandleStatusException(INVALID_EXPIRATION_TIME);
            } else {
                resolvedExpiry = updateMetadata.expiry();
            }
        }

        var resolvedAutoRenewPeriod = currentMetadata.autoRenewPeriod();
        if (updateMetadata.hasAutoRenewPeriod()) {
            if (!validator.isValidAutoRenewPeriod(updateMetadata.autoRenewPeriod())) {
                throw new HandleStatusException(AUTORENEW_DURATION_NOT_IN_RANGE);
            }
            resolvedAutoRenewPeriod = updateMetadata.autoRenewPeriod();
        }

        var resolvedAutoRenewNum = currentMetadata.autoRenewNum();
        if (updateMetadata.hasAutoRenewNum()) {
            if (!currentMetadata.hasAutoRenewNum() && !validator.isValidAutoRenewPeriod(resolvedAutoRenewPeriod)) {
                throw new HandleStatusException(AUTORENEW_DURATION_NOT_IN_RANGE);
            } else {
                resolvedAutoRenewNum = updateMetadata.autoRenewNum();
            }
        }
        return new ExpiryMeta(resolvedExpiry, resolvedAutoRenewPeriod, resolvedAutoRenewNum);
    }

    private boolean hasCompleteAutoRenewSpec(
            final boolean entityCanSelfFundRenewal, final ExpiryMeta creationMetadata) {
        return creationMetadata.hasFullAutoRenewSpec()
                || (!creationMetadata.hasExplicitExpiry() && entityCanSelfFundRenewal);
    }
}
