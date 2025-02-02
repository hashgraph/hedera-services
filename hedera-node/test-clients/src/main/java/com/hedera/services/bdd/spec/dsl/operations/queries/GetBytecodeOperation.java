/*
 * Copyright (C) 2025 Hedera Hashgraph, LLC
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

import static com.hedera.services.bdd.spec.queries.QueryVerbs.getContractBytecode;

import com.hedera.services.bdd.spec.HapiSpec;
import com.hedera.services.bdd.spec.SpecOperation;
import com.hedera.services.bdd.spec.dsl.entities.SpecContract;
import com.hedera.services.bdd.spec.dsl.operations.AbstractSpecOperation;
import com.hedera.services.bdd.spec.queries.contract.HapiGetContractBytecode;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

public class GetBytecodeOperation extends AbstractSpecOperation implements SpecOperation {
    private final SpecContract target;

    @Nullable
    private Consumer<HapiGetContractBytecode> assertions = null;

    public GetBytecodeOperation(@NonNull final SpecContract target) {
        super(List.of(target));
        this.target = target;
    }

    public GetBytecodeOperation andAssert(@NonNull final Consumer<HapiGetContractBytecode> assertions) {
        this.assertions = assertions;
        return this;
    }

    @Override
    protected @NonNull SpecOperation computeDelegate(@NonNull final HapiSpec spec) {
        final var op = getContractBytecode(target.name());
        Optional.ofNullable(assertions).ifPresent(a -> a.accept(op));
        return op;
    }
}
