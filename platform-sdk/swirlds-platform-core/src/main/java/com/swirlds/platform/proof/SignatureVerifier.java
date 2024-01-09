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

package com.swirlds.platform.proof;

import com.swirlds.common.crypto.Signature;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.security.PublicKey;

/**
 * A method that verifies a signature.
 */
@FunctionalInterface
public interface SignatureVerifier {

    /**
     * Check if a signature is valid.
     *
     * @param signature the signature to check
     * @param bytes     the data that was signed
     * @param publicKey the public key corresponding to the private key that signed the data
     * @return true if the signature is valid, false otherwise
     */
    boolean verifySignature(@NonNull Signature signature, @NonNull byte[] bytes, @NonNull PublicKey publicKey);
}
