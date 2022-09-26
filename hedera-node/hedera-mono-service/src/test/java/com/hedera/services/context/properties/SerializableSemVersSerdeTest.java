/*
 * Copyright (C) 2020-2022 Hedera Hashgraph, LLC
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
package com.hedera.services.context.properties;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.hedera.test.serde.SelfSerializableDataTest;
import com.hedera.test.utils.SeededPropertySource;
import java.util.Optional;
import java.util.function.BiConsumer;

public class SerializableSemVersSerdeTest extends SelfSerializableDataTest<SerializableSemVers> {
    @Override
    protected Class<SerializableSemVers> getType() {
        return SerializableSemVers.class;
    }

    @Override
    protected SerializableSemVers getExpectedObject(final SeededPropertySource propertySource) {
        return propertySource.nextSerializableSemVers();
    }

    @Override
    protected int getNumTestCasesFor(final int version) {
        return 2 * MIN_TEST_CASES_PER_VERSION;
    }

    @Override
    protected Optional<BiConsumer<SerializableSemVers, SerializableSemVers>> customAssertEquals() {
        return Optional.of(SerializableSemVersSerdeTest::assertEqualVersions);
    }

    public static void assertEqualVersions(
            final SerializableSemVers a, final SerializableSemVers b) {
        assertEquals(a.getProto(), b.getProto(), "protobuf semvers are unequal");
        assertEquals(a.getServices(), b.getServices(), "Services semvers are unequal");
    }
}
