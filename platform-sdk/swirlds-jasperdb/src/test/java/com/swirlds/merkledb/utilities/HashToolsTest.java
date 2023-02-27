/*
 * Copyright (C) 2021-2023 Hedera Hashgraph, LLC
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

package com.swirlds.merkledb.utilities;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.swirlds.common.crypto.Hash;
import com.swirlds.merkledb.MerkleDbTestUtils;
import java.nio.ByteBuffer;
import org.junit.jupiter.api.Test;

class HashToolsTest {

    @Test
    void constructsExpectedBuffer() {
        final Hash hash = MerkleDbTestUtils.hash(123);

        final ByteBuffer buffer = HashTools.hashToByteBuffer(hash);

        assertArrayEquals(hash.getValue(), buffer.array(), "Hash digest should match created buffer");
    }

    @Test
    void putsDigestAsExpected() {
        final Hash hash = MerkleDbTestUtils.hash(123);
        final ByteBuffer buffer = ByteBuffer.allocate(48);

        HashTools.hashToByteBuffer(hash, buffer);

        assertArrayEquals(hash.getValue(), buffer.array(), "Hash digest should matched filled buffer");
    }

    @Test
    void canOnlyTranslateByteBuffersWithCurrentSerializationVersion() {
        final ByteBuffer buffer = ByteBuffer.allocate(48);
        assertThrows(
                IllegalArgumentException.class,
                () -> HashTools.byteBufferToHash(buffer, 2),
                "Should reject non-current serialization version");
    }
}
