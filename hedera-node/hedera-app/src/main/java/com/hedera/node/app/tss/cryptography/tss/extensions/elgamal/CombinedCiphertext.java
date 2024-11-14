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
import com.hedera.node.app.tss.cryptography.tss.api.TssShareTable;
import edu.umd.cs.findbugs.annotations.NonNull;

import java.util.List;

/**
 * Represents a comprised element obtained from a {@link CiphertextTable}
 * @param randomness a compressed shared randomness value
 * @param values a combined representation of all the elements in a {@link CiphertextTable}
 */
public record CombinedCiphertext(@NonNull GroupElement randomness, @NonNull List<GroupElement> values)
        implements TssShareTable<GroupElement> {
    @NonNull
    @Override
    public GroupElement getForShareId(final int shareId) {
        return values().get(shareId - 1);
    }
}
