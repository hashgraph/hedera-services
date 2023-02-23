/*
 * Copyright (C) 2023 Hedera Hashgraph, LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package com.hedera.node.app.spi.service;

import com.hedera.node.app.spi.FacilityFacade;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Set;

/**
 * Factory for creating a service.
 *
 * @param <T> type of the service
 */
public interface ServiceFactory<T extends Service> {

    /**
     * Returns the class of the service that this factory creates.
     *
     * @return the class of the service
     */
    @NonNull
    Class<T> getServiceClass();

    /**
     * Creates a service.
     *
     * @param serviceProvider the service provider that can be used to get access to other services
     * @param facilityFacade  the facility facade that can be used to get access to basic functionalities
     * @return the service
     */
    @NonNull
    T createService(ServiceProvider serviceProvider, FacilityFacade facilityFacade);

    /**
     * Returns the set of all services that the service that is created by this factory depends on.
     *
     * @return set of all services that the service that is created by this factory depends on
     */
    @NonNull
    default Set<Class<Service>> getDependencies() {
        return Set.of();
    }
}
