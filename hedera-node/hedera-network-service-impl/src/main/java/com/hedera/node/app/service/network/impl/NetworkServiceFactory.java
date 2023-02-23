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

package com.hedera.node.app.service.network.impl;

import com.google.auto.service.AutoService;
import com.hedera.node.app.service.network.NetworkService;
import com.hedera.node.app.spi.FacilityFacade;
import com.hedera.node.app.spi.service.ServiceFactory;
import com.hedera.node.app.spi.service.ServiceProvider;
import edu.umd.cs.findbugs.annotations.NonNull;

/**
 * Factory for creating a {@link NetworkService} instance.
 */
@AutoService(ServiceFactory.class)
public class NetworkServiceFactory implements ServiceFactory<NetworkService> {

    @NonNull
    @Override
    public Class<NetworkService> getServiceClass() {
        return NetworkService.class;
    }

    @NonNull
    @Override
    public NetworkService createService(final ServiceProvider serviceProvider, final FacilityFacade facilityFacade) {
        return new NetworkServiceImpl();
    }
}
