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
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.hedera.node.app.spi.api.ServiceApiDefinition;
import com.hedera.node.app.spi.api.ServiceApiFactory;
import com.hedera.node.app.spi.metrics.StoreMetricsService;
import com.hedera.node.config.testfixtures.HederaTestConfigBuilder;
import com.swirlds.config.api.Configuration;
import com.swirlds.state.HederaState;
import com.swirlds.state.spi.WritableStates;
import java.util.Collection;
import java.util.List;
import org.assertj.core.util.Arrays;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ServiceApiRegistryTest {

    private static final Configuration CONFIGURATION = HederaTestConfigBuilder.createConfig();
    private static final ServiceApiDefinition<TestApi1> DEF_1 = new ServiceApiDefinition<>(TestApi1.class, TestApi1::new);
    private static final ServiceApiDefinition<TestApi2> DEF_2 = new ServiceApiDefinition<>(TestApi2.class, TestApi2::new);
    private static final ServiceApiDefinition<TestApi3> DEF_3 = new ServiceApiDefinition<>(TestApi3.class, TestApi3::new);

    @Mock
    private HederaState state;

    @Mock
    private StoreMetricsService storeMetricsService;

    private ServiceApiRegistry registry;

    @BeforeEach
    void setup() {
        registry = new ServiceApiRegistry();
    }

    @SuppressWarnings("DataFlowIssue")
    @Test
    void failsWithNullParameters() {
        assertThatThrownBy(() -> registry.registerServiceApi(null, DEF_1)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> registry.registerServiceApi("test", null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> registry.registerServiceApis(null, List.of(DEF_1))).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> registry.registerServiceApis("test", (Collection<ServiceApiDefinition<?>>) null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> registry.registerServiceApis(null, Arrays.array(DEF_1))).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> registry.registerServiceApis("test", (ServiceApiDefinition<?>) null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    void succeedsDifferentSizeCollection() {
        assertThatCode(() -> registry.registerServiceApis("test", List.of())).doesNotThrowAnyException();
        assertThatCode(() -> registry.registerServiceApis("test", List.of(DEF_1))).doesNotThrowAnyException();
        assertThatCode(() -> registry.registerServiceApis("test", List.of(DEF_2, DEF_3))).doesNotThrowAnyException();
    }

    @Test
    void succeedsDifferentSizeArray() {
        assertThatCode(() -> registry.registerServiceApis("test", Arrays.array())).doesNotThrowAnyException();
        assertThatCode(() -> registry.registerServiceApis("test", Arrays.array(DEF_1))).doesNotThrowAnyException();
        assertThatCode(() -> registry.registerServiceApis("test", Arrays.array(DEF_2, DEF_3))).doesNotThrowAnyException();
    }

    @Nested
    class ServiceApiFactoryTests {

        private ServiceApiFactory factory;

        @BeforeEach
        void setup() {
            factory = registry.createServiceApiFactory(state, CONFIGURATION, storeMetricsService);
        }

        @SuppressWarnings("DataFlowIssue")
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
            registry.registerServiceApi("test", DEF_1);
            assertThat(factory.serviceApi(TestApi1.class)).isInstanceOf(TestApi1.class);
        }

        @Test
        void failsWithInvalidProvider() {
            registry.registerServiceApi(
                    "test", new ServiceApiDefinition<>(TestApi1.class, ServiceApiRegistryTest::brokenFactory));
            assertThatThrownBy(() -> factory.serviceApi(TestApi1.class)).isInstanceOf(IllegalArgumentException.class);
        }
    }

    public static class TestApi1 {
        public TestApi1(WritableStates states, Configuration configuration, StoreMetricsService storeMetricsService) {
            // no-op
        }
    }

    public static class TestApi2 {
        public TestApi2(WritableStates states, Configuration configuration, StoreMetricsService storeMetricsService) {
            // no-op
        }
    }

    public static class TestApi3 {
        public TestApi3(WritableStates states, Configuration configuration, StoreMetricsService storeMetricsService) {
            // no-op
        }
    }

    public static TestApi1 brokenFactory(
            WritableStates states, Configuration configuration, StoreMetricsService storeMetricsService) {
        return null;
    }
}
