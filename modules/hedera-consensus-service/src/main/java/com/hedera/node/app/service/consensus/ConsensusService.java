/*
<<<<<<<< HEAD:hedera-node/hedera-consensus-service/src/main/java/com/hedera/node/app/service/consensus/ConsensusService.java
 * Copyright (C) 2020-2024 Hedera Hashgraph, LLC
========
 * Copyright (C) 2020-2022 Hedera Hashgraph, LLC
>>>>>>>> main:modules/hedera-consensus-service/src/main/java/com/hedera/node/app/service/consensus/ConsensusService.java
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

package com.hedera.node.app.service.consensus;

import com.hedera.node.app.spi.PreHandleContext;
import com.hedera.node.app.spi.Service;
import com.hedera.node.app.spi.ServiceFactory;
<<<<<<<< HEAD:hedera-node/hedera-consensus-service/src/main/java/com/hedera/node/app/service/consensus/ConsensusService.java
import com.hedera.pbj.runtime.RpcServiceDefinition;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.ServiceLoader;
import java.util.Set;
========
import com.hedera.node.app.spi.state.States;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.ServiceLoader;
>>>>>>>> main:modules/hedera-consensus-service/src/main/java/com/hedera/node/app/service/consensus/ConsensusService.java

/**
 * Implements the HAPI <a
 * href="https://github.com/hashgraph/hedera-protobufs/blob/main/services/consensus_service.proto">Consensus
 * Service</a>.
 */
public interface ConsensusService extends Service {

    String NAME = "ConsensusService";

    @NonNull
    @Override
<<<<<<<< HEAD:hedera-node/hedera-consensus-service/src/main/java/com/hedera/node/app/service/consensus/ConsensusService.java
    default String getServiceName() {
        return NAME;
    }

    @NonNull
    @Override
    default Set<RpcServiceDefinition> rpcDefinitions() {
        return Set.of(ConsensusServiceDefinition.INSTANCE);
    }
========
    @NonNull
    ConsensusPreTransactionHandler createPreTransactionHandler(
            @NonNull States states, @NonNull PreHandleContext ctx);
>>>>>>>> main:modules/hedera-consensus-service/src/main/java/com/hedera/node/app/service/consensus/ConsensusService.java

    /**
     * Returns the concrete implementation instance of the service
     *
     * @return the implementation instance
     */
    @NonNull
    static ConsensusService getInstance() {
<<<<<<<< HEAD:hedera-node/hedera-consensus-service/src/main/java/com/hedera/node/app/service/consensus/ConsensusService.java
        return ServiceFactory.loadService(ConsensusService.class, ServiceLoader.load(ConsensusService.class));
========
        return ServiceFactory.loadService(
                ConsensusService.class, ServiceLoader.load(ConsensusService.class));
>>>>>>>> main:modules/hedera-consensus-service/src/main/java/com/hedera/node/app/service/consensus/ConsensusService.java
    }
}
