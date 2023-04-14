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

import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_AUTORENEW_ACCOUNT;

import com.hedera.node.app.service.evm.exceptions.InvalidTransactionException;
import com.hedera.node.app.service.mono.config.HederaNumbers;
import com.hedera.node.app.service.mono.pbj.PbjConverter;
import com.hedera.node.app.service.mono.store.AccountStore;
import com.hedera.node.app.service.mono.txns.validation.OptionValidator;
import com.hedera.node.app.spi.exceptions.HandleException;
import com.hedera.node.app.spi.validation.AttributeValidator;
import com.hedera.node.app.spi.validation.ExpiryValidator;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.function.LongSupplier;
import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * An implementation of {@link ExpiryValidator} that encapsulates the current policies
 * of the Hedera network with the help of a {@code mono-service} {@link OptionValidator}.
 */
@Singleton
public class MonoExpiryValidator extends StandardizedExpiryValidator {

    @Inject
    public MonoExpiryValidator(
            @NonNull final AccountStore accountStore,
            @NonNull final AttributeValidator attributeValidator,
            @NonNull final LongSupplier consensusSecondNow,
            @NonNull final HederaNumbers numbers) {
        super(
                id -> {
                    try {
                        accountStore.loadAccountOrFailWith(id, PbjConverter.fromPbj(INVALID_AUTORENEW_ACCOUNT));
                    } catch (final InvalidTransactionException e) {
                        throw new HandleException(PbjConverter.toPbj(e.getResponseCode()));
                    }
                },
                attributeValidator,
                consensusSecondNow,
                numbers);
    }
}
