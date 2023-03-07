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

package com.swirlds.common.test.crypto;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.swirlds.common.crypto.engine.EcdsaSecp256k1Verifier;
import com.swirlds.common.utility.CommonUtils;
import com.swirlds.test.framework.TestQualifierTags;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.Security;
import java.security.interfaces.ECPublicKey;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
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
    @CsvSource({
        "f9f27f9ad76bbdaa518ed6d21f9ea472adaf0aa5a92ba7ec035812451ec09aa9,"
                + "0923761d214c8a44e4014c717f72437273b39e677b50e56881f09492822a0674,"
                + "3045022100f9f27f9ad76bbdaa518ed6d21f9ea472adaf0aa5a92ba7ec035812"
                + "451ec09aa902200923761d214c8a44e4014c717f72437273b39e677b50e56881f09492822a0674",
        "60498ba4ae336e76924d8d047c9991f873ebd21ee6d8672681273d6633d8044a,"
                + "7ce0d3f0203587f085fcd3d5a9c3ba72cbf7eef5a4771cfb14415f01618cc831,"
                + "3044022060498ba4ae336e76924d8d047c9991f873ebd21ee6d8672681273d66"
                + "33d8044a02207ce0d3f0203587f085fcd3d5a9c3ba72cbf7eef5a4771cfb14415f01618cc831"
    })
    void encodesAsn1DerAsExpected(final String hexedR, final String hexedS, final String hexedDerSig) {
        final var r = CommonUtils.unhex(hexedR);
        final var s = CommonUtils.unhex(hexedS);
        final var rawSig = new byte[64];
        System.arraycopy(r, 0, rawSig, 0, 32);
        System.arraycopy(s, 0, rawSig, 32, 32);

        final var derSig = CommonUtils.unhex(hexedDerSig);
        assertArrayEquals(derSig, EcdsaSecp256k1Verifier.asn1DerEncode(rawSig), "derSigs should match");
    }

    @ParameterizedTest
    @CsvSource({
        "000000,00",
        "00701234,701234",
        "60701234,60701234",
        "0000801234,00801234",
        "801234,00801234",
    })
    void minifiesPositiveBigEndianAsExpected(final String hexedUnsignedV, final String hexedAns) {
        final var v = CommonUtils.unhex(hexedUnsignedV);
        final var ans = CommonUtils.unhex(hexedAns);

        assertArrayEquals(
                ans,
                EcdsaSecp256k1Verifier.minifiedPositiveBigEndian(v),
                "minifiedPositiveBigEndian values should match");
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
