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

package com.hedera.services.bdd.spec.dsl.operations.queries;

import static com.hedera.services.bdd.spec.dsl.utils.DslUtils.allRequiredCallEntities;
import static com.hedera.services.bdd.spec.dsl.utils.DslUtils.withSubstitutedTypes;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.contractCallLocal;
import static java.util.Objects.requireNonNull;

import com.hedera.services.bdd.spec.HapiSpec;
import com.hedera.services.bdd.spec.SpecOperation;
import com.hedera.services.bdd.spec.dsl.entities.SpecContract;
import com.hedera.services.bdd.spec.queries.contract.HapiContractCallLocal;
import edu.umd.cs.findbugs.annotations.NonNull;

/**
 * Represents a static call to a smart contract.
 */
public class StaticCallContractOperation extends AbstractSpecQuery<StaticCallContractOperation, HapiContractCallLocal>
        implements SpecOperation {
    private final SpecContract target;
    private final String function;
    private final Object[] parameters;

    public StaticCallContractOperation(
            @NonNull final SpecContract target, @NonNull final String function, @NonNull final Object... parameters) {
        super(allRequiredCallEntities(target, parameters));
        this.target = requireNonNull(target);
        this.function = requireNonNull(function);
        this.parameters = requireNonNull(parameters);
    }

    @NonNull
    @Override
    protected SpecOperation computeDelegate(@NonNull HapiSpec spec) {
        final var op = contractCallLocal(
                target.name(), function, withSubstitutedTypes(spec.targetNetworkOrThrow(), parameters));
        maybeAssertions().ifPresent(a -> a.accept(op));
        return op;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected StaticCallContractOperation self() {
        return this;
    }
}
