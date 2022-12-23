/*
 * Copyright (C) 2022 Hedera Hashgraph, LLC
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
package com.hedera.node.app.itest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.hedera.node.app.ServiceFacade;
import com.hedera.node.app.service.admin.FreezeService;
import com.hedera.node.app.service.consensus.ConsensusService;
import com.hedera.node.app.service.contract.ContractService;
import com.hedera.node.app.service.file.FileService;
import com.hedera.node.app.service.network.NetworkService;
import com.hedera.node.app.service.schedule.ScheduleService;
import com.hedera.node.app.service.token.CryptoService;
import com.hedera.node.app.service.token.TokenService;
import com.hedera.node.app.service.util.UtilService;
import com.hedera.node.app.spi.Service;
import java.util.Objects;
import java.util.Set;
import org.junit.jupiter.api.Test;

public class ServiceFacadeTest {

    @Test
    void testAllServices() {
        // when
        final Set<Service> services = ServiceFacade.getAll();

        // then
        assertNotNull(services, "Set must never be null");
        assertEquals(9, services.size(), "All 9 services must be part of the set");

        assertEquals(
                1,
                countForServiceType(services, FreezeService.class),
                "Must contain exactly 1 implementation of the service type");
        assertEquals(
                1,
                countForServiceType(services, ConsensusService.class),
                "Must contain exactly 1 implementation of the service type");
        assertEquals(
                1,
                countForServiceType(services, FileService.class),
                "Must contain exactly 1 implementation of the service type");
        assertEquals(
                1,
                countForServiceType(services, NetworkService.class),
                "Must contain exactly 1 implementation of the service type");
        assertEquals(
                1,
                countForServiceType(services, ScheduleService.class),
                "Must contain exactly 1 implementation of the service type");
        assertEquals(
                1,
                countForServiceType(services, ContractService.class),
                "Must contain exactly 1 implementation of the service type");
        assertEquals(
                1,
                countForServiceType(services, CryptoService.class),
                "Must contain exactly 1 implementation of the service type");
        assertEquals(
                1,
                countForServiceType(services, TokenService.class),
                "Must contain exactly 1 implementation of the service type");
        assertEquals(
                1,
                countForServiceType(services, UtilService.class),
                "Must contain exactly 1 implementation of the service type");
    }

    @Test
    void testServiceTypes() {
        assertNotNull(
                ServiceFacade.getContractService(), "Must provide an instance for the service");
        assertNotNull(ServiceFacade.getCryptoService(), "Must provide an instance for the service");
        assertNotNull(
                ServiceFacade.getConsensusService(), "Must provide an instance for the service");
        assertNotNull(ServiceFacade.getFileService(), "Must provide an instance for the service");
        assertNotNull(ServiceFacade.getFreezeService(), "Must provide an instance for the service");
        assertNotNull(
                ServiceFacade.getNetworkService(), "Must provide an instance for the service");
        assertNotNull(
                ServiceFacade.getScheduleService(), "Must provide an instance for the service");
        assertNotNull(ServiceFacade.getUtilService(), "Must provide an instance for the service");
        assertNotNull(ServiceFacade.getTokenService(), "Must provide an instance for the service");
    }

    private <S extends Service> long countForServiceType(
            final Set<Service> services, final Class<S> serviceType) {
        Objects.requireNonNull(services);
        Objects.requireNonNull(serviceType);
        return services.stream().filter(s -> serviceType.isAssignableFrom(s.getClass())).count();
    }
}
