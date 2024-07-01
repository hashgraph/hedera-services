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

package com.hedera.node.app.spi.key;

import com.hedera.hapi.node.base.Key;
import com.hedera.node.app.spi.signatures.SignatureVerification;
import com.hedera.node.app.spi.signatures.VerificationAssistant;
import edu.umd.cs.findbugs.annotations.NonNull;

/**
 * Helper class that contains all functionality for verifying signatures during handle.
 */
public interface KeyVerifier {

    /**
     * Gets the {@link SignatureVerification} for the given key. If this key was not provided during pre-handle, then
     * there will be no corresponding {@link SignatureVerification}. If the key was provided during pre-handle, then the
     * corresponding {@link SignatureVerification} will be returned with the result of that verification operation.
     *
     * <p>The signatures of required keys are guaranteed to be verified. Optional signatures may still be in the
     * process of being verified (and therefore may time out). The timeout can be configured via the configuration
     * {@code hedera.workflow.verificationTimeoutMS}
     *
     * @param key the key to get the verification for
     * @return the verification for the given key
     * @throws NullPointerException if {@code key} is {@code null}
     */
    @NonNull
    SignatureVerification verificationFor(@NonNull Key key);

    /**
     * Gets the {@link SignatureVerification} for the given key. If this key was not provided during pre-handle, then
     * there will be no corresponding {@link SignatureVerification}. If the key was provided during pre-handle, then the
     * corresponding {@link SignatureVerification} will be returned with the result of that verification operation.
     * Additionally, the VerificationAssistant provided may modify the result for "primitive", "Contract ID", or
     * "Delegatable Contract ID" keys, and will be called to observe and reply for each such key as it is processed.
     *
     * <p>The signatures of required keys are guaranteed to be verified. Optional signatures may still be in the
     * process of being verified (and therefore may time out). The timeout can be configured via the configuration
     * {@code hedera.workflow.verificationTimeoutMS}
     *
     * @param key the key to get the verification for
     * @param callback a VerificationAssistant callback function that will observe each "primitive", "Contract ID", or
     * "Delegatable Contract ID" key and return a boolean indicating if the given key should be considered valid.
     * @return the verification for the given key
     */
    @NonNull
    SignatureVerification verificationFor(@NonNull Key key, @NonNull VerificationAssistant callback);
}
