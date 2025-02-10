// SPDX-License-Identifier: Apache-2.0
package com.swirlds.common.crypto;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.swirlds.common.crypto.engine.EcdsaSecp256k1Verifier;
import com.swirlds.common.test.fixtures.crypto.EcdsaUtils;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.Security;
import java.security.interfaces.ECPublicKey;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
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
    @Disabled("This test needs to be investigated")
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
