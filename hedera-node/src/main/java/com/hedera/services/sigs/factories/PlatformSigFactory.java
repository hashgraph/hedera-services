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

import static java.util.stream.Collectors.joining;

import com.swirlds.common.crypto.SignatureType;
import com.swirlds.common.crypto.TransactionSignature;
import com.swirlds.common.utility.CommonUtils;
import java.util.Arrays;
import java.util.List;

/**
 * Provides a static method to create {@link com.swirlds.common.crypto.Signature} instances from the
 * raw bytes constituting a public key, cryptographic signature, and signed data.
 */
public final class PlatformSigFactory {
    private PlatformSigFactory() {
        throw new UnsupportedOperationException("Utility Class");
    }

    /**
     * Combine raw bytes into a syntactically valid ed25519 {@link
     * com.swirlds.common.crypto.Signature}.
     *
     * @param pk bytes of the ed25519 public key.
     * @param sig bytes of the cryptographic signature.
     * @param data bytes of the data claimed to have been signed.
     * @return the platform signature representing the collective input parameters.
     */
    public static TransactionSignature ed25519Sig(
            final byte[] pk, final byte[] sig, final byte[] data) {
        return sig(pk, sig, data, SignatureType.ED25519);
    }

    /**
     * Combine raw bytes into a syntactically valid ECDSA(secp256k1) {@link
     * com.swirlds.common.crypto.Signature}.
     *
     * @param pk uncompressed bytes of the ECDSA(secp256k1) public key
     * @param sig bytes of the cryptographic signature.
     * @param data bytes of the data claimed to have been signed.
     * @return the platform signature representing the collective input parameters.
     */
    public static TransactionSignature ecdsaSecp256k1Sig(
            final byte[] pk, final byte[] sig, final byte[] data) {
        return sig(pk, sig, data, SignatureType.ECDSA_SECP256K1);
    }

    private static TransactionSignature sig(
            final byte[] pk, final byte[] sig, final byte[] data, final SignatureType type) {
        final var contents = new byte[sig.length + data.length];
        System.arraycopy(sig, 0, contents, 0, sig.length);
        System.arraycopy(data, 0, contents, sig.length, data.length);
        return new TransactionSignature(
                contents, 0, sig.length, pk, 0, pk.length, sig.length, data.length, type);
    }

    public static boolean varyingMaterialEquals(
            final TransactionSignature a, final TransactionSignature b) {
        if (!Arrays.equals(a.getExpandedPublicKeyDirect(), b.getExpandedPublicKeyDirect())) {
            return false;
        }
        final var aOffset = a.getSignatureOffset();
        final var aLen = a.getSignatureLength();
        final var bOffset = b.getSignatureOffset();
        final var bLen = b.getSignatureLength();
        return Arrays.equals(
                a.getContentsDirect(),
                aOffset,
                aOffset + aLen,
                b.getContentsDirect(),
                bOffset,
                bOffset + bLen);
    }

    public static boolean allVaryingMaterialEquals(
            final List<TransactionSignature> aSigs, final List<TransactionSignature> bSigs) {
        final var n = aSigs.size();
        if (n != bSigs.size()) {
            return false;
        }
        for (int i = 0; i < n; i++) {
            if (!varyingMaterialEquals(aSigs.get(i), bSigs.get(i))) {
                return false;
            }
        }

        return true;
    }

    public static String pkSigRepr(final List<TransactionSignature> sigs) {
        return sigs.stream()
                .map(
                        sig ->
                                String.format(
                                        "(PK = %s | SIG = %s | %s)",
                                        CommonUtils.hex(sig.getExpandedPublicKeyDirect()),
                                        CommonUtils.hex(
                                                Arrays.copyOfRange(
                                                        sig.getContentsDirect(),
                                                        sig.getSignatureOffset(),
                                                        sig.getSignatureOffset()
                                                                + sig.getSignatureLength())),
                                        sig.getSignatureStatus()))
                .collect(joining(", "));
    }
}
