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

package com.hedera.cryptography.pairings.signatures.api;

/**
 * An enum to clarify which group public keys and signatures are in, for a given
 * {@link SignatureSchema SignatureSchema}
 */
public enum GroupAssignment {
    /**
     * The group for signatures is the first group in the pairing, and the group for public keys is the second group.
     */
    GROUP1_FOR_SIGNING,
    /**
     * The group for signatures is the second group in the pairing, and the group for public keys is the first group.
     */
    GROUP1_FOR_PUBLIC_KEY;
}
