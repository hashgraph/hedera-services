/*
 * Copyright (C) 2021-2022 Hedera Hashgraph, LLC
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

import org.bouncycastle.jcajce.provider.digest.Keccak;
import org.bouncycastle.jce.ECNamedCurveTable;
import org.bouncycastle.jce.spec.ECNamedCurveParameterSpec;
import org.bouncycastle.math.ec.ECCurve;

public class MiscCryptoUtils {
    private MiscCryptoUtils() {
        throw new UnsupportedOperationException("Utility Class");
    }

    private static final ECNamedCurveParameterSpec secp256k1 =
            ECNamedCurveTable.getParameterSpec("secp256k1");
    private static final ECCurve curveSecp256k1 = secp256k1.getCurve();

    public static byte[] keccak256DigestOf(final byte[] msg) {
        return new Keccak.Digest256().digest(msg);
    }

    /**
     * Given a 33-byte compressed ECDSA(secp256k1) public key, returns the uncompressed key as a
     * 64-byte array whose first 32 bytes are the x-coordinate of the key and second 32 bytes are
     * the y-coordinate of the key.
     *
     * @param compressedKey a compressed ECDSA(secp256k1) public key
     * @return the raw bytes of the public key coordinates
     */
    public static byte[] decompressSecp256k1(final byte[] compressedKey) {
        final var pk = curveSecp256k1.decodePoint(compressedKey);

        final var rawKey = new byte[64];
        System.arraycopy(pk.getRawXCoord().getEncoded(), 0, rawKey, 0, 32);
        System.arraycopy(pk.getRawYCoord().getEncoded(), 0, rawKey, 32, 32);
        return rawKey;
    }
}
