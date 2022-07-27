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
package com.hedera.services.sigs.utils;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.hedera.test.factories.keys.KeyFactory;
import com.swirlds.common.utility.CommonUtils;
import java.util.Arrays;
import org.bouncycastle.crypto.params.ECPublicKeyParameters;
import org.junit.jupiter.api.Test;

class MiscCryptoUtilsTest {
    @Test
    void computesExpectedHashes() {
        final var data = "AHOTMESS".getBytes();

        final var expectedHexedHash =
                "9eed2a3d8a3987c15d6ec326012c8a3b91346341921a09cc75eb38df28101e8d";

        final var actualHexedHash = CommonUtils.hex(MiscCryptoUtils.keccak256DigestOf(data));

        assertEquals(expectedHexedHash, actualHexedHash);
    }

    @Test
    void recoversUncompressedSecp256k1PubKey() {
        final var kp = KeyFactory.ecdsaKpGenerator.generateKeyPair();
        final var q = ((ECPublicKeyParameters) kp.getPublic()).getQ();
        final var compressed = q.getEncoded(true);
        final var uncompressed = q.getEncoded(false);

        assertArrayEquals(
                Arrays.copyOfRange(uncompressed, 1, 65),
                MiscCryptoUtils.decompressSecp256k1(compressed));
    }
}
