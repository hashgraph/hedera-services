/*
 * Copyright (C) 2024 Hedera Hashgraph, LLC
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

package com.hedera.node.app.spi.signatures;

import com.hedera.hapi.node.base.Key;
import com.hedera.hapi.node.base.SignatureMap;
import com.hedera.hapi.node.base.SignaturePair;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.util.function.Function;

/**
 * Verifies whether a {@link Key} has a valid signature for a given {@link Bytes} payload based on the cryptographic
 * signatures given in a {@link SignatureMap} and, optionally, an override strategy for verifying simple key signatures.
 * <p>
 * The cryptographic signatures in the {@link SignatureMap} may be either Ed25519 or ECDSA(secp256k1) signatures. For
 * the latter, the bytes signed are taken as the Keccak-256 (SHA-2) hash of the payload; while Ed25519 signatures are
 * of the payload itself.
 */
public interface SignatureVerifier {
    /**
     * Verifies the signature of a {@link Key} for a given {@link Bytes} payload based on the cryptographic signatures
     * given in a {@link SignatureMap} and, optionally, an override strategy for verifying simple key signatures.
     * <p>
     * It is not necessary to include the full public key of a cryptographic key in the {@link SignatureMap}, as long
     * as the prefix given is enough to uniquely identify the key. This is because the {@link Key} structure will
     * contain the full public key, and the verifier will use this to verify the signature. If there are multiple
     * prefixes that match the same key, the verifier will use the first pair encountered when traversing the
     * {@link SignaturePair}s in the order given.
     *
     * @param key the key whose signature should be verified
     * @param bytes the payload to verify
     * @param signatureMap the cryptographic signatures to verify against
     * @param simpleKeyVerifier an optional override strategy for verifying simple key signatures
     * @return true if the signature is valid; false otherwise
     */
    boolean verifySignature(
            @NonNull Key key,
            @NonNull Bytes bytes,
            @NonNull SignatureMap signatureMap,
            @Nullable Function<Key, SimpleKeyVerification> simpleKeyVerifier);

    /**
     * Convenience method for getting the number of Ed25519 and ECDSA(secp256k1) keys in a {@link Key} structure,
     * useful for estimating the maximum amount of work that will be done in verifying the signature of the key.
     * @param key the key structure to count simple keys in
     * @return the number of Ed25519 and ECDSA(secp256k1) keys in the key
     */
    SimpleKeyCount countSimpleKeys(@NonNull Key key);
}
