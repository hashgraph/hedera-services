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
package com.hedera.services.state.virtual;

import static com.google.common.truth.Truth.assertThat;

import com.swirlds.common.io.streams.SerializableDataInputStream;
import com.swirlds.common.io.streams.SerializableDataOutputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import org.junit.jupiter.api.Test;

class UniqueTokenValueSupplierTest {
    @Test
    void tokenSupplier_whenCalledMultipleTimes_producesNewCopies() {
        UniqueTokenValueSupplier supplier = new UniqueTokenValueSupplier();
        UniqueTokenValue value1 = supplier.get();
        UniqueTokenValue value2 = supplier.get();

        assertThat(value1).isNotNull();
        assertThat(value2).isNotNull();
        assertThat(value1).isNotSameInstanceAs(value2);
    }

    // Test invariants. The below tests are designed to fail if one accidentally modifies specified
    // constants.
    @Test
    void checkClassId_isExpected() {
        assertThat(new UniqueTokenValueSupplier().getClassId()).isEqualTo(0xc4d512c6695451d4L);
    }

    @Test
    void checkCurrentVersion_isExpected() {
        assertThat(new UniqueTokenValueSupplier().getVersion()).isEqualTo(1);
    }

    @Test
    void noopFunctions_forTestCoverage() {
        UniqueTokenValueSupplier supplier = new UniqueTokenValueSupplier();
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        SerializableDataOutputStream dataOutputStream =
                new SerializableDataOutputStream(outputStream);
        supplier.serialize(dataOutputStream);
        assertThat(outputStream.toByteArray()).isEmpty();

        SerializableDataInputStream dataInputStream =
                new SerializableDataInputStream(
                        new ByteArrayInputStream(outputStream.toByteArray()));
        supplier.deserialize(dataInputStream, 1);
    }
}
