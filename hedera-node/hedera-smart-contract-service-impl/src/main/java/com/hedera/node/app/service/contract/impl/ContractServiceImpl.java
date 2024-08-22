/*
 * Copyright (C) 2020-2024 Hedera Hashgraph, LLC
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

package com.hedera.node.app.service.contract.impl;

import static java.util.Objects.requireNonNull;

import com.hedera.node.app.service.contract.ContractService;
import com.hedera.node.app.service.contract.impl.handlers.ContractHandlers;
import com.hedera.node.app.service.contract.impl.schemas.V0490ContractSchema;
import com.hedera.node.app.service.contract.impl.schemas.V0500ContractSchema;
import com.hedera.node.app.spi.AppContext;
import com.swirlds.state.spi.SchemaRegistry;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.util.List;
import java.util.function.Supplier;
import org.hyperledger.besu.evm.tracing.OperationTracer;

/**
 * Implementation of the {@link ContractService}.
 */
public class ContractServiceImpl implements ContractService {
    public static final long INTRINSIC_GAS_LOWER_BOUND = 21_000L;

    private final ContractServiceComponent component;

    public ContractServiceImpl(@NonNull final AppContext appContext) {
        this(appContext, null);
    }

    public ContractServiceImpl(
            @NonNull final AppContext appContext, @Nullable final Supplier<List<OperationTracer>> addOnTracers) {
        requireNonNull(appContext);
        this.component = DaggerContractServiceComponent.factory()
                .create(
                        appContext.instantSource(),
                        // (FUTURE) Inject the signature verifier instance into the IsAuthorizedSystemContract
                        // C.f. https://github.com/hashgraph/hedera-services/issues/14248
                        appContext.signatureVerifier(),
                        addOnTracers);
    }

    @Override
    public void registerSchemas(@NonNull final SchemaRegistry registry) {
        registry.register(new V0490ContractSchema());
        registry.register(new V0500ContractSchema());
    }

    public ContractHandlers handlers() {
        return component.handlers();
    }
}
