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

import static com.hedera.services.bdd.spec.queries.QueryVerbs.getTokenNftInfo;

import com.hedera.services.bdd.spec.HapiSpec;
import com.hedera.services.bdd.spec.SpecOperation;
import com.hedera.services.bdd.spec.dsl.entities.SpecToken;
import com.hedera.services.bdd.spec.dsl.operations.AbstractSpecOperation;
import com.hedera.services.bdd.spec.queries.token.HapiGetTokenNftInfo;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * Operation dispatching HapiGetTokenNFTInfo, to retrieve the info for specific NFT from collection,
 * identified by the serial number.
 */
public class GetTokenNftInfoOperation extends AbstractSpecOperation implements SpecOperation {
    private final SpecToken target;
    private final int serialNumber;

    @Nullable
    private Consumer<HapiGetTokenNftInfo> assertions = null;

    public GetTokenNftInfoOperation(@NonNull final SpecToken target, final int serialNumber) {
        super(List.of(target));
        this.target = target;
        this.serialNumber = serialNumber;
    }

    public GetTokenNftInfoOperation andAssert(@NonNull final Consumer<HapiGetTokenNftInfo> assertions) {
        this.assertions = assertions;
        return this;
    }

    @NonNull
    @Override
    protected SpecOperation computeDelegate(@NonNull HapiSpec spec) {
        final var op = getTokenNftInfo(target.name(), serialNumber);
        Optional.ofNullable(assertions).ifPresent(a -> a.accept(op));
        return op;
    }
}