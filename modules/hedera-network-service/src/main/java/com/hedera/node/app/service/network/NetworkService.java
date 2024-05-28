/*
<<<<<<<< HEAD:hedera-node/hedera-network-admin-service/src/main/java/com/hedera/node/app/service/networkadmin/NetworkService.java
 * Copyright (C) 2023-2024 Hedera Hashgraph, LLC
========
 * Copyright (C) 2020-2022 Hedera Hashgraph, LLC
>>>>>>>> main:modules/hedera-network-service/src/main/java/com/hedera/node/app/service/network/NetworkService.java
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

package com.hedera.node.app.service.networkadmin;

import com.hedera.node.app.spi.PreHandleContext;
import com.hedera.node.app.spi.Service;
import com.hedera.node.app.spi.ServiceFactory;
<<<<<<<< HEAD:hedera-node/hedera-network-admin-service/src/main/java/com/hedera/node/app/service/networkadmin/NetworkService.java
import com.hedera.pbj.runtime.RpcServiceDefinition;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.ServiceLoader;
import java.util.Set;
========
import com.hedera.node.app.spi.state.States;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.ServiceLoader;
>>>>>>>> main:modules/hedera-network-service/src/main/java/com/hedera/node/app/service/network/NetworkService.java

/**
 * Implements the HAPI <a
 * href="https://github.com/hashgraph/hedera-protobufs/blob/main/services/network_service.proto">Network
 * Service</a>.
 */
public interface NetworkService extends Service {
<<<<<<<< HEAD:hedera-node/hedera-network-admin-service/src/main/java/com/hedera/node/app/service/networkadmin/NetworkService.java
    String NAME = "NetworkService";

    @NonNull
    @Override
    default String getServiceName() {
        return NAME;
    }

    @NonNull
    @Override
    default Set<RpcServiceDefinition> rpcDefinitions() {
        return Set.of(NetworkServiceDefinition.INSTANCE);
    }
========
    /**
     * Creates the network service pre-handler given a particular Hedera world state.
     *
     * @param states the state of the world
     * @return the corresponding network service pre-handler
     */
    @NonNull
    @Override
    NetworkPreTransactionHandler createPreTransactionHandler(
            @NonNull States states, @NonNull PreHandleContext ctx);
>>>>>>>> main:modules/hedera-network-service/src/main/java/com/hedera/node/app/service/network/NetworkService.java

    /**
     * Returns the concrete implementation instance of the service
     *
     * @return the implementation instance
     */
    @NonNull
    static NetworkService getInstance() {
<<<<<<<< HEAD:hedera-node/hedera-network-admin-service/src/main/java/com/hedera/node/app/service/networkadmin/NetworkService.java
        return ServiceFactory.loadService(NetworkService.class, ServiceLoader.load(NetworkService.class));
========
        return ServiceFactory.loadService(
                NetworkService.class, ServiceLoader.load(NetworkService.class));
>>>>>>>> main:modules/hedera-network-service/src/main/java/com/hedera/node/app/service/network/NetworkService.java
    }
}
