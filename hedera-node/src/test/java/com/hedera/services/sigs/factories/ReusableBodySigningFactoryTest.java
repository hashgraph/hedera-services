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
package com.hedera.services.sigs.factories;

import static com.hedera.services.sigs.factories.PlatformSigFactoryTest.EXPECTED_SIG;
import static com.hedera.services.sigs.factories.PlatformSigFactoryTest.data;
import static com.hedera.services.sigs.factories.PlatformSigFactoryTest.pk;
import static com.hedera.services.sigs.factories.PlatformSigFactoryTest.sig;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.BDDMockito.given;

import com.hedera.services.sigs.utils.MiscCryptoUtils;
import com.hedera.services.utils.accessors.TxnAccessor;
import com.hedera.test.factories.keys.KeyFactory;
import com.swirlds.common.crypto.SignatureType;
import com.swirlds.common.crypto.TransactionSignature;
import java.util.Arrays;
import org.bouncycastle.crypto.params.ECPublicKeyParameters;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ReusableBodySigningFactoryTest {
    @Mock private TxnAccessor accessor;

    private ReusableBodySigningFactory subject = new ReusableBodySigningFactory();

    @Test
    void resetWorks() {
        // when:
        subject.resetFor(accessor);

        // then:
        assertSame(accessor, subject.getAccessor());
    }

    @Test
    void createsExpectedBodySigGivenInjectedAccessor() {
        given(accessor.getTxnBytes()).willReturn(data);

        subject = new ReusableBodySigningFactory(accessor);

        final var actualSig = subject.signBodyWithEd25519(pk, sig);

        Assertions.assertEquals(EXPECTED_SIG, actualSig);
    }

    @Test
    void createsExpectedBodySig() {
        given(accessor.getTxnBytes()).willReturn(data);

        // when:
        subject.resetFor(accessor);
        // and:
        final var actualSig = subject.signBodyWithEd25519(pk, sig);

        // then:
        Assertions.assertEquals(EXPECTED_SIG, actualSig);
    }

    @Test
    void createsExpectedKeccak256Sig() {
        final var kp = KeyFactory.ecdsaKpGenerator.generateKeyPair();
        final var q = ((ECPublicKeyParameters) kp.getPublic()).getQ();
        final var compressed = q.getEncoded(true);
        final var uncompressed = Arrays.copyOfRange(q.getEncoded(false), 1, 65);

        given(accessor.getTxnBytes()).willReturn(data);

        final var digest = MiscCryptoUtils.keccak256DigestOf(data);

        final var expectedSig = expectedEcdsaSecp256k1Sig(uncompressed);

        subject.resetFor(accessor);
        final var actualSig = subject.signKeccak256DigestWithSecp256k1(compressed, sig);

        Assertions.assertEquals(expectedSig, actualSig);

        assertArrayEquals(digest, subject.getKeccak256Digest());
        subject.resetFor(accessor);
        assertNull(subject.getKeccak256Digest());
    }

    private TransactionSignature expectedEcdsaSecp256k1Sig(final byte[] pk) {
        final var digest = MiscCryptoUtils.keccak256DigestOf(data);
        final var expectedContents = new byte[digest.length + sig.length];
        System.arraycopy(sig, 0, expectedContents, 0, sig.length);
        System.arraycopy(digest, 0, expectedContents, sig.length, digest.length);
        return new TransactionSignature(
                expectedContents,
                0,
                sig.length,
                pk,
                0,
                pk.length,
                sig.length,
                digest.length,
                SignatureType.ECDSA_SECP256K1);
    }
}
