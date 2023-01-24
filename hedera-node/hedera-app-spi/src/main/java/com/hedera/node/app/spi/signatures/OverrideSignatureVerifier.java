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
package com.hedera.node.app.spi.signatures;

import com.hedera.node.app.spi.key.HederaKey;
import com.swirlds.common.crypto.VerificationStatus;
import edu.umd.cs.findbugs.annotations.NonNull;

/**
 * Given a non-cryptographic key, returns whether it should be considered to have signed at the
 * current checkpoint.
 *
 * <p>The EVM system contracts need this interface to allow the {@link SignatureVerifier} to
 * correctly test keys of type {@code contractID} and {@code delegatable_contract_id}.
 *
 * <p>The scheduled transaction service needs this interface to allow the {@link SignatureVerifier}
 * to detect valid signatures already added to the scheduled transaction, perhaps long ago, and not
 * present in the current transaction's {@code SignatureMap}.
 *
 * <p>
 */
@FunctionalInterface
public interface OverrideSignatureVerifier {
    /**
     * Given a key, returns whether it should be considered to have signed at the current
     * checkpoint. Implementations are free to return {@link VerificationStatus#UNKNOWN} if they
     * cannot determine the answer.
     *
     * @param key the key to test
     * @return whether the key has signed, if known
     */
    @NonNull
    VerificationStatus hasSigned(@NonNull HederaKey key);
}
