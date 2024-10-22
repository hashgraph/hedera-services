/*
 * Copyright (C) 2022-2024 Hedera Hashgraph, LLC
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

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.swirlds.common.AddressBook;
import com.swirlds.common.crypto.KeyType;
import com.swirlds.common.crypto.Signature;
import com.swirlds.common.platform.NodeId;
import com.swirlds.common.test.fixtures.crypto.PreGeneratedPublicKeys;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.security.PublicKey;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class KeysAndCertsTest {
    private static final byte[] DATA_ARRAY = {1, 2, 3};
    private static final Bytes DATA_BYTES = Bytes.wrap(DATA_ARRAY);
    private static final PublicKey WRONG_KEY =
            PreGeneratedPublicKeys.getPublicKey(KeyType.RSA, 0).getPublicKey();

    private void testSignVerify(final PlatformSigner signer, final PublicKey publicKey) {
        final Signature signature = signer.sign(DATA_ARRAY);

        assertTrue(
                CryptoStatic.verifySignature(DATA_BYTES, signature.getBytes(), publicKey),
                "verify should be true when using the correct public key");
        assertFalse(
                CryptoStatic.verifySignature(DATA_BYTES, signature.getBytes(), WRONG_KEY),
                "verify should be false when using the incorrect public key");
    }

    /**
     * Tests signing and verifying with provided {@link KeysAndCerts}
     *
     * @param addressBook
     * 		address book of the network
     * @param keysAndCerts
     * 		keys and certificates to use for testing
     */
    @ParameterizedTest
    @MethodSource({"com.swirlds.platform.crypto.CryptoArgsProvider#basicTestArgs"})
    void basicTest(@NonNull final AddressBook addressBook, @NonNull final Map<NodeId, KeysAndCerts> keysAndCerts) {
        Objects.requireNonNull(addressBook, "addressBook must not be null");
        Objects.requireNonNull(keysAndCerts, "keysAndCerts must not be null");
        // choose a random node to test
        final Random random = new Random();
        final int node = random.nextInt(addressBook.getSize());
        final NodeId nodeId = addressBook.getNodeId(node);

        final PlatformSigner signer = new PlatformSigner(keysAndCerts.get(nodeId));
        testSignVerify(signer, addressBook.getAddress(nodeId).getSigPublicKey());
        // test it twice to verify that the signer is reusable
        testSignVerify(signer, addressBook.getAddress(nodeId).getSigPublicKey());
    }
}
