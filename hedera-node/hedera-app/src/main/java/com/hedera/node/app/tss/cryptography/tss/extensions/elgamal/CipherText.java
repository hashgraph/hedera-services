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

package com.hedera.node.app.tss.cryptography.tss.extensions.elgamal;

import com.hedera.node.app.tss.cryptography.pairings.api.GroupElement;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.List;
import java.util.Objects;

/**
 * An ElGamal cipherText
 * @param cipherText the cipherText value.
 */
public record CipherText(@NonNull List<GroupElement> cipherText) {
    /**
     * Constructor
     * @param cipherText the cipherText value
     */
    public CipherText {
        if (Objects.requireNonNull(cipherText).isEmpty()) {
            throw new IllegalArgumentException("cipherText cannot be empty");
        }
    }

    /**
     * Returns the size of the cipherText.
     * @return the size of the cipherText
     */
    public int size() {
        return cipherText.size();
    }
}
