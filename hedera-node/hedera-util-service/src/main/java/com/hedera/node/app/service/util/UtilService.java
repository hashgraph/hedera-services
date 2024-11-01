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

package com.hedera.node.app.service.util;

import com.hedera.node.app.spi.RpcService;
import com.hedera.node.app.spi.RpcServiceFactory;
import com.hedera.pbj.runtime.RpcServiceDefinition;
import com.swirlds.state.merkle.SchemaRegistry;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.ServiceLoader;
import java.util.Set;

/**
 * Implements the HAPI <a
 * href="https://github.com/hashgraph/hedera-protobufs/blob/main/services/util_service.proto">Util
 * Service</a>.
 */
public interface UtilService extends RpcService {

    String NAME = "UtilService";

    @NonNull
    @Override
    default String getServiceName() {
        return NAME;
    }

    @NonNull
    @Override
    default Set<RpcServiceDefinition> rpcDefinitions() {
        return Set.of(UtilServiceDefinition.INSTANCE);
    }

    /**
     * Returns the concrete implementation instance of the service.
     *
     * @return the implementation instance
     */
    @NonNull
    static UtilService getInstance() {
        return RpcServiceFactory.loadService(UtilService.class, ServiceLoader.load(UtilService.class));
    }

    @Override
    default void registerSchemas(@NonNull SchemaRegistry registry) {
        // no schemas to register
    }
}
