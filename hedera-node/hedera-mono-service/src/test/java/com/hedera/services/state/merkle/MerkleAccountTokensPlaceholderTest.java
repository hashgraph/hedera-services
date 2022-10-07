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
package com.hedera.services.state.merkle;

import static com.hedera.test.utils.SerdeUtils.deserializeFromBytes;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.swirlds.common.io.streams.MerkleDataOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import org.junit.jupiter.api.Test;

class MerkleAccountTokensPlaceholderTest {
    @Test
    void sanityChecks() {
        final var subject = new MerkleAccountTokensPlaceholder();
        assertEquals(1, subject.getVersion());
        assertEquals(0x4dd9cde14aae5f8eL, subject.getClassId());
        assertDoesNotThrow(subject::copy);
    }

    @Test
    void serdeWorks() throws IOException {
        final var subject = new MerkleAccountTokensPlaceholder();
        final var baos = new ByteArrayOutputStream();
        final var out = new MerkleDataOutputStream(baos);
        subject.serialize(out);
        out.flush();
        final var form = baos.toByteArray();
        assertNotNull(deserializeFromBytes(MerkleAccountTokensPlaceholder::new, 1, form));
    }
}
