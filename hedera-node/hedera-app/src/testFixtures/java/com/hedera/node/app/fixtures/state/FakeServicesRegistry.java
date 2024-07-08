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

package com.hedera.node.app.fixtures.state;

import static java.util.Objects.requireNonNull;

import com.hedera.node.app.api.ServiceApiRegistry;
import com.hedera.node.app.services.ServicesRegistry;
import com.hedera.node.app.spi.AppService;
import com.hedera.node.app.store.StoreRegistry;
import com.swirlds.common.constructable.ConstructableRegistry;
import com.swirlds.config.api.Configuration;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Collections;
import java.util.SortedSet;
import java.util.TreeSet;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * A fake implementation of the {@link ServicesRegistry} interface.
 */
public class FakeServicesRegistry implements ServicesRegistry {
    public static final ServicesRegistry.Factory FACTORY =
            (@NonNull final ConstructableRegistry registry,
                    @NonNull final Configuration configuration,
                    @NonNull final StoreRegistry storeRegistry,
                    @NonNull final ServiceApiRegistry serviceApiRegistry) ->
                    new FakeServicesRegistry(storeRegistry, serviceApiRegistry);

    private static final Logger logger = LogManager.getLogger(FakeServicesRegistry.class);

    /** All stores of a service have to be registered with the {@link StoreRegistry} */
    private final StoreRegistry storeRegistry;

    /** All service APIs have to be registered with the {@link ServiceApiRegistry} */
    private final ServiceApiRegistry serviceApiRegistry;

    /**
     * The set of registered services
     */
    private final SortedSet<ServicesRegistry.Registration> entries;

    /**
     * Creates a new registry.
     */
    public FakeServicesRegistry(
            @NonNull final StoreRegistry storeRegistry, @NonNull final ServiceApiRegistry serviceApiRegistry) {
        this.storeRegistry = requireNonNull(storeRegistry);
        this.serviceApiRegistry = requireNonNull(serviceApiRegistry);
        this.entries = new TreeSet<>();
    }

    /**
     * Register the given service.
     *
     * @param service The service to register
     */
    @Override
    public void register(@NonNull final AppService service) {
        final String serviceName = service.getServiceName();
        final var registry = new FakeSchemaRegistry();
        service.registerSchemas(registry);

        logger.debug("Registering stores and serviceAPIs for service {}", serviceName);
        storeRegistry.registerReadableStores(serviceName, service.readableStoreDefinitions());
        storeRegistry.registerWritableStores(serviceName, service.writableStoreDefinitions());
        serviceApiRegistry.registerServiceApis(serviceName, service.serviceApiDefinitions());

        entries.add(new FakeServicesRegistry.Registration(service, registry));
        logger.info("Registered service {}", serviceName);
    }

    @NonNull
    @Override
    public SortedSet<FakeServicesRegistry.Registration> registrations() {
        return Collections.unmodifiableSortedSet(entries);
    }
}
