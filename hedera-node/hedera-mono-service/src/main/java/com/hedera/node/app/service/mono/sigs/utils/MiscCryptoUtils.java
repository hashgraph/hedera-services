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

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.HexFormat;

import com.sun.jna.ptr.LongByReference;
import org.bouncycastle.jcajce.provider.digest.Keccak;
import org.bouncycastle.jce.ECNamedCurveTable;
import org.bouncycastle.jce.spec.ECNamedCurveParameterSpec;
import org.bouncycastle.math.ec.ECCurve;
import org.hyperledger.besu.nativelib.secp256k1.LibSecp256k1;

public class MiscCryptoUtils {
    private MiscCryptoUtils() {
        throw new UnsupportedOperationException("Utility Class");
    }

    public static byte[] keccak256DigestOf(final byte[] msg) {
        return new Keccak.Digest256().digest(msg);
    }

    private static final LongByReference OUTPUT_LENGTH_65 = new LongByReference(65);
    private static final LongByReference OUTPUT_LENGTH_33 = new LongByReference(33);

    // Thread local caches to avoid allocating memory for each verification. They will leak memory for each thread used
    // for verification but only just over 100 bytes to totally worth it.
    private static final ThreadLocal<LibSecp256k1.secp256k1_pubkey> PUB_KEY_CACHE =
            ThreadLocal.withInitial(LibSecp256k1.secp256k1_pubkey::new);
    private static final ThreadLocal<byte[]> UNCOMPRESSED_PUBLIC_KEY_INPUT_CACHE =
            ThreadLocal.withInitial(() -> {
                byte[] publicKeyInput = new byte[65];
                publicKeyInput[0] = 0x04;
                return publicKeyInput;
            });
    private static final ThreadLocal<ByteBuffer> UNCOMPRESSED_PUBLIC_KEY_BYTEBUFFER_CACHE =
            ThreadLocal.withInitial(() -> ByteBuffer.allocate(65));

    /**
     * Given a 33-byte compressed ECDSA(secp256k1) public key, returns the uncompressed key as a
     * 64-byte array whose first 32 bytes are the x-coordinate of the key and second 32 bytes are
     * the y-coordinate of the key.
     *
     * @param compressedKey a compressed ECDSA(secp256k1) public key
     * @return the raw bytes of the public key coordinates
     */
    public static byte[] decompressSecp256k1(final byte[] compressedKey) {
        // convert public key to native format
        final LibSecp256k1.secp256k1_pubkey pubkey = PUB_KEY_CACHE.get();
        final int keyParseResult = LibSecp256k1.secp256k1_ec_pubkey_parse(
                LibSecp256k1.CONTEXT,
                pubkey,
                compressedKey,
                compressedKey.length);
        if (keyParseResult != 1) throw new RuntimeException("Failed to parse public key");
        final ByteBuffer outputBuffer = UNCOMPRESSED_PUBLIC_KEY_BYTEBUFFER_CACHE.get();
        final int keySerializeResult = LibSecp256k1.secp256k1_ec_pubkey_serialize(
                LibSecp256k1.CONTEXT,
                outputBuffer,
                OUTPUT_LENGTH_65,
                pubkey,
                LibSecp256k1.SECP256K1_EC_UNCOMPRESSED);
        if (keySerializeResult != 1) throw new RuntimeException("Failed to serialize public key");
        // chop off header first byte
        final var rawKey = new byte[64];
        outputBuffer.get(1,rawKey);
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
        // add header byte on to decompressed key
        final var decompressedBytes = UNCOMPRESSED_PUBLIC_KEY_INPUT_CACHE.get();
        System.arraycopy(decompressedKey, 0, decompressedBytes, 1, 64);;
        // convert public key to native format
        final LibSecp256k1.secp256k1_pubkey pubkey = PUB_KEY_CACHE.get();
        final int keyParseResult = LibSecp256k1.secp256k1_ec_pubkey_parse(
                LibSecp256k1.CONTEXT,
                pubkey,
                decompressedBytes,
                decompressedBytes.length);
        if (keyParseResult != 1) throw new RuntimeException("Failed to parse public key");
        // serialize public key to compressed format
        final ByteBuffer outputBuffer = ByteBuffer.allocate(33);
        final int keySerializeResult = LibSecp256k1.secp256k1_ec_pubkey_serialize(
                LibSecp256k1.CONTEXT,
                outputBuffer,
                OUTPUT_LENGTH_33,
                pubkey,
                LibSecp256k1.SECP256K1_EC_COMPRESSED);
        if (keySerializeResult != 1) throw new RuntimeException("Failed to serialize public key");
        return outputBuffer.array();
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
