/*
<<<<<<<< HEAD:hedera-node/hedera-file-service/src/main/java/com/hedera/node/app/service/file/FileService.java
 * Copyright (C) 2020-2024 Hedera Hashgraph, LLC
========
 * Copyright (C) 2020-2022 Hedera Hashgraph, LLC
>>>>>>>> main:modules/hedera-file-service/src/main/java/com/hedera/node/app/service/file/FileService.java
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

package com.hedera.node.app.service.file;

import com.hedera.node.app.spi.PreHandleContext;
import com.hedera.node.app.spi.Service;
import com.hedera.node.app.spi.ServiceFactory;
<<<<<<<< HEAD:hedera-node/hedera-file-service/src/main/java/com/hedera/node/app/service/file/FileService.java
import com.hedera.pbj.runtime.RpcServiceDefinition;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.ServiceLoader;
import java.util.Set;
========
import com.hedera.node.app.spi.state.States;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.ServiceLoader;
>>>>>>>> main:modules/hedera-file-service/src/main/java/com/hedera/node/app/service/file/FileService.java

/**
 * Implements the HAPI <a
 * href="https://github.com/hashgraph/hedera-protobufs/blob/main/services/file_service.proto">File
 * Service</a>.
 */
public interface FileService extends Service {

    /**
     * The name of the service
     */
<<<<<<<< HEAD:hedera-node/hedera-file-service/src/main/java/com/hedera/node/app/service/file/FileService.java
    String NAME = "FileService";

    @NonNull
    @Override
    default String getServiceName() {
        return NAME;
    }

    @NonNull
    @Override
    default Set<RpcServiceDefinition> rpcDefinitions() {
        return Set.of(FileServiceDefinition.INSTANCE);
    }
========
    @NonNull
    @Override
    FilePreTransactionHandler createPreTransactionHandler(
            @NonNull States states, @NonNull PreHandleContext ctx);
>>>>>>>> main:modules/hedera-file-service/src/main/java/com/hedera/node/app/service/file/FileService.java

    /**
     * Returns the concrete implementation instance of the service
     *
     * @return the implementation instance
     */
    @NonNull
    static FileService getInstance() {
        return ServiceFactory.loadService(FileService.class, ServiceLoader.load(FileService.class));
    }
}
