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

import static com.hedera.services.bdd.spec.dsl.utils.DslUtils.allRequiredCallEntities;
import static com.hedera.services.bdd.spec.dsl.utils.DslUtils.withSubstitutedTypes;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCallWithFunctionAbi;
import static com.hedera.services.bdd.suites.contract.Utils.FunctionType.FUNCTION;
import static com.hedera.services.bdd.suites.contract.Utils.getABIFor;
import static java.util.Objects.requireNonNull;

import com.hedera.services.bdd.spec.HapiSpec;
import com.hedera.services.bdd.spec.SpecOperation;
import com.hedera.services.bdd.spec.dsl.contracts.TokenRedirectContract;
import com.hedera.services.bdd.spec.dsl.entities.SpecToken;
import com.hedera.services.bdd.spec.transactions.contract.HapiContractCall;
import edu.umd.cs.findbugs.annotations.NonNull;

public class CallTokenOperation extends AbstractSpecTransaction<CallTokenOperation, HapiContractCall>
        implements SpecOperation {
    private static final long DEFAULT_GAS = 100_000;

    private final SpecToken target;
    private final String function;
    private final Object[] parameters;
    private final TokenRedirectContract redirectContract;

    private long gas = DEFAULT_GAS;

    public CallTokenOperation(
            @NonNull final SpecToken target,
            @NonNull final TokenRedirectContract redirectContract,
            @NonNull final String function,
            @NonNull final Object... parameters) {
        super(allRequiredCallEntities(target, parameters));
        this.target = requireNonNull(target);
        this.function = requireNonNull(function);
        this.parameters = requireNonNull(parameters);
        this.redirectContract = requireNonNull(redirectContract);
    }

    /**
     * Sets the gas to be used for the call.
     *
     * @param gas the gas
     * @return this
     */
    public CallTokenOperation gas(final long gas) {
        this.gas = gas;
        return this;
    }

    @NonNull
    @Override
    protected SpecOperation computeDelegate(@NonNull HapiSpec spec) {
        final var network = spec.targetNetworkOrThrow();
        final var abi = getABIFor(FUNCTION, function, redirectContract.abiResource());
        final var arguments = withSubstitutedTypes(network, parameters);
        final var op = contractCallWithFunctionAbi(target.addressOn(network).toString(), abi, arguments)
                .gas(gas);
        maybeAssertions().ifPresent(a -> a.accept(op));
        return op;
    }

    @Override
    protected CallTokenOperation self() {
        return this;
    }
}
