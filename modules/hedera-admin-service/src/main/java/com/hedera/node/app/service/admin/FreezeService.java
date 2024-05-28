/*
<<<<<<<< HEAD:hedera-node/hedera-network-admin-service/src/main/java/com/hedera/node/app/service/networkadmin/FreezeService.java
 * Copyright (C) 2020-2024 Hedera Hashgraph, LLC
========
 * Copyright (C) 2020-2022 Hedera Hashgraph, LLC
>>>>>>>> main:modules/hedera-admin-service/src/main/java/com/hedera/node/app/service/admin/FreezeService.java
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
<<<<<<<< HEAD:hedera-node/hedera-network-admin-service/src/main/java/com/hedera/node/app/service/networkadmin/FreezeService.java

package com.hedera.node.app.service.networkadmin;
========
package com.hedera.node.app.service.admin;
>>>>>>>> main:modules/hedera-admin-service/src/main/java/com/hedera/node/app/service/admin/FreezeService.java

import com.hedera.node.app.spi.PreHandleContext;
import com.hedera.node.app.spi.Service;
import com.hedera.node.app.spi.ServiceFactory;
<<<<<<<< HEAD:hedera-node/hedera-network-admin-service/src/main/java/com/hedera/node/app/service/networkadmin/FreezeService.java
import com.hedera.pbj.runtime.RpcServiceDefinition;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.ServiceLoader;
import java.util.Set;
========
import com.hedera.node.app.spi.state.States;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.ServiceLoader;
>>>>>>>> main:modules/hedera-admin-service/src/main/java/com/hedera/node/app/service/admin/FreezeService.java

/**
 * Implements the HAPI <a
 * href="https://github.com/hashgraph/hedera-protobufs/blob/main/services/freeze_service.proto">Freeze
 * Service</a>.
 */
public interface FreezeService extends Service {
<<<<<<<< HEAD:hedera-node/hedera-network-admin-service/src/main/java/com/hedera/node/app/service/networkadmin/FreezeService.java

    String NAME = "FreezeService";

    @NonNull
    @Override
    default String getServiceName() {
        return NAME;
    }

    @NonNull
    @Override
    default Set<RpcServiceDefinition> rpcDefinitions() {
        return Set.of(FreezeServiceDefinition.INSTANCE);
    }
========
    /**
     * Creates the freeze service pre-handler given a particular Hedera world state.
     *
     * @param states the state of the world
     * @return the corresponding freeze service pre-handler
     */
    @NonNull
    @Override
    FreezePreTransactionHandler createPreTransactionHandler(
            @NonNull States states, @NonNull PreHandleContext ctx);
>>>>>>>> main:modules/hedera-admin-service/src/main/java/com/hedera/node/app/service/admin/FreezeService.java

    /**
     * Returns the concrete implementation instance of the service
     *
     * @return the implementation instance
     */
    @NonNull
    static FreezeService getInstance() {
<<<<<<<< HEAD:hedera-node/hedera-network-admin-service/src/main/java/com/hedera/node/app/service/networkadmin/FreezeService.java
        return ServiceFactory.loadService(FreezeService.class, ServiceLoader.load(FreezeService.class));
========
        return ServiceFactory.loadService(
                FreezeService.class, ServiceLoader.load(FreezeService.class));
>>>>>>>> main:modules/hedera-admin-service/src/main/java/com/hedera/node/app/service/admin/FreezeService.java
    }
}
