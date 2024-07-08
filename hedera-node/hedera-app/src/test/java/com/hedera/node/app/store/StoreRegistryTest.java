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

package com.hedera.node.app.store;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.hedera.node.app.spi.metrics.StoreMetricsService;
import com.hedera.node.app.spi.store.ReadableStoreDefinition;
import com.hedera.node.app.spi.store.ReadableStoreFactory;
import com.hedera.node.app.spi.store.StoreFactory;
import com.hedera.node.app.spi.store.WritableStoreDefinition;
import com.hedera.node.config.testfixtures.HederaTestConfigBuilder;
import com.swirlds.config.api.Configuration;
import com.swirlds.state.HederaState;
import com.swirlds.state.spi.ReadableStates;
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
public class StoreRegistryTest {

    private static final ReadableStoreDefinition<TestReadableStore1> READ_DEF_1 = new ReadableStoreDefinition<>(
            TestReadableStore1.class, TestReadableStore1::new);
    private static final ReadableStoreDefinition<TestReadableStore2> READ_DEF_2 = new ReadableStoreDefinition<>(
            TestReadableStore2.class, TestReadableStore2::new);
    private static final ReadableStoreDefinition<TestReadableStore3> READ_DEF_3 = new ReadableStoreDefinition<>(
            TestReadableStore3.class, TestReadableStore3::new);

    private static final WritableStoreDefinition<TestWritableStore1> WRITE_DEF_1 = new WritableStoreDefinition<>(
            TestWritableStore1.class, TestWritableStore1::new);
    private static final WritableStoreDefinition<TestWritableStore2> WRITE_DEF_2 = new WritableStoreDefinition<>(
            TestWritableStore2.class, TestWritableStore2::new);
    private static final WritableStoreDefinition<TestWritableStore3> WRITE_DEF_3 = new WritableStoreDefinition<>(
            TestWritableStore3.class, TestWritableStore3::new);

    private static final Configuration CONFIGURATION = HederaTestConfigBuilder.createConfig();

    @Mock
    private HederaState state;

    @Mock
    private StoreMetricsService storeMetricsService;

    private StoreRegistry registry;

    @BeforeEach
    void setup() {
        registry = new StoreRegistry();
    }

    @Nested
    class RegisterReadableStoreTests {

        @SuppressWarnings("DataFlowIssue")
        @Test
        void failsWithNullParameters() {
            assertThatThrownBy(() -> registry.registerReadableStore(null, READ_DEF_1)).isInstanceOf(
                    NullPointerException.class);
            assertThatThrownBy(() -> registry.registerReadableStore("test", null)).isInstanceOf(
                    NullPointerException.class);
            assertThatThrownBy(() -> registry.registerReadableStores(null, List.of(READ_DEF_1))).isInstanceOf(
                    NullPointerException.class);
            assertThatThrownBy(() -> registry.registerReadableStores("test",
                    (Collection<ReadableStoreDefinition<?>>) null)).isInstanceOf(NullPointerException.class);
            assertThatThrownBy(() -> registry.registerReadableStores(null, Arrays.array(READ_DEF_1))).isInstanceOf(
                    NullPointerException.class);
            assertThatThrownBy(
                    () -> registry.registerReadableStores("test", (ReadableStoreDefinition<?>) null)).isInstanceOf(
                    NullPointerException.class);
        }

        @Test
        void succeedsWithDifferentSizeCollection() {
            assertThatCode(() -> registry.registerReadableStores("test", List.of())).doesNotThrowAnyException();
            assertThatCode(
                    () -> registry.registerReadableStores("test", List.of(READ_DEF_1))).doesNotThrowAnyException();
            assertThatCode(
                    () -> registry.registerReadableStores("test",
                            List.of(READ_DEF_2, READ_DEF_3))).doesNotThrowAnyException();
        }

        @Test
        void succeedsWithDifferentSizeArray() {
            assertThatCode(() -> registry.registerReadableStores("test", Arrays.array())).doesNotThrowAnyException();
            assertThatCode(
                    () -> registry.registerReadableStores("test", Arrays.array(READ_DEF_1))).doesNotThrowAnyException();
            assertThatCode(() -> registry.registerReadableStores("test",
                    Arrays.array(READ_DEF_2, READ_DEF_3))).doesNotThrowAnyException();
        }
    }

    @Nested
    class RegisterWritableStoreTests {

        @SuppressWarnings("DataFlowIssue")
        @Test
        void failsWithNullParameters() {
            assertThatThrownBy(() -> registry.registerWritableStores(null, WRITE_DEF_1)).isInstanceOf(
                    NullPointerException.class);
            assertThatThrownBy(() -> registry.registerWritableStore("test", null)).isInstanceOf(
                    NullPointerException.class);
            assertThatThrownBy(() -> registry.registerWritableStores(null, List.of(WRITE_DEF_1))).isInstanceOf(
                    NullPointerException.class);
            assertThatThrownBy(() -> registry.registerWritableStores("test",
                    (Collection<WritableStoreDefinition<?>>) null)).isInstanceOf(NullPointerException.class);
            assertThatThrownBy(() -> registry.registerWritableStores(null, Arrays.array(WRITE_DEF_1))).isInstanceOf(
                    NullPointerException.class);
            assertThatThrownBy(
                    () -> registry.registerWritableStores("test", (WritableStoreDefinition<?>) null)).isInstanceOf(
                    NullPointerException.class);
        }

