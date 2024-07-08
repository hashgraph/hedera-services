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

package com.hedera.node.app.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.hedera.node.app.spi.api.ServiceApiDefinition;
import com.hedera.node.app.spi.api.ServiceApiFactory;
import com.hedera.node.app.spi.metrics.StoreMetricsService;
import com.hedera.node.config.testfixtures.HederaTestConfigBuilder;
import com.swirlds.config.api.Configuration;
import com.swirlds.state.HederaState;
import com.swirlds.state.spi.WritableStates;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ServiceApiRegistryTest {

    private static final Configuration configuration = HederaTestConfigBuilder.createConfig();

    @Mock
    private HederaState state;

    @Mock
    private StoreMetricsService storeMetricsService;

    private ServiceApiRegistry registry;

    @BeforeEach
    void setup() {
        registry = new ServiceApiRegistry();
    }

    @Nested
    class ServiceApiFactoryTests {

        private ServiceApiFactory factory;

        @BeforeEach
        void setup() {
            factory = registry.createServiceApiFactory(state, configuration, storeMetricsService);
        }

        @Test
        void failsWithNullParameter() {
            assertThatThrownBy(() -> factory.serviceApi(null)).isInstanceOf(NullPointerException.class);
        }

        @Test
        void failsAsExpectedWithoutAvailableApi() {
            assertThatThrownBy(() -> factory.serviceApi(Object.class)).isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void succeedsWithAvailableApi() {
            registry.registerServiceApi("test", new ServiceApiDefinition<>(TestApi.class, TestApi::new));
            assertThat(factory.serviceApi(TestApi.class)).isInstanceOf(TestApi.class);
        }

        @Test
        void failsWithInvalidProvider() {
            registry.registerServiceApi(
                    "test", new ServiceApiDefinition<>(TestApi.class, ServiceApiRegistryTest::brokenFactory));
            assertThatThrownBy(() -> factory.serviceApi(TestApi.class)).isInstanceOf(IllegalArgumentException.class);
        }
    }

    public static class TestApi {
        public TestApi(WritableStates states, Configuration configuration, StoreMetricsService storeMetricsService) {
            // no-op
        }
    }

    public static TestApi brokenFactory(
            WritableStates states, Configuration configuration, StoreMetricsService storeMetricsService) {
        return null;
    }
}
