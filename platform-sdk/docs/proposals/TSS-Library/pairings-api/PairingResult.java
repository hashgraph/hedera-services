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


package com.hedera.cryptography.pairings.api;

import edu.umd.cs.findbugs.annotations.NonNull;

/**
 * This class represents the result of the pairing operation between two elements
 */
public interface PairingResult {
    /**
     * Get the first input element. This element is in the opposite group of the second input element
     *
     * @return the first input element
     */
    @NonNull
    GroupElement getInputElement1();

    /**
     * Get the second input element. This element is in the opposite group of the first input element
     *
     * @return the second input element
     */
    @NonNull
    GroupElement getInputElement2();

    /**
     * Get the bytes of the pairing result
     * <p>
     * If the implementation so wishes, it might throw an {@link UnsupportedOperationException}. Serializing the
     * pairing result is an expensive operation, and may not be supported by all implementations.
     *
     * @return the bytes of the pairing result
     */
    @NonNull
    byte[] getPairingBytes();
}
