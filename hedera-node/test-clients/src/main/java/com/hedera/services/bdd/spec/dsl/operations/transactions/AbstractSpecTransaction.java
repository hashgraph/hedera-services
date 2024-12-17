/*
 * Copyright (C) 2024 Hedera Hashgraph, LLC
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

package com.hedera.services.bdd.spec.dsl.operations.transactions;

import static java.util.Objects.requireNonNull;

import com.hedera.services.bdd.spec.dsl.SpecEntity;
import com.hedera.services.bdd.spec.dsl.entities.SpecAccount;
import com.hedera.services.bdd.spec.dsl.operations.AbstractSpecOperation;
import com.hedera.services.bdd.spec.transactions.HapiTxnOp;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

public abstract class AbstractSpecTransaction<S extends AbstractSpecTransaction<S, T>, T extends HapiTxnOp<T>>
        extends AbstractSpecOperation {
    @Nullable
    private Consumer<T> assertions;

    @Nullable
    protected SpecAccount payer;

    protected AbstractSpecTransaction(@NonNull final List<SpecEntity> requiredEntities) {
        super(requiredEntities);
    }

    /**
     * Set the account that will pay for the transaction.
     * @param payer the account
     * @return this
     */
    public S payingWith(@NonNull final SpecAccount payer) {
        this.payer = requireNonNull(payer);
        requiredEntities.add(payer);
        return self();
    }

    /**
     * Set the assertions to be made on the transaction.
     *
     * @param assertions the assertions
     * @return this
     */
    public S andAssert(@NonNull final Consumer<T> assertions) {
        requireNonNull(assertions);
        return incorporating(assertions);
    }

    /**
     * Adds customizations to be made to the delegate transaction.
     *
     * @param customizations the customizations
     * @return this
     */
    public S with(@NonNull final Consumer<T> customizations) {
        requireNonNull(customizations);
        return incorporating(customizations);
    }

    /**
     * Get the assertions to be made on the transaction, if present.
     *
     * @return the assertions
     */
    protected Optional<Consumer<T>> maybeAssertions() {
        return Optional.ofNullable(assertions)
                .map(spec -> payer == null ? spec : spec.andThen(op -> op.payingWith(payer.name())));
    }

    /**
     * Return this object as an instance of its concrete type.
     *
     * @return this
     */
    protected abstract S self();

    private S incorporating(@NonNull final Consumer<T> consumer) {
        if (assertions == null) {
            assertions = consumer;
        } else {
            assertions = assertions.andThen(consumer);
        }
        return self();
    }
}