        @Test
        void succeedsWithDifferentSizeCollection() {
            assertThatCode(() -> registry.registerWritableStores("test", List.of())).doesNotThrowAnyException();
            assertThatCode(
                    () -> registry.registerWritableStores("test", List.of(WRITE_DEF_1))).doesNotThrowAnyException();
            assertThatCode(
                    () -> registry.registerWritableStores("test",
                            List.of(WRITE_DEF_2, WRITE_DEF_3))).doesNotThrowAnyException();
        }

        @Test
        void succeedsWithDifferentSizeArray() {
            assertThatCode(() -> registry.registerWritableStores("test", Arrays.array())).doesNotThrowAnyException();
            assertThatCode(
                    () -> registry.registerWritableStores("test",
                            Arrays.array(WRITE_DEF_1))).doesNotThrowAnyException();
            assertThatCode(() -> registry.registerWritableStores("test",
                    Arrays.array(WRITE_DEF_2, WRITE_DEF_3))).doesNotThrowAnyException();
        }
    }

    @Nested
    class ReadableStoreFactoryTests {

        private ReadableStoreFactory factory;

        @BeforeEach
        void setup() {
            factory = registry.createReadableStoreFactory(state);
        }

        @SuppressWarnings("DataFlowIssue")
        @Test
        void failsWithNullParameter() {
            assertThatThrownBy(() -> factory.getStore(null)).isInstanceOf(NullPointerException.class);
        }

        @Test
        void failsAsExpectedWithoutAvailableApi() {
            assertThatThrownBy(() -> factory.getStore(Object.class)).isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void succeedsWithAvailableApi() {
            registry.registerReadableStores("test", READ_DEF_1);
            assertThat(factory.getStore(TestReadableStore1.class)).isInstanceOf(TestReadableStore1.class);
        }

        @Test
        void failsWithInvalidProvider() {
            registry.registerReadableStores(
                    "test",
                    new ReadableStoreDefinition<>(TestReadableStore1.class, StoreRegistryTest::brokenReadableStoreFactory));
            assertThatThrownBy(() -> factory.getStore(TestReadableStore1.class)).isInstanceOf(
                    IllegalArgumentException.class);
        }
    }


    @Nested
    class StoreFactoryTests {

        private StoreFactory factory;

        @BeforeEach
        void setup() {
            factory = registry.createStoreFactory(state, "test", CONFIGURATION, storeMetricsService);
        }

        @SuppressWarnings("DataFlowIssue")
        @Test
        void failsWithNullParameter() {
            assertThatThrownBy(() -> factory.readableStore(null)).isInstanceOf(NullPointerException.class);
            assertThatThrownBy(() -> factory.writableStore(null)).isInstanceOf(NullPointerException.class);
        }

        @Test
        void failsAsExpectedWithoutAvailableStore() {
            assertThatThrownBy(() -> factory.readableStore(Object.class)).isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> factory.writableStore(Object.class)).isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void succeedsWithAvailableReadableStore() {
            registry.registerReadableStores("test", READ_DEF_1);
            assertThat(factory.readableStore(TestReadableStore1.class)).isInstanceOf(TestReadableStore1.class);
        }

        @Test
        void succeedsWithAvailableWritableStore() {
            registry.registerWritableStore("test", WRITE_DEF_1);
            assertThat(factory.writableStore(TestWritableStore1.class)).isInstanceOf(TestWritableStore1.class);
        }

        @Test
        void failsWithInvalidReadableStoreProvider() {
            registry.registerReadableStores(
                    "test",
                    new ReadableStoreDefinition<>(TestReadableStore1.class, StoreRegistryTest::brokenReadableStoreFactory));
            assertThatThrownBy(() -> factory.readableStore(TestReadableStore1.class)).isInstanceOf(
                    IllegalArgumentException.class);
        }

        @Test
        void failsWithInvalidWritableStoreProvider() {
            registry.registerWritableStore(
                    "test",
                    new WritableStoreDefinition<>(TestWritableStore1.class, StoreRegistryTest::brokenWritableStoreFactory));
            assertThatThrownBy(() -> factory.writableStore(TestWritableStore1.class)).isInstanceOf(
                    IllegalArgumentException.class);
        }

        @Test
        void failsWithDifferentService() {
            registry.registerWritableStore("unknown", WRITE_DEF_1);
            assertThatThrownBy(() -> factory.writableStore(TestWritableStore1.class)).isInstanceOf(
                    IllegalArgumentException.class);
        }
    }

    public static class TestReadableStore1 {
        public TestReadableStore1(ReadableStates states) {
            // no-op
        }
    }

    public static class TestReadableStore2 {
        public TestReadableStore2(ReadableStates states) {
            // no-op
        }
    }

    public static class TestReadableStore3 {
        public TestReadableStore3(ReadableStates states) {
            // no-op
        }
    }

    public static TestReadableStore1 brokenReadableStoreFactory(ReadableStates states) {
        return null;
    }

    public static class TestWritableStore1 {
        public TestWritableStore1(WritableStates states, Configuration configuration, StoreMetricsService storeMetricsService) {
            // no-op
        }
    }

    public static class TestWritableStore2 {
        public TestWritableStore2(WritableStates states, Configuration configuration, StoreMetricsService storeMetricsService) {
            // no-op
        }
    }

    public static class TestWritableStore3 {
        public TestWritableStore3(WritableStates states, Configuration configuration, StoreMetricsService storeMetricsService) {
            // no-op
        }
    }

    public static TestWritableStore1 brokenWritableStoreFactory(WritableStates states, Configuration configuration, StoreMetricsService storeMetricsService) {
        return null;
    }
}
