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

package com.hedera.node.app.di;

import com.hedera.node.app.service.token.CryptoService;
import com.hedera.node.app.spi.Service;
import java.util.Set;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class DaggerServiceProviderTest {

    @Test
    void test1() {
        final Set<Service> allServices = DaggerServiceProvider.provideServiceProvider().getAllServices();
        Assertions.assertEquals(1, allServices.size());
    }

    @Test
    void test2() {
        final Service service = DaggerServiceProvider.provideServiceProvider().getServiceByName(
                CryptoService.NAME).get();
        Assertions.assertNotNull(service);
        Assertions.assertTrue(CryptoService.class.isAssignableFrom(service.getClass()));
    }

    @Test
    void test3() {
        final CryptoService service = DaggerServiceProvider.provideServiceProvider().getServiceByType(
                CryptoService.class).get();
        Assertions.assertNotNull(service);
    }

    @Test
    void test4() {
        final ClassWithAllServices classWithAllServices = DaggerModuleOnlyForTests.builder().build()
                .createClassWithAllServices();
        Assertions.assertNotNull(classWithAllServices);
        Assertions.assertNotNull(classWithAllServices.getServices());
        Assertions.assertEquals(1, classWithAllServices.getServices().size());
    }

    @Test
    void test5() {
        final ClassWithCryptoService instance = DaggerModuleOnlyForTests.builder().build()
                .createClassWithCryptoService();
        Assertions.assertNotNull(instance);
        Assertions.assertNotNull(instance.getCryptoService());
    }
}
