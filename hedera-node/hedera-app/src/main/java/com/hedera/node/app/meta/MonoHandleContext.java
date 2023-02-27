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

package com.hedera.node.app.meta;

import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;

import com.hedera.node.app.service.evm.exceptions.InvalidTransactionException;
import com.hedera.node.app.service.mono.context.TransactionContext;
import com.hedera.node.app.service.mono.ledger.ids.EntityIdSource;
import com.hedera.node.app.service.mono.txns.validation.OptionValidator;
import com.hedera.node.app.spi.exceptions.HandleStatusException;
import com.hedera.node.app.spi.meta.HandleContext;
import com.hedera.node.app.spi.validation.AttributeValidator;
import com.hedera.node.app.spi.validation.ExpiryValidator;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.Key;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.time.Instant;
import java.util.Objects;
import java.util.function.LongSupplier;
import javax.inject.Singleton;

/**
 * A {@link HandleContext} implementation that primarily uses adapters of {@code mono-service}
 * utilities. These adapters will either be replaced with new implementations; or refactored
 * and ported from {@code mono-service} into {@code hedera-app} at a later time.
 */
@Singleton
public class MonoHandleContext implements HandleContext {
    private static final AccountID PLACEHOLDER_ID = AccountID.getDefaultInstance();

    private final LongSupplier nums;
    private final ExpiryValidator expiryValidator;
    private final TransactionContext txnCtx;
    private final AttributeValidator attributeValidator;

    public MonoHandleContext(
            @NonNull final EntityIdSource ids,
            @NonNull final ExpiryValidator expiryValidator,
            @NonNull final OptionValidator optionValidator,
            @NonNull final TransactionContext txnCtx) {
        Objects.requireNonNull(ids);
        this.nums = () -> ids.newAccountId(PLACEHOLDER_ID).getAccountNum();
        this.txnCtx = Objects.requireNonNull(txnCtx);
        this.expiryValidator = Objects.requireNonNull(expiryValidator);
        this.attributeValidator = new MonoAttributeValidator(Objects.requireNonNull(optionValidator));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Instant consensusNow() {
        return txnCtx.consensusTime();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public LongSupplier newEntityNumSupplier() {
        return nums;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public AttributeValidator attributeValidator() {
        return attributeValidator;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ExpiryValidator expiryValidator() {
        return expiryValidator;
    }

    private static class MonoAttributeValidator implements AttributeValidator {
        private final OptionValidator optionValidator;

        private MonoAttributeValidator(final OptionValidator optionValidator) {
            this.optionValidator = optionValidator;
        }

        @Override
        public void validateKey(final Key key) {
            try {
                optionValidator.attemptDecodeOrThrow(key);
            } catch (final InvalidTransactionException e) {
                throw new HandleStatusException(e.getResponseCode());
            }
        }

        @Override
        public void validateMemo(final String memo) {
            final var validity = optionValidator.memoCheck(memo);
            if (validity != OK) {
                throw new HandleStatusException(validity);
            }
        }
    }
}
