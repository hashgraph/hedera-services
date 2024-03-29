/*
 * Copyright (C) 2020-2024 Hedera Hashgraph, LLC
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

package com.swirlds.platform.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.params.provider.Arguments.arguments;

import com.swirlds.common.constructable.ConstructableRegistry;
import com.swirlds.common.constructable.ConstructableRegistryException;
import com.swirlds.common.io.SelfSerializable;
import com.swirlds.common.test.fixtures.RandomUtils;
import com.swirlds.common.test.fixtures.io.SerializationUtils;
import com.swirlds.config.extensions.test.fixtures.TestConfigBuilder;
import com.swirlds.platform.system.BasicSoftwareVersion;
import com.swirlds.platform.system.StaticSoftwareVersion;
import com.swirlds.platform.test.fixtures.event.TestingEventBuilder;
import java.io.IOException;
import java.util.Random;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

public class SerializationTests {

    @BeforeAll
    public static void setUp() throws ConstructableRegistryException {
        new TestConfigBuilder().withValue("transactionMaxBytes", 1_000_000).getOrCreateConfig();

        ConstructableRegistry.getInstance().registerConstructables("com.swirlds");
        StaticSoftwareVersion.setSoftwareVersion(new BasicSoftwareVersion(1));
    }

    @AfterAll
    static void afterAll() {
        StaticSoftwareVersion.reset();
    }

    @ParameterizedTest
    @DisplayName("Serialize then deserialize SelfSerializable class")
    @MethodSource("selfSerializableProvider")
    public <T extends SelfSerializable> void serializeDeserializeTest(T generated) throws IOException {
        T serDes = SerializationUtils.serializeDeserialize(generated);
        assertEquals(generated, serDes);
    }

    static Stream<Arguments> selfSerializableProvider() {
        final Random random = RandomUtils.getRandomPrintSeed();
        return Stream.of(arguments(TestingEventBuilder.builder(random).build().getHashedData()));
    }
}
