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

package com.hedera.node.app.tss.cryptography.altbn128;

import com.hedera.node.app.tss.cryptography.pairings.api.PairingsException;
import edu.umd.cs.findbugs.annotations.NonNull;

/**
 * A {@link PairingsException} thrown by the com.hedera.node.app.tss.cryptography.altbn128 implementation
 */
public class AltBn128Exception extends PairingsException {
    /**
     * Creates as a consequence of a failed rust JNI call
     *
     * @param result        The result code from the rust operation
     * @param operationName The underlying arkworks operation
     */
    public AltBn128Exception(final int result, final @NonNull String operationName) {
        super("result=" + result + ", operationName=" + operationName);
    }

    /**
     * Creates a general ALT-BN-128 exception
     *
     * @param message The message
     */
    public AltBn128Exception(@NonNull final String message) {
        super(message);
    }
}
