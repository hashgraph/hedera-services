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

package com.hedera.node.app.hapi.utils.ethereum;

import static com.hedera.node.app.hapi.utils.EthSigsUtils.recoverAddressFromPubKey;
import static com.hedera.node.app.hapi.utils.ethereum.EthTxData.SECP256K1_EC_COMPRESSED;
import static org.hyperledger.besu.nativelib.secp256k1.LibSecp256k1.CONTEXT;
import static org.hyperledger.besu.nativelib.secp256k1.LibSecp256k1.secp256k1_ecdsa_recover;
import static org.hyperledger.besu.nativelib.secp256k1.LibSecp256k1.secp256k1_ecdsa_recoverable_signature_parse_compact;

import com.esaulpaugh.headlong.rlp.RLPEncoder;
import com.esaulpaugh.headlong.util.Integers;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.MoreObjects;
import com.sun.jna.ptr.LongByReference;
import java.nio.ByteBuffer;
import java.util.Arrays;
import org.apache.commons.codec.binary.Hex;
import org.bouncycastle.jcajce.provider.digest.Keccak;
import org.hyperledger.besu.nativelib.secp256k1.LibSecp256k1;

public record EthTxSigs(byte[] publicKey, byte[] address) {

    public static EthTxSigs extractSignatures(EthTxData ethTx) {
        final var message = calculateSignableMessage(ethTx);
        final var pubKey = extractSig(ethTx.recId(), ethTx.r(), ethTx.s(), message);
        final var address = recoverAddressFromPubKey(pubKey);
        final var compressedKey = recoverCompressedPubKey(pubKey);
        return new EthTxSigs(compressedKey, address);
    }

    public static byte[] calculateSignableMessage(EthTxData ethTx) {
        return switch (ethTx.type()) {
            case LEGACY_ETHEREUM -> (ethTx.chainId() != null && ethTx.chainId().length > 0)
                    ? RLPEncoder.encodeAsList(
                            Integers.toBytes(ethTx.nonce()),
                            ethTx.gasPrice(),
                            Integers.toBytes(ethTx.gasLimit()),
                            ethTx.to(),
                            Integers.toBytesUnsigned(ethTx.value()),
                            ethTx.callData(),
                            ethTx.chainId(),
                            Integers.toBytes(0),
                            Integers.toBytes(0))
                    : RLPEncoder.encodeAsList(
                            Integers.toBytes(ethTx.nonce()),
                            ethTx.gasPrice(),
                            Integers.toBytes(ethTx.gasLimit()),
                            ethTx.to(),
                            Integers.toBytesUnsigned(ethTx.value()),
                            ethTx.callData());
            case EIP1559 -> RLPEncoder.encodeSequentially(Integers.toBytes(2), new Object[] {
                ethTx.chainId(),
                Integers.toBytes(ethTx.nonce()),
                ethTx.maxPriorityGas(),
                ethTx.maxGas(),
                Integers.toBytes(ethTx.gasLimit()),
                ethTx.to(),
                Integers.toBytesUnsigned(ethTx.value()),
                ethTx.callData(),
                new Object[0]
            });
            case EIP2930 -> RLPEncoder.encodeSequentially(Integers.toBytes(1), new Object[] {
                ethTx.chainId(),
                Integers.toBytes(ethTx.nonce()),
                ethTx.gasPrice(),
                Integers.toBytes(ethTx.gasLimit()),
                ethTx.to(),
                Integers.toBytesUnsigned(ethTx.value()),
                ethTx.callData(),
                new Object[0]
            });
        };
    }

    static byte[] recoverCompressedPubKey(LibSecp256k1.secp256k1_pubkey pubKey) {
        final ByteBuffer recoveredFullKey = ByteBuffer.allocate(33);
        final LongByReference fullKeySize = new LongByReference(recoveredFullKey.limit());
        LibSecp256k1.secp256k1_ec_pubkey_serialize(
                CONTEXT, recoveredFullKey, fullKeySize, pubKey, SECP256K1_EC_COMPRESSED);
        return recoveredFullKey.array();
    }

    private static LibSecp256k1.secp256k1_pubkey extractSig(int recId, byte[] r, byte[] s, byte[] message) {
        byte[] dataHash = new Keccak.Digest256().digest(message);

        // The RLP library output won't include leading zeros, which means
        // a simple (r, s) concatenation breaks signature verification below
        byte[] signature = concatLeftPadded(r, s);

        final LibSecp256k1.secp256k1_ecdsa_recoverable_signature parsedSignature =
                new LibSecp256k1.secp256k1_ecdsa_recoverable_signature();

        if (secp256k1_ecdsa_recoverable_signature_parse_compact(CONTEXT, parsedSignature, signature, recId) == 0) {
            throw new IllegalArgumentException("Could not parse signature");
        }
        final LibSecp256k1.secp256k1_pubkey newPubKey = new LibSecp256k1.secp256k1_pubkey();
        if (secp256k1_ecdsa_recover(CONTEXT, newPubKey, parsedSignature, dataHash) == 0) {
            throw new IllegalArgumentException("Could not recover signature");
        } else {
            return newPubKey;
        }
    }

    @VisibleForTesting
    static byte[] concatLeftPadded(final byte[] r, final byte[] s) {
        byte[] signature = new byte[64];
        final var rLeadingZeros = 32 - r.length;
        System.arraycopy(r, 0, signature, rLeadingZeros, r.length);
        final var sLeadingZeros = 32 - s.length;
        System.arraycopy(s, 0, signature, 32 + sLeadingZeros, s.length);
        return signature;
    }

    @Override
    public boolean equals(final Object other) {
        if (this == other) return true;
        if (other == null || getClass() != other.getClass()) return false;

        final EthTxSigs ethTxSigs = (EthTxSigs) other;

        if (!Arrays.equals(publicKey, ethTxSigs.publicKey)) return false;
        return Arrays.equals(address, ethTxSigs.address);
    }

    @Override
    public int hashCode() {
        int result = Arrays.hashCode(publicKey);
        result = 31 * result + Arrays.hashCode(address);
        return result;
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
                .add("publicKey", Hex.encodeHexString(publicKey))
                .add("address", Hex.encodeHexString(address))
                .toString();
    }
}
