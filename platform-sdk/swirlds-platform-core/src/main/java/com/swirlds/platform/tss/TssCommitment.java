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

package com.swirlds.platform.tss;

import com.swirlds.platform.tss.pairings.GroupElement;
import edu.umd.cs.findbugs.annotations.NonNull;

/**
 * A commitment to a polynomial.
 * <p>
 * The commitments produced during a keying contain the data to reconstruct the public key for each share.
 */
public interface TssCommitment {
    /**
     * Extract the public key from this commitment for a given share.
     * <p>
     * The public key returned by this method will be aggregated with the public keys from all other commitments for
     * the same share, and the result will be the final public key for that share.
     *
     * @param shareId the share ID of the public key to extract
     * @return the public key extracted from this commitment
     */
    @NonNull
    GroupElement extractPublicKey(@NonNull final TssShareId shareId);

    /**
     * Get the term at the given index. // TODO: does this method make sense, naming and content wise?
     *
     * @param index the index of the term to get
     * @return the term at the given index
     */
    @NonNull
    GroupElement getTerm(final int index);

    /**
     * Get the byte array representation of this commitment.
     *
     * @return the byte array representation of this commitment
     */
    byte[] toBytes();
}
