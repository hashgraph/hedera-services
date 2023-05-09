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

package com.hedera.node.app.signature.impl;

import static java.util.Objects.requireNonNull;

import com.hedera.hapi.node.base.Key;
import com.hedera.node.app.signature.ExpandedSignaturePair;
import com.hedera.node.app.signature.SignatureVerificationFuture;
import com.hedera.node.app.signature.SignatureVerifier;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.swirlds.common.crypto.Cryptography;
import com.swirlds.common.crypto.TransactionSignature;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import javax.inject.Inject;

/**
 * A concrete implementation of {@link SignatureVerifier} that uses the {@link Cryptography} engine to verify the
 * signatures.
 */
public class SignatureVerifierImpl implements SignatureVerifier {

    /** The {@link Cryptography} engine to use for signature verification. */
    private final Cryptography cryptoEngine;

    /** Create a new instance with the given {@link Cryptography} engine. */
    @Inject
    public SignatureVerifierImpl(@NonNull final Cryptography cryptoEngine) {
        this.cryptoEngine = requireNonNull(cryptoEngine);
    }

    @NonNull
    @Override
    public Map<Key, SignatureVerificationFuture> verify(
            @NonNull final Bytes signedBytes, @NonNull final Set<ExpandedSignaturePair> sigs) {
        requireNonNull(signedBytes);
        requireNonNull(sigs);

        // The Hashgraph Platform crypto engine takes a list of TransactionSignature objects to verify. Each of these
        // is fed a byte array of the signed bytes, the public key, the signature, and the signature type, with
        // appropriate offsets. Rather than many small arrays, we're going to create one big array and reuse it
        // across all TransactionSignature objects. It gives a slight savings by having the signed bytes only copied
        // to the array one time.
        final var bytesPerSig = 64;
        final var bytesPerKey = 64; // 64 for ECDSA_SECP256K1, less for ED25519
        final var content = new byte[(int) signedBytes.length() + sigs.size() * (bytesPerSig + bytesPerKey)];
        int offset = add(content, 0, signedBytes);

        final var platformSigs = new ArrayList<TransactionSignature>(sigs.size());
        final var futures = new HashMap<Key, SignatureVerificationFuture>(sigs.size());
        for (ExpandedSignaturePair sigPair : sigs) {
            final Bytes sigBytes = sigPair.signature();
            final var sigBytesOffset = offset;
            offset = add(content, offset, sigBytes);

            final Bytes keyBytes = sigPair.keyBytes();
            final var keyBytesOffset = offset;
            offset = add(content, offset, keyBytes);
            final var platformSig = new TransactionSignature(
                    content, sigBytesOffset, (int) sigBytes.length(), keyBytesOffset, (int) keyBytes.length(), 0, (int)
                            signedBytes.length());
            platformSigs.add(platformSig);
            futures.put(
                    sigPair.key(),
                    new SignatureVerificationFutureImpl(sigPair.key(), sigPair.hollowAccount(), platformSig));
        }

        cryptoEngine.verifyAsync(platformSigs);

        return futures;
    }

    private int add(byte[] content, int offset, Bytes bytes) {
        bytes.getBytes(0, content, offset, (int) bytes.length());
        return offset + (int) bytes.length();
    }
}
