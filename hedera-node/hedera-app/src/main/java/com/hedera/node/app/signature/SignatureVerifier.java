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

package com.hedera.node.app.signature;

import com.hedera.hapi.node.base.Key;
import com.hedera.hapi.node.base.SignaturePair;
import com.hedera.hapi.node.state.token.Account;
import com.hedera.node.app.spi.signatures.SignatureVerification;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.List;
import java.util.concurrent.Future;

/**
 * Asynchronously verifies signatures on a transaction given a set of keys.
 */
public interface SignatureVerifier {
    /**
     * Asynchronously verifies that there exists in {@code sigPairs} a {@link SignaturePair} such that it both matches
     * the given {@code key} AND matches the {@code signedBytes}.
     *
     * @param signedBytes The signed bytes to verify
     * @param sigPairs The list of {@link SignaturePair}s, at least one of which must have signed {@code signedBytes}
     *                 and have a prefix matching the given {@code key}
     * @param key The key that must have signed the bytes
     * @return A {@link Future} indicating whether the {code signedBytes} were signed by the {@code key}.
     */
    @NonNull
    Future<SignatureVerification> verify(
            @NonNull Bytes signedBytes, @NonNull List<SignaturePair> sigPairs, @NonNull Key key);

    /**
     * Asynchronously verifies that there exists in {@code sigPairs} a {@link SignaturePair} such that it both matches
     * the given account's {@code evmAddress} AND matches the {@code signedBytes}.
     *
     * <p>An ECDSA(secp256k1) key has the unusual property that the public key can be extracted by the combination of
     * the signature and the signed bytes. Our transactions are unique in that they provide for a "key prefix" in the
     * {@link SignaturePair}. An EVM address is the last 20 bytes of the Keccak hash of the public key.
     *
     * <p>Given an {@code evmAddress}, this method will first look to see if there is a {@link SignaturePair} with an
     * ECDSA(secp256k1) key that, when hashed and trimmed, exactly matches the {@code evmAddress}. If so, it will use
     * this key. If not, it will then check to see whether there is a {@link SignaturePair} with a key that can be
     * extracted (similar to how the EVM has {@code `ecrecover`}). Taking that key, and hashing and trimming it, if it
     * matches the alias, then it will be used for signature verification.
     *
     * @param signedBytes The signed bytes to verify
     * @param sigPairs The list of {@link SignaturePair}s, at least one of which must have signed {@code signedBytes}
     *                 and have a prefix matching the given {@code key}
     * @param hollowAccount The hollow account with an EVM address to use
     * @return A {@link Future} indicating whether the {code signedBytes} were signed by a {@code key} corresponding to
     *        the given {@code evmAddress}.
     */
    @NonNull
    Future<SignatureVerification> verify(
            @NonNull Bytes signedBytes, @NonNull List<SignaturePair> sigPairs, @NonNull Account hollowAccount);
}
