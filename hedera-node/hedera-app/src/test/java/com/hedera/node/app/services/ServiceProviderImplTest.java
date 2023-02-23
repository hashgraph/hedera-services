/*
 * Copyright (C) 2023 Hedera Hashgraph, LLC
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

package com.hedera.node.app.services;

import static org.assertj.core.api.Assertions.assertThat;

import com.hedera.node.app.service.admin.FreezeService;
import com.hedera.node.app.service.consensus.ConsensusService;
import com.hedera.node.app.service.contract.ContractService;
import com.hedera.node.app.service.file.FileService;
import com.hedera.node.app.service.network.NetworkService;
import com.hedera.node.app.service.schedule.ScheduleService;
import com.hedera.node.app.service.token.CryptoService;
import com.hedera.node.app.service.token.TokenService;
import com.hedera.node.app.service.util.UtilService;
import com.hedera.node.app.spi.service.Service;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ServiceProviderImplTest {

    @Test
    void testAllServicesContainsBasicServices() {
        // given
        final var serviceProvider = new ServiceProviderImpl(null);

        // when
        final Set<Service> allServices = serviceProvider.getAllServices();

        // then
        assertThat(allServices).isNotNull();
        assertThat(allServices).isNotEmpty();
        assertThat(allServices).hasAtLeastOneElementOfType(FreezeService.class);
        assertThat(allServices).hasAtLeastOneElementOfType(ConsensusService.class);
        assertThat(allServices).hasAtLeastOneElementOfType(FileService.class);
        assertThat(allServices).hasAtLeastOneElementOfType(NetworkService.class);
        assertThat(allServices).hasAtLeastOneElementOfType(ScheduleService.class);
        assertThat(allServices).hasAtLeastOneElementOfType(ContractService.class);
        assertThat(allServices).hasAtLeastOneElementOfType(CryptoService.class);
        assertThat(allServices).hasAtLeastOneElementOfType(TokenService.class);
        assertThat(allServices).hasAtLeastOneElementOfType(UtilService.class);
    }

    @Test
    void testCanAccessBasicServices() {
        // given
        final var serviceProvider = new ServiceProviderImpl(null);

        // then
        assertThat(serviceProvider.getServiceByType(FreezeService.class)).containsInstanceOf(FreezeService.class);
        assertThat(serviceProvider.getServiceByType(ConsensusService.class)).containsInstanceOf(ConsensusService.class);
        assertThat(serviceProvider.getServiceByType(FileService.class)).containsInstanceOf(FileService.class);
        assertThat(serviceProvider.getServiceByType(NetworkService.class)).containsInstanceOf(NetworkService.class);
        assertThat(serviceProvider.getServiceByType(ScheduleService.class)).containsInstanceOf(ScheduleService.class);
        assertThat(serviceProvider.getServiceByType(ContractService.class)).containsInstanceOf(ContractService.class);
        assertThat(serviceProvider.getServiceByType(CryptoService.class)).containsInstanceOf(CryptoService.class);
        assertThat(serviceProvider.getServiceByType(TokenService.class)).containsInstanceOf(TokenService.class);
        assertThat(serviceProvider.getServiceByType(UtilService.class)).containsInstanceOf(UtilService.class);
    }

    @Test
    void testCanNotAccessBasicServiceType() {
        // given
        final var serviceProvider = new ServiceProviderImpl(null);

        // then
        assertThat(serviceProvider.getServiceByType(Service.class)).isNotPresent();
    }
}
