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

package com.hedera.node.app.service.mono.sigs.utils;

import static com.hedera.node.app.service.mono.utils.EntityIdUtils.EVM_ADDRESS_SIZE;

import java.util.Arrays;
import org.bouncycastle.jcajce.provider.digest.Keccak;
import org.bouncycastle.jce.ECNamedCurveTable;
import org.bouncycastle.jce.spec.ECNamedCurveParameterSpec;
import org.bouncycastle.math.ec.ECCurve;

public class MiscCryptoUtils {
    private MiscCryptoUtils() {
        throw new UnsupportedOperationException("Utility Class");
    }

    private static final ECNamedCurveParameterSpec secp256k1 = ECNamedCurveTable.getParameterSpec("secp256k1");
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

    /**
     * Given a 64-byte decompressed ECDSA(secp256k1) public key, returns the compressed key as a
     * 33-byte array whose first byte is the parity of the y coordinate and the following 32 bytes
     * are the x-coordinate of the key.
     *
     * @param decompressedKey a decompressed ECDSA(secp256k1) public key
     * @return the raw bytes of the compressed public key
     */
    public static byte[] compressSecp256k1(final byte[] decompressedKey) {
        final var decompressedBytes = new byte[65];
        decompressedBytes[0] = 0x04;
        System.arraycopy(decompressedKey, 0, decompressedBytes, 1, 32);
        System.arraycopy(decompressedKey, 32, decompressedBytes, 33, 32);
        return curveSecp256k1.decodePoint(decompressedBytes).getEncoded(true);
    }

    /**
     * Given a 64-byte decompressed ECDSA(secp256k1) public key, returns the evm address
     * derived from the last 20 bytes of the keccak256 hash of the public key.
     *
     * @param decompressedKey a decompressed ECDSA(secp256k1) public key
     * @return the raw bytes of the evm address derived from that key
     */
    public static byte[] extractEvmAddressFromDecompressedECDSAKey(final byte[] decompressedKey) {
        final var publicKeyHash = MiscCryptoUtils.keccak256DigestOf(decompressedKey);
        return Arrays.copyOfRange(publicKeyHash, publicKeyHash.length - EVM_ADDRESS_SIZE, publicKeyHash.length);
    }
}
