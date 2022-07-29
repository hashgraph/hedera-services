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
package com.hedera.services.keys;

import com.hedera.services.legacy.core.jproto.JKey;
import com.swirlds.common.crypto.TransactionSignature;
import java.util.function.BiPredicate;
import java.util.function.Function;

public interface ActivationTest {
    /**
     * Checks if the given Hedera key has an active top-level signature, using:
     *
     * <ol>
     *   <li>The provided {@code sigsFn} to map cryptographic public keys such as Ed25519 or
     *       ECDSA(secp256k1) into {@link TransactionSignature} objects; and,
     *   <li>The provided {@code validityTest} to check if a primitive keys (either cryptographic or
     *       contract) has a valid signature.
     * </ol>
     *
     * @param key the key whose activation to test
     * @param sigsFn a mapping from public keys to cryptographic signatures
     * @param validityTest a test for validity of the cryptographic signature for a primitive key
     * @return whether the Hedera key is active
     */
    boolean test(
            JKey key,
            Function<byte[], TransactionSignature> sigsFn,
            BiPredicate<JKey, TransactionSignature> validityTest);
}
