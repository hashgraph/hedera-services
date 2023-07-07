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

package com.swirlds.platform.stateproof;

/**
 * Signature thresholds for state proofs. If validating using the address book used to generate this state proof,
 * {@link #SUPER_MAJORITY} is recommended. If validating using a different address book, weaker thresholds may
 * optionally be selected (as long as the security guarantees are acceptable for the use case). A different address book
 * may be capable of validating a state proof as long as the difference in the address book is sufficiently minor.
 */
public enum StateProofThreshold {
    /**
     * Consider the state proof valid iff valid signatures collectively have &ge;1/3 consensus weight. Safe in the
     * absence of ISSes.
     */
    STRONG_MINORITY,
    /**
     * Consider the state proof valid iff valid signatures collectively have &gt;1/2 consensus weight. Safe in the
     * presence of ISSes as long as malicious nodes are not double signing different ISS partitions.
     */
    MAJORITY,
    /**
     * Consider the state proof valid iff valid signatures collectively have &gt;2/3 consensus weight. Safe in the
     * presence of ISSes regardless of activity by dirty rotten double signers.
     */
    SUPER_MAJORITY,
}
