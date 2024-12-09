/*
 * Copyright (C) 2024 Hedera Hashgraph, LLC
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

package com.hedera.node.app.service.addressbook;

import com.hedera.node.app.spi.RpcService;
import com.hedera.node.app.spi.RpcServiceFactory;
import com.hedera.pbj.runtime.RpcServiceDefinition;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.ServiceLoader;
import java.util.Set;

/**
 * Implements the HAPI <a
 * href="https://github.com/hashgraph/hedera-protobufs/blob/main/services/address_book_service.proto">Address Book Service</a>.
 */
public interface AddressBookService extends RpcService {

    String NAME = "AddressBookService";

    @NonNull
    @Override
    default String getServiceName() {
        return NAME;
    }

    @NonNull
    @Override
    default Set<RpcServiceDefinition> rpcDefinitions() {
        return Set.of(AddressBookServiceDefinition.INSTANCE);
    }

    /**
     * Returns the concrete implementation instance of the service.
     *
     * @return the implementation instance
     */
    @NonNull
    static AddressBookService getInstance() {
        return RpcServiceFactory.loadService(AddressBookService.class, ServiceLoader.load(AddressBookService.class));
    }

    /**
     * A sort value for the service, make sure this service migrate is after TokenService and FileService migrate.
     */
    @Override
    default int migrationOrder() {
        return 1;
    }
}
