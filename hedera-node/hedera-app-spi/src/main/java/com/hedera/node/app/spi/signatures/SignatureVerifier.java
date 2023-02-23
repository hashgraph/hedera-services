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

import com.hedera.node.app.spi.service.Service;
import com.hedera.node.app.spi.key.HederaKey;
import edu.umd.cs.findbugs.annotations.NonNull;

/**
 * Allows either a workflow or a {@link Service} to test if a {@link HederaKey} has signed at the
 * current point in the workflow.
 *
 * <p>There are three kinds of "checkpoints" where we test signatures:
 *
 * <ol>
 *   <li>Before submitting or executing a top-level HAPI transaction.
 *   <li>Before triggering the execution of a scheduled transaction.
 *   <li>Before executing a EVM system contract.
 * </ol>
 *
 * Note that workflows are solely responsible for enforcing the first checkpoint, while {@link
 * Service} business logic must detect and enforce the second and third checkpoints.
 */
public interface SignatureVerifier {
    /**
     * Returns whether the given key has signed at the current checkpoint.
     *
     * @param key the key to test
     * @return whether the key has signed
     */
    boolean hasSigned(@NonNull HederaKey key);

    /**
     * Returns whether the given key has signed at the current checkpoint, giving priority to
     * verdicts from the given {@link OverrideSignatureVerifier}. That is, if the given {@link
     * OverrideSignatureVerifier} returns either {@link
     * com.swirlds.common.crypto.VerificationStatus#VALID} or {@link
     * com.swirlds.common.crypto.VerificationStatus#INVALID}, then that verdict is returned.
     * Otherwise, this {@link SignatureVerifier} gives the final verdict.
     *
     * @param key the key to test
     * @param override the {@link OverrideSignatureVerifier} to consult first
     * @return whether the key has signed
     */
    boolean hasSigned(@NonNull HederaKey key, @NonNull OverrideSignatureVerifier override);
}
