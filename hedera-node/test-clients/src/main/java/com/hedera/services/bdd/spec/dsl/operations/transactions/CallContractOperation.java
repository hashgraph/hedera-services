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
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCall;
import static java.util.Objects.requireNonNull;

import com.hedera.services.bdd.spec.HapiSpec;
import com.hedera.services.bdd.spec.SpecOperation;
import com.hedera.services.bdd.spec.dsl.entities.SpecContract;
import com.hedera.services.bdd.spec.transactions.contract.HapiContractCall;
import edu.umd.cs.findbugs.annotations.NonNull;

/**
 * Represents a call to a smart contract.
 */
public class CallContractOperation extends AbstractSpecTransaction<CallContractOperation, HapiContractCall>
        implements SpecOperation {
    private static final long DEFAULT_GAS = 100_000;

    private final SpecContract target;
    private final String function;
    private final Object[] parameters;
    private long gas = DEFAULT_GAS;
    private long sendValue;
    private String txnName;

    public CallContractOperation(
            @NonNull final SpecContract target, @NonNull final String function, @NonNull final Object... parameters) {
        super(allRequiredCallEntities(target, parameters));
        this.target = requireNonNull(target);
        this.function = requireNonNull(function);
        this.parameters = requireNonNull(parameters);
    }

    @NonNull
    @Override
    protected SpecOperation computeDelegate(@NonNull final HapiSpec spec) {
        final var op = contractCall(
                        target.name(), function, withSubstitutedTypes(spec.targetNetworkOrThrow(), parameters))
                .sending(sendValue)
                .via(txnName)
                .gas(gas);
        maybeAssertions().ifPresent(a -> a.accept(op));
        return op;
    }

    /**
     * Sets the gas to be used for the call.
     * @param gas the gas
     * @return this
     */
    public CallContractOperation gas(final long gas) {
        this.gas = gas;
        return this;
    }

    /**
     * Sets the value to be sent with the call.
     * @param value the "msg.value" for the call
     * @return this
     */
    public CallContractOperation sending(final long value) {
        this.sendValue = value;
        return this;
    }

    public CallContractOperation via(final String txnName) {
        this.txnName = txnName;
        return this;
    }

    @Override
    protected CallContractOperation self() {
        return this;
    }
}
