/*
<<<<<<<< HEAD:hedera-node/hedera-token-service/src/main/java/com/hedera/node/app/service/token/TokenService.java
 * Copyright (C) 2020-2024 Hedera Hashgraph, LLC
========
 * Copyright (C) 2020-2022 Hedera Hashgraph, LLC
>>>>>>>> main:modules/hedera-token-service/src/main/java/com/hedera/node/app/service/token/TokenService.java
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

package com.hedera.node.app.service.token;

import com.hedera.node.app.spi.PreHandleContext;
import com.hedera.node.app.spi.Service;
import com.hedera.node.app.spi.ServiceFactory;
<<<<<<<< HEAD:hedera-node/hedera-token-service/src/main/java/com/hedera/node/app/service/token/TokenService.java
import com.hedera.pbj.runtime.RpcServiceDefinition;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.ServiceLoader;
import java.util.Set;
========
import com.hedera.node.app.spi.state.States;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.ServiceLoader;
>>>>>>>> main:modules/hedera-token-service/src/main/java/com/hedera/node/app/service/token/TokenService.java

/**
 * Implements the HAPI <a
 * href="https://github.com/hashgraph/hedera-protobufs/blob/main/services/token_service.proto">Token
 * Service</a>.
 */
public interface TokenService extends Service {
    /**
     * The name of the service
     */
    String NAME = "TokenService";

    @NonNull
    @Override
<<<<<<<< HEAD:hedera-node/hedera-token-service/src/main/java/com/hedera/node/app/service/token/TokenService.java
    default String getServiceName() {
        return NAME;
    }

    @NonNull
    @Override
    default Set<RpcServiceDefinition> rpcDefinitions() {
        return Set.of(CryptoServiceDefinition.INSTANCE, TokenServiceDefinition.INSTANCE);
    }
========
    @NonNull
    TokenPreTransactionHandler createPreTransactionHandler(
            @NonNull States states, @NonNull PreHandleContext ctx);
>>>>>>>> main:modules/hedera-token-service/src/main/java/com/hedera/node/app/service/token/TokenService.java

    /**
     * Returns the concrete implementation instance of the service
     *
     * @return the implementation instance
     */
    @NonNull
    static TokenService getInstance() {
<<<<<<<< HEAD:hedera-node/hedera-token-service/src/main/java/com/hedera/node/app/service/token/TokenService.java
        return ServiceFactory.loadService(TokenService.class, ServiceLoader.load(TokenService.class));
========
        return ServiceFactory.loadService(
                TokenService.class, ServiceLoader.load(TokenService.class));
>>>>>>>> main:modules/hedera-token-service/src/main/java/com/hedera/node/app/service/token/TokenService.java
    }
}
