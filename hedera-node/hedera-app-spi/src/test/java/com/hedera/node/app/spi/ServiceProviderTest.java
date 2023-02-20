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

package com.hedera.node.app.spi;

import com.hedera.node.app.spi.state.SchemaRegistry;
import com.swirlds.config.api.Configuration;
import java.util.Set;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;


public class ServiceProviderTest {

    @Test
    void test() {
        final FacilityFacade facilityFacade = new FacilityFacade() {
            @Override
            public Configuration getConfiguration() {
                return null;
            }

            @Override
            public SchemaRegistry getSchemaRegistry() {
                return null;
            }
        };
        final ServiceProvider serviceProvider = new ServiceProvider(facilityFacade);
        final Set<Service> allServices = serviceProvider.getAllServices();

        Assertions.assertEquals(1, allServices.size());
    }
}
