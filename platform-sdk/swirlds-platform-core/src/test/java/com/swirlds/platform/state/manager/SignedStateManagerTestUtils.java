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

package com.swirlds.platform.state.manager;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.swirlds.common.crypto.Hash;
import com.swirlds.common.crypto.Signature;
import com.swirlds.common.stream.HashSigner;
import java.security.PublicKey;
import java.util.Arrays;
import java.util.Objects;
import org.mockito.invocation.InvocationOnMock;

/**
 * Utility methods for testing {@link com.swirlds.platform.state.signed.SignedStateManager}
 */
public class SignedStateManagerTestUtils {

    /**
     * Build a fake signature. The signature acts like a correct signature for the given key/hash,
     * and acts like an invalid signature for any other key/hash. If either the key or hash is
     * set to null then the signature will always fail (this is sometimes desired).
     */
    public static Signature buildFakeSignature(final PublicKey key, final Hash hash) {

        final Signature signature = mock(Signature.class);

        when(signature.verifySignature(any(byte[].class), any(PublicKey.class)))
                .thenAnswer((final InvocationOnMock invocation) -> {
                    final byte[] data = invocation.getArgument(0);
                    final PublicKey publicKey = invocation.getArgument(1);

                    return Arrays.equals(hash.getValue(), data) && Objects.equals(publicKey, key);
                });

        return signature;
    }

    /**
     * Build a signature that is always considered to be valid.
     */
    public static Signature buildReallyFakeSignature() {
        final Signature signature = mock(Signature.class);

        when(signature.verifySignature(any(byte[].class), any(PublicKey.class)))
                .thenAnswer((final InvocationOnMock invocation) -> true);

        return signature;
    }

    /**
     * Create a HashSigner that always returns valid (fake) signatures.
     */
    public static HashSigner buildFakeHashSigner(final PublicKey key) {
        return hash -> buildFakeSignature(key, hash);
    }
}
