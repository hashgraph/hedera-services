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

package com.hedera.node.app.service.contract;

import com.hedera.node.app.spi.RpcService;
import com.hedera.pbj.runtime.RpcServiceDefinition;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Set;

/**
 * Implements the HAPI <a
 * href="https://github.com/hashgraph/hedera-protobufs/blob/main/services/smart_contract_service.proto">Smart
 * Contract Service</a>.
 */
public interface ContractService extends RpcService {
    /**
     * The name of the service.
     */
    String NAME = "ContractService";

    /**
     * {@inheritDoc}
     *
     * Ensure the contract service schemas are migrated before the {@code TokenService} schemas, since
     * the {@code TokenService} depends on the {@code ContractService} to know the updated first storage
     * keys for contracts with broken storage links.
     *
     * @return {@code Integer.MIN_VALUE}
     */
    @Override
    default int migrationOrder() {
        return Integer.MIN_VALUE;
    }

    @NonNull
    @Override
    default String getServiceName() {
        return NAME;
    }

    @NonNull
    @Override
    default Set<RpcServiceDefinition> rpcDefinitions() {
        return Set.of(SmartContractServiceDefinition.INSTANCE);
    }
}
