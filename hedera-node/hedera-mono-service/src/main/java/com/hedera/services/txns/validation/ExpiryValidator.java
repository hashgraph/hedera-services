/*
 * Copyright (C) 2022 Hedera Hashgraph, LLC
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
package com.hedera.services.txns.validation;

import static com.hedera.services.txns.validation.SummarizedExpiryMeta.EXPIRY_REDUCTION_SUMMARY;
import static com.hedera.services.txns.validation.SummarizedExpiryMeta.INVALID_EXPIRY_SUMMARY;
import static com.hedera.services.txns.validation.SummarizedExpiryMeta.INVALID_PERIOD_SUMMARY;

import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * Provides helpers to summarize the validity and net consequence of creating and updating an
 * entity's expiration related metadata (that is, its expiration, auto-renew period, and auto-renew
 * account number).
 */
@Singleton
public class ExpiryValidator {
    private final OptionValidator validator;

    @Inject
    public ExpiryValidator(OptionValidator validator) {
        this.validator = validator;
    }

    /**
     * Summarizes the validity and net consequences of an attempt to create an entity with the
     * requested expiration metadata, at the given time.
     *
     * @param now the effective consensus time
     * @param requestedMeta the new entity's desired metadata
     * @param canSelfFundRenewals whether the new entity can self-fund renewals
     * @return a summary of the metadata validity and net effect
     */
    public SummarizedExpiryMeta summarizeCreationAttempt(
            final long now, final boolean canSelfFundRenewals, final ExpiryMeta requestedMeta) {
        // If the expiry is not set explicitly, it may be possible to infer it from the auto-renew
        // period
        long resolvedExpiry = requestedMeta.expiry();
        if (requestedMeta.hasFullAutoRenewSpec()
                || (!requestedMeta.hasExplicitExpiry() && canSelfFundRenewals)) {
            resolvedExpiry = now + requestedMeta.autoRenewPeriod();
        }
        if (!validator.isValidExpiry(resolvedExpiry)) {
            return INVALID_EXPIRY_SUMMARY;
        }
        // We always require a valid auto-renew period if set
        if (requestedMeta.hasAutoRenewPeriod()
                && !validator.isValidAutoRenewPeriod(requestedMeta.autoRenewPeriod())) {
            return INVALID_PERIOD_SUMMARY;
        }
        // The auto-renew account is already validated while enforcing signing requirements,
        // so nothing more to do with it here
        return SummarizedExpiryMeta.forValid(
                new ExpiryMeta(
                        resolvedExpiry,
                        requestedMeta.autoRenewPeriod(),
                        requestedMeta.autoRenewNum()));
    }

    /**
     * Summarizes the validity and net consequences of an attempt to update an entity with the
     * requested expiration metadata, relative to the given current metadata.
     *
     * @param currentMeta the current expiry metadata
     * @param requestedMeta the entity's desired metadata update
     * @return a summary of the metadata update validity and net effect
     */
    public SummarizedExpiryMeta summarizeUpdateAttempt(
            final ExpiryMeta currentMeta, final ExpiryMeta requestedMeta) {
        var resolvedExpiry = currentMeta.expiry();
        if (requestedMeta.hasExplicitExpiry()) {
            if (requestedMeta.expiry() < currentMeta.expiry()) {
                return EXPIRY_REDUCTION_SUMMARY;
            } else if (!validator.isValidExpiry(requestedMeta.expiry())) {
                return INVALID_EXPIRY_SUMMARY;
            } else {
                resolvedExpiry = requestedMeta.expiry();
            }
        }

        var resolvedAutoRenewPeriod = currentMeta.autoRenewPeriod();
        if (requestedMeta.hasAutoRenewPeriod()) {
            if (!validator.isValidAutoRenewPeriod(requestedMeta.autoRenewPeriod())) {
                return INVALID_PERIOD_SUMMARY;
            }
            resolvedAutoRenewPeriod = requestedMeta.autoRenewPeriod();
        }

        var resolvedAutoRenewNum = currentMeta.autoRenewNum();
        if (requestedMeta.hasAutoRenewNum()) {
            if (!currentMeta.hasAutoRenewNum()
                    && !validator.isValidAutoRenewPeriod(resolvedAutoRenewPeriod)) {
                return INVALID_PERIOD_SUMMARY;
            } else {
                resolvedAutoRenewNum = requestedMeta.autoRenewNum();
            }
        }
        final var resolvedMeta =
                new ExpiryMeta(resolvedExpiry, resolvedAutoRenewPeriod, resolvedAutoRenewNum);
        return SummarizedExpiryMeta.forValid(resolvedMeta);
    }
}
