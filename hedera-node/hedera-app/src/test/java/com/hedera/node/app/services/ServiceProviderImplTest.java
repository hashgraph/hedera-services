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

import static org.assertj.core.api.Assertions.assertThat;

import com.hedera.node.app.service.token.CryptoService;
import com.hedera.node.app.spi.service.Service;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ServiceProviderImplTest {

    @Test
    void test1() {
        //given
        final var serviceProvider = new ServiceProviderImpl(null);

        //when
        final Set<Service> allServices = serviceProvider.getAllServices();

        //then
        assertThat(allServices).isNotNull();
        assertThat(allServices).isNotEmpty();
        assertThat(allServices).hasAtLeastOneElementOfType(CryptoService.class);
    }

}