/*
 * Copyright (C) 2020-2022 Hedera Hashgraph, LLC
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
import com.hedera.node.app.spi.state.ReadableStates;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.ServiceLoader;

/**
 * Implements the HAPI <a
 * href="https://github.com/hashgraph/hedera-protobufs/blob/main/services/file_service.proto">File
 * Service</a>.
 */
public interface FileService extends Service {

    @NonNull
    @Override
    default String getServiceName() {
        return FileService.class.getSimpleName();
    }

    /**
     * Creates the file service pre-handler given a particular Hedera world state.
     *
     * @param states the state of the world
     * @return the corresponding file service pre-handler
     */
    @NonNull
    @Override
    FilePreTransactionHandler createPreTransactionHandler(
            @NonNull ReadableStates states, @NonNull PreHandleContext ctx);

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
