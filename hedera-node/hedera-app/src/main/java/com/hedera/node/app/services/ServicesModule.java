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

package com.hedera.node.app.services;

import com.hedera.node.app.FacilityFacadeModule;
import com.hedera.node.app.service.admin.FreezeService;
import com.hedera.node.app.service.consensus.ConsensusService;
import com.hedera.node.app.service.contract.ContractService;
import com.hedera.node.app.service.file.FileService;
import com.hedera.node.app.service.network.NetworkService;
import com.hedera.node.app.service.schedule.ScheduleService;
import com.hedera.node.app.service.token.TokenService;
import com.hedera.node.app.service.util.UtilService;
import com.hedera.node.app.spi.FacilityFacade;
import com.hedera.node.app.spi.service.Service;
import com.hedera.node.app.spi.service.ServiceProvider;
import dagger.Module;
import dagger.Provides;
import dagger.multibindings.ElementsIntoSet;
import java.util.Objects;
import java.util.Set;
import javax.inject.Singleton;

@Module(includes = FacilityFacadeModule.class)
public interface ServicesModule {

    @Provides
    @Singleton
    static ServiceProvider bindServiceProvider(final FacilityFacade facilityFacade) {
        return new ServiceProviderImpl(facilityFacade);
    }

    @Provides
    @ElementsIntoSet
    static Set<Service> provideAllServices(final ServiceProvider serviceProvider) {
        return serviceProvider.getAllServices();
    }

    @Provides
    @Singleton
    static FreezeService provideFreezeService(final ServiceProvider serviceProvider) {
        return provideService(serviceProvider, FreezeService.class);
    }

    @Provides
    @Singleton
    static ConsensusService provideConsensusService(final ServiceProvider serviceProvider) {
        return provideService(serviceProvider, ConsensusService.class);
    }

    @Provides
    @Singleton
    static FileService provideFileService(final ServiceProvider serviceProvider) {
        return provideService(serviceProvider, FileService.class);
    }

    @Provides
    @Singleton
    static NetworkService provideNetworkService(final ServiceProvider serviceProvider) {
        return provideService(serviceProvider, NetworkService.class);
    }

    @Provides
    @Singleton
    static ScheduleService provideScheduleService(final ServiceProvider serviceProvider) {
        return provideService(serviceProvider, ScheduleService.class);
    }

    @Provides
    @Singleton
    static ContractService provideContractService(final ServiceProvider serviceProvider) {
        return provideService(serviceProvider, ContractService.class);
    }

    @Provides
    @Singleton
    static TokenService provideTokenService(final ServiceProvider serviceProvider) {
        return provideService(serviceProvider, TokenService.class);
    }

    @Provides
    @Singleton
    static UtilService provideUtilService(final ServiceProvider serviceProvider) {
        return provideService(serviceProvider, UtilService.class);
    }

    static <S extends Service> S provideService(final ServiceProvider serviceProvider, final Class<S> serviceType) {
        Objects.requireNonNull(serviceProvider, "serviceProvider must not be null");
        Objects.requireNonNull(serviceProvider, "serviceType must not be serviceType");
        return serviceProvider.getServiceByType(serviceType)
                .orElseThrow(() -> new IllegalStateException(serviceType.getName() + " not found"));
    }
}
