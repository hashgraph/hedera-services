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

import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAccountBalance;

import com.hedera.services.bdd.spec.HapiSpec;
import com.hedera.services.bdd.spec.SpecOperation;
import com.hedera.services.bdd.spec.dsl.entities.SpecAccount;
import com.hedera.services.bdd.spec.dsl.entities.SpecContract;
import com.hedera.services.bdd.spec.dsl.operations.AbstractSpecOperation;
import com.hedera.services.bdd.spec.queries.crypto.HapiGetAccountBalance;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

public class GetBalanceOperation extends AbstractSpecOperation implements SpecOperation {

    private final String targetName;

    @Nullable
    private Consumer<HapiGetAccountBalance> assertions = null;

    public GetBalanceOperation(@NonNull final SpecAccount target) {
        super(List.of(target));
        this.targetName = target.name();
    }

    public GetBalanceOperation(@NonNull final SpecContract target) {
        super(List.of(target));
        this.targetName = target.name();
    }

    public GetBalanceOperation andAssert(@NonNull final Consumer<HapiGetAccountBalance> assertions) {
        this.assertions = assertions;
        return this;
    }

    @Override
    protected @NonNull SpecOperation computeDelegate(@NonNull final HapiSpec spec) {
        final var op = getAccountBalance(targetName);
        Optional.ofNullable(assertions).ifPresent(a -> a.accept(op));
        return op;
    }
}
