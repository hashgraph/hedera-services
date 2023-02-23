/*
 * Copyright (C) 2022-2023 Hedera Hashgraph, LLC
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

import static com.hedera.node.app.hapi.utils.ethereum.EthTxData.EthTransactionType.LEGACY_ETHEREUM;
import static com.hedera.node.app.hapi.utils.ethereum.EthTxData.SECP256K1_EC_COMPRESSED;
import static com.hedera.node.app.service.evm.utils.EthSigsUtils.recoverAddressFromPubKey;
import static org.hyperledger.besu.nativelib.secp256k1.LibSecp256k1.CONTEXT;
import static org.hyperledger.besu.nativelib.secp256k1.LibSecp256k1.secp256k1_ecdsa_recover;
import static org.hyperledger.besu.nativelib.secp256k1.LibSecp256k1.secp256k1_ecdsa_recoverable_signature_parse_compact;

import com.esaulpaugh.headlong.rlp.RLPEncoder;
import com.esaulpaugh.headlong.util.Integers;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.MoreObjects;
import com.sun.jna.ptr.IntByReference;
import com.sun.jna.ptr.LongByReference;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.util.Arrays;
import org.apache.commons.codec.binary.Hex;
import org.bouncycastle.jcajce.provider.digest.Keccak;
import org.hyperledger.besu.nativelib.secp256k1.LibSecp256k1;

public record EthTxSigs(byte[] publicKey, byte[] address) {

    public static EthTxSigs extractSignatures(EthTxData ethTx) {
        byte[] message = calculateSignableMessage(ethTx);
        var pubKey = extractSig(ethTx.recId(), ethTx.r(), ethTx.s(), message);
        byte[] address = recoverAddressFromPubKey(pubKey);
        byte[] compressedKey = recoverCompressedPubKey(pubKey);

        return new EthTxSigs(compressedKey, address);
    }

    public static EthTxData signMessage(EthTxData ethTx, byte[] privateKey) {
        byte[] signableMessage = calculateSignableMessage(ethTx);
        final LibSecp256k1.secp256k1_ecdsa_recoverable_signature signature =
                new LibSecp256k1.secp256k1_ecdsa_recoverable_signature();
        LibSecp256k1.secp256k1_ecdsa_sign_recoverable(
                CONTEXT, signature, new Keccak.Digest256().digest(signableMessage), privateKey, null, null);

        final ByteBuffer compactSig = ByteBuffer.allocate(64);
        final IntByReference recId = new IntByReference(0);
        LibSecp256k1.secp256k1_ecdsa_recoverable_signature_serialize_compact(
                LibSecp256k1.CONTEXT, compactSig, recId, signature);
        compactSig.flip();
        final byte[] sig = compactSig.array();

        // wrap in signature object
        final byte[] r = new byte[32];
        System.arraycopy(sig, 0, r, 0, 32);
        final byte[] s = new byte[32];
        System.arraycopy(sig, 32, s, 0, 32);

        BigInteger v;
        if (ethTx.type() == LEGACY_ETHEREUM) {
            if (ethTx.chainId() == null || ethTx.chainId().length == 0) {
                v = BigInteger.valueOf(27L + recId.getValue());
            } else {
                v = BigInteger.valueOf(35L + recId.getValue())
                        .add(new BigInteger(1, ethTx.chainId()).multiply(BigInteger.TWO));
            }
        } else {
            v = null;
        }

        return new EthTxData(
                ethTx.rawTx(),
                ethTx.type(),
                ethTx.chainId(),
                ethTx.nonce(),
                ethTx.gasPrice(),
                ethTx.maxPriorityGas(),
                ethTx.maxGas(),
                ethTx.gasLimit(),
                ethTx.to(),
                ethTx.value(),
                ethTx.callData(),
                ethTx.accessList(),
                (byte) recId.getValue(),
                v == null ? null : v.toByteArray(),
                r,
                s);
    }

    static byte[] calculateSignableMessage(EthTxData ethTx) {
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
            case EIP2930 -> throw new IllegalArgumentException("Unsupported transaction type " + ethTx.type());
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
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        final EthTxSigs ethTxSigs = (EthTxSigs) o;

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
