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

package com.hedera.node.app.workflows.handle;

import static java.util.Objects.requireNonNull;

import com.hedera.node.app.spi.accounts.AccountLookup;
import com.hedera.node.app.spi.meta.HandleContext;
import com.hedera.node.app.spi.validation.AttributeValidator;
import com.hedera.node.app.spi.validation.EntityCreationLimits;
import com.hedera.node.app.spi.validation.EntityExpiryValidator;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.time.Instant;
import java.util.function.LongSupplier;
import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class MonoHandleContext implements HandleContext {
    private final EntityExpiryValidator expiryValidator;
    private final AccountLookup accountLookup;

    @Inject
    public MonoHandleContext(
            @NonNull final EntityExpiryValidator expiryValidator, @NonNull final AccountLookup accountLookup) {
        this.expiryValidator = requireNonNull(expiryValidator);
        this.accountLookup = requireNonNull(accountLookup);
    }

    @Override
    public Instant consensusNow() {
        throw new AssertionError("Not implemented");
    }

    @Override
    public LongSupplier newEntityNumSupplier() {
        throw new AssertionError("Not implemented");
    }

    @Override
    public AttributeValidator attributeValidator() {
        throw new AssertionError("Not implemented");
    }

    @Override
    public EntityExpiryValidator expiryValidator() {
        return expiryValidator;
    }

    @Override
    public EntityCreationLimits entityCreationLimits() {
        throw new AssertionError("Not implemented");
    }

    @Override
    public AccountLookup accountLookup() {
        return accountLookup;
    }
}
