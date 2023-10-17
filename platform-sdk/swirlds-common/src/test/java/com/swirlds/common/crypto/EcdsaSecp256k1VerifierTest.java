/*
 * Copyright (C) 2023 Hedera Hashgraph, LLC
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

package com.swirlds.common.crypto;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.swirlds.common.crypto.engine.EcdsaSecp256k1Verifier;
import com.swirlds.common.test.fixtures.crypto.EcdsaUtils;
import com.swirlds.test.framework.TestQualifierTags;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.Security;
import java.security.interfaces.ECPublicKey;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Test class to verify the signatures of ECDSA(secp256k1) keys.
 */
class EcdsaSecp256k1VerifierTest {
    static {
        // add provider only if it's not in the JVM
        Security.addProvider(new BouncyCastleProvider());
    }

    private final EcdsaSecp256k1Verifier subject = new EcdsaSecp256k1Verifier();

    @BeforeAll
    static void setupClass() {
        Security.addProvider(new BouncyCastleProvider());
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 10, 49, 1000})
    @Tag(TestQualifierTags.TIME_CONSUMING)
    void verifySignatureVerification(int count) throws Exception {
        for (int i = 0; i < count; i++) {
            final KeyPair pair = EcdsaUtils.genEcdsaSecp256k1KeyPair();

            final byte[] signature = EcdsaUtils.signWellKnownDigestWithEcdsaSecp256k1(pair.getPrivate());
            final byte[] rawPubKey = EcdsaUtils.asRawEcdsaSecp256k1Key((ECPublicKey) pair.getPublic());

            final boolean isValid =
                    subject.verify(signature, EcdsaUtils.WELL_KNOWN_DIGEST.getBytes(StandardCharsets.UTF_8), rawPubKey);

            assertTrue(isValid, "signature should be valid");
        }
    }
}
