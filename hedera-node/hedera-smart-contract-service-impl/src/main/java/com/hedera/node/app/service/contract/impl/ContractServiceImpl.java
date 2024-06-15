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

import com.hedera.node.app.service.contract.ContractService;
import com.hedera.node.app.service.contract.impl.handlers.ContractHandlers;
import com.hedera.node.app.service.contract.impl.schemas.V0490ContractSchema;
import com.swirlds.state.spi.SchemaRegistry;
import edu.umd.cs.findbugs.annotations.NonNull;

/**
 * Implementation of the {@link ContractService}.
 */
public enum ContractServiceImpl implements ContractService {
    CONTRACT_SERVICE;

    public static final long INTRINSIC_GAS_LOWER_BOUND = 21_000L;

    private final ContractServiceComponent component;

    ContractServiceImpl() {
        this.component = DaggerContractServiceComponent.create();
    }

    @Override
    public void registerSchemas(@NonNull final SchemaRegistry registry) {
        registry.register(new V0490ContractSchema());
        //        registry.register(new V0500ContractSchema());
    }

    public ContractHandlers handlers() {
        return component.handlers();
    }
}
