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

    protected AbstractSpecTransaction(@NonNull final List<SpecEntity> requiredEntities) {
        super(requiredEntities);
    }

    /**
     * Set the assertions to be made on the transaction.
     *
     * @param assertions the assertions
     * @return this
     */
    public S andAssert(@NonNull final Consumer<T> assertions) {
        this.assertions = requireNonNull(assertions);
        return self();
    }

    /**
     * Get the assertions to be made on the transaction, if present.
     *
     * @return the assertions
     */
    protected Optional<Consumer<T>> maybeAssertions() {
        return Optional.ofNullable(assertions);
    }

    /**
     * Return this object as an instance of its concrete type.
     *
     * @return this
     */
    protected abstract S self();
}
