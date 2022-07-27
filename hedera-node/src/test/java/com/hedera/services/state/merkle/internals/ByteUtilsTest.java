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
package com.hedera.services.state.merkle.internals;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.hedera.services.legacy.proto.utils.CommonUtils;
import com.swirlds.common.crypto.DigestType;
import org.junit.jupiter.api.Test;

class ByteUtilsTest {

    @Test
    void buildsByteArrayAsExpected() {
        final var expected = new byte[] {0, 0, 0, 0, 0, 0, 48, 57};
        final var expectedHashBytes = CommonUtils.noThrowSha384HashOf(expected);
        final var actualLong = 12_345L;

        assertArrayEquals(expectedHashBytes, ByteUtils.getHashBytes(new long[] {actualLong}));
        assertEquals(0, ByteUtils.getHashBytes(null).length);
        assertEquals(DigestType.SHA_384.digestLength(), ByteUtils.getHashBytes(new long[0]).length);
    }
}
