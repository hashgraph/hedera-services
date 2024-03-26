/*
 * Copyright (C) 2023-2024 Hedera Hashgraph, LLC
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

import com.swirlds.common.crypto.Hash;
import com.swirlds.common.crypto.Signature;
import com.swirlds.common.crypto.SignatureType;
import com.swirlds.platform.state.signed.StateSignatureCollector;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.security.PublicKey;
import java.util.Arrays;

/**
 * Utility methods for testing {@link StateSignatureCollector}
 */
public class SignatureVerificationTestUtils {

    /**
     * Build a fake signature. The signature acts like a correct signature for the given key/hash, and acts like an
     * invalid signature for any other key/hash. If either the key or hash is set to null then the signature will always
     * fail (this is sometimes desired).
     */
    public static Signature buildFakeSignature(final PublicKey key, final Hash hash) {
        return new Signature(SignatureType.RSA, concat(key.getEncoded(), hash.getValue()));
    }

    public static boolean verifySignature(
            @NonNull final byte[] data,
            @NonNull final byte[] signature,
            @NonNull final PublicKey publicKey) {
        return Arrays.equals(concat(publicKey.getEncoded(), data), signature);
    }

    private static byte[] concat(final byte[] first, final byte[] second) {
        final byte[] result = Arrays.copyOf(first, first.length + second.length);
        System.arraycopy(second, 0, result, first.length, second.length);
        return result;
    }
}
