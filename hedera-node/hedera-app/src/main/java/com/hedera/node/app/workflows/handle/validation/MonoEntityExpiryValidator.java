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

import static com.hedera.node.app.spi.validation.UpdateEntityExpiryMetadata.invalidMetadata;
import static com.hedera.node.app.spi.validation.UpdateEntityExpiryMetadata.validMetadata;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.AUTORENEW_DURATION_NOT_IN_RANGE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.EXPIRATION_REDUCTION_NOT_ALLOWED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_EXPIRATION_TIME;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;

import com.hedera.node.app.service.mono.context.TransactionContext;
import com.hedera.node.app.service.mono.txns.validation.OptionValidator;
import com.hedera.node.app.spi.validation.EntityExpiryMetadata;
import com.hedera.node.app.spi.validation.EntityExpiryValidator;
import com.hedera.node.app.spi.validation.UpdateEntityExpiryMetadata;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import javax.inject.Inject;

/**
 * An implementation of {@link EntityExpiryValidator} that encapsulates the
 * current policies of the Hedera network with the help of a {@code mono-service}
 * {@link OptionValidator}.
 */
public class MonoEntityExpiryValidator implements EntityExpiryValidator {
    private final OptionValidator validator;
    private final TransactionContext txnCtx;

    @Inject
    public MonoEntityExpiryValidator(final OptionValidator validator, final TransactionContext txnCtx) {
        this.validator = validator;
        this.txnCtx = txnCtx;
    }

    @Override
    public ResponseCodeEnum validateCreationAttempt(
            final boolean entityCanSelfFundRenewal, final EntityExpiryMetadata creationMetadata) {
        final var thisSecond = txnCtx.consensusTime().getEpochSecond();

        // If the expiry is not set explicitly, we can try to infer it from the auto-renew period
        // (not possible if, for example, there is no auto-renew account and no self-funding)
        long effectiveExpiry = creationMetadata.expiry();
        if (creationMetadata.hasFullAutoRenewSpec()
                || (!creationMetadata.hasExplicitExpiry() && entityCanSelfFundRenewal)) {
            effectiveExpiry = thisSecond + creationMetadata.autoRenewPeriod();
        }
        if (!validator.isValidExpiry(effectiveExpiry)) {
            return INVALID_EXPIRATION_TIME;
        }

        // Even if the effective expiry is valid, we still also require any explicit
        // auto-renew period to be valid
        if (creationMetadata.hasAutoRenewPeriod()
                && !validator.isValidAutoRenewPeriod(creationMetadata.autoRenewPeriod())) {
            return AUTORENEW_DURATION_NOT_IN_RANGE;
        }
        return OK;
    }

    @Override
    public UpdateEntityExpiryMetadata resolveAndValidateUpdateAttempt(
            final EntityExpiryMetadata currentMetadata, final EntityExpiryMetadata updateMetadata) {
        var resolvedExpiry = currentMetadata.expiry();
        if (updateMetadata.hasExplicitExpiry()) {
            if (updateMetadata.expiry() < currentMetadata.expiry()) {
                return invalidMetadata(EXPIRATION_REDUCTION_NOT_ALLOWED);
            } else if (!validator.isValidExpiry(updateMetadata.expiry())) {
                return invalidMetadata(INVALID_EXPIRATION_TIME);
            } else {
                resolvedExpiry = updateMetadata.expiry();
            }
        }

        var resolvedAutoRenewPeriod = currentMetadata.autoRenewPeriod();
        if (updateMetadata.hasAutoRenewPeriod()) {
            if (!validator.isValidAutoRenewPeriod(updateMetadata.autoRenewPeriod())) {
                return invalidMetadata(AUTORENEW_DURATION_NOT_IN_RANGE);
            }
            resolvedAutoRenewPeriod = updateMetadata.autoRenewPeriod();
        }

        var resolvedAutoRenewNum = currentMetadata.autoRenewNum();
        if (updateMetadata.hasAutoRenewNum()) {
            if (!currentMetadata.hasAutoRenewNum() && !validator.isValidAutoRenewPeriod(resolvedAutoRenewPeriod)) {
                return invalidMetadata(AUTORENEW_DURATION_NOT_IN_RANGE);
            } else {
                resolvedAutoRenewNum = updateMetadata.autoRenewNum();
            }
        }
        final var resolvedMeta =
                new EntityExpiryMetadata(resolvedExpiry, resolvedAutoRenewPeriod, resolvedAutoRenewNum);
        return validMetadata(resolvedMeta);
    }
}
