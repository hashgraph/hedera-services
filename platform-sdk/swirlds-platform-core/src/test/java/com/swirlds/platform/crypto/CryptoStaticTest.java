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

package com.swirlds.platform.crypto;

import static com.swirlds.platform.roster.RosterRetrieverTests.randomX509Certificate;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.security.cert.X509Certificate;
import org.junit.jupiter.api.Test;

public class CryptoStaticTest {
    @Test
    void testDecodeCertificate() throws Exception {
        final X509Certificate cert1 = randomX509Certificate();
        final byte[] encoded1 = cert1.getEncoded();

        final X509Certificate cert2 = CryptoStatic.decodeCertificate(encoded1);
        final byte[] encoded2 = cert2.getEncoded();

        assertArrayEquals(encoded1, encoded2);
        assertEquals(cert1, cert2);
    }
}
