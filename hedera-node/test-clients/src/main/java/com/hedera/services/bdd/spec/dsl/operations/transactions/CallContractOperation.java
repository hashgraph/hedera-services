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

import static com.hedera.services.bdd.spec.dsl.utils.DslUtils.withSubstitutedAddresses;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCall;
import static java.util.Objects.requireNonNull;

import com.hedera.services.bdd.SpecOperation;
import com.hedera.services.bdd.spec.HapiSpec;
import com.hedera.services.bdd.spec.dsl.SpecEntity;
import com.hedera.services.bdd.spec.dsl.entities.SpecContract;
import com.hedera.services.bdd.spec.transactions.contract.HapiContractCall;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.ArrayList;
import java.util.List;

/**
 * Represents a call to a smart contract.
 */
public class CallContractOperation extends AbstractSpecTransaction<CallContractOperation, HapiContractCall>
        implements SpecOperation {
    private final SpecContract target;
    private final String function;
    private final Object[] parameters;

    public CallContractOperation(
            @NonNull final SpecContract target, @NonNull final String function, @NonNull final Object... parameters) {
        super(allRequiredEntities(target, parameters));
        this.target = requireNonNull(target);
        this.function = requireNonNull(function);
        this.parameters = requireNonNull(parameters);
    }

    @NonNull
    @Override
    protected SpecOperation computeDelegate(@NonNull final HapiSpec spec) {
        final var op = contractCall(
                target.name(), function, withSubstitutedAddresses(spec.targetNetworkOrThrow(), parameters));
        maybeAssertions().ifPresent(a -> a.accept(op));
        return op;
    }

    @Override
    protected CallContractOperation self() {
        return this;
    }

    private static List<SpecEntity> allRequiredEntities(
            @NonNull final SpecContract target, @NonNull final Object[] parameters) {
        List<SpecEntity> requiredEntities = new ArrayList<>();
        requiredEntities.add(target);
        for (final var parameter : parameters) {
            if (parameter instanceof SpecEntity entity) {
                requiredEntities.add(entity);
            }
        }
        return requiredEntities;
    }
}
