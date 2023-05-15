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

package com.hedera.node.app.signature;

import com.hedera.hapi.node.base.Key;
import com.hedera.hapi.node.base.SignaturePair;
import com.hedera.hapi.node.state.token.Account;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.List;
import java.util.Set;

/**
 * Expands {@link SignaturePair}s into {@link ExpandedSignaturePair}s.
 *
 * <p>A {@link SignaturePair} is a cryptographic signature and an optional public key prefix. The prefix can be anything
 * from 0 bytes all the way up to the bytes of a full cryptographic public key. Only ED25519 (32 bytes long) and
 * ECDSA_SECP256K1 <strong>compressed</strong> (33 bytes long) public keys are supported.
 *
 * <p>The job of the {@link SignatureExpander} is to "expand" the prefix of a {@link SignaturePair} to be a full,
 * uncompressed (in the case of ECDSA_SECP256K1), public key. There are three different scenarios it must deal with:
 * <ol>
 *     <li>Given a {@link Key}, find a {@link SignaturePair} with a signature type and prefix that matches the
 *     cryptographic keys that make up the {@link Key} (remember a {@link Key} could be a compound key such as a
 *     {@link com.hedera.hapi.node.base.KeyList} or a {@link com.hedera.hapi.node.base.ThresholdKey}).</li>
 *
 *     <li>Given a hollow {@link Account}, find a {@link SignaturePair} of the ECDSA_SECP256K1 type such that the
 *     prefix on that pair is a full public key (33 bytes) and the keccak hash of that public key, trimmed to the last
 *     20 bytes, exactly matches the EVM alias on the hollow account.</li>
 *
 *     <li>Irrespective of either of the above, any full key prefix (with compressed keys being expanded).</li>
 * </ol>
 */
public interface SignatureExpander {
    /**
     * Given a list of {@link SignaturePair}s, find all that have a full key prefix, uncompressing any ECDSA_SECP256K1
     * keys, and populate the given set of {@code expanded} pairs.
     *
     * @param sigPairs The {@link SignaturePair}s to search for full key prefixes. This list must be pre-filtered such
     *                 that there are no duplicate entries and one prefix is not the prefix of another.
     * @param expanded Will be populated with all created {@link ExpandedSignaturePair}s
     */
    void expand(@NonNull List<SignaturePair> sigPairs, @NonNull Set<ExpandedSignaturePair> expanded);

    /**
     * Given a {@link Key}, traverses it looking for each cryptographic key (ED25519, ECDSA_SECP256K1). For each such
     * cryptographic key, look for a corresponding matching prefix in the {@link SignaturePair}s. If one is found, then
     * we are guaranteed that it is the only possible match, and a corresponding {@link ExpandedSignaturePair} will be
     * created.
     *
     * @param key The {@link Key} to traverse looking for cryptographic keys, used to find matching
     *            {@link SignaturePair}s
     * @param sigPairs The {@link SignaturePair}s to search for full key prefixes. This list must be pre-filtered such
     *                 that there are no duplicate entries and one prefix is not the prefix of another.
     * @param expanded Will be populated with all created {@link ExpandedSignaturePair}s
     */
    void expand(@NonNull Key key, @NonNull List<SignaturePair> sigPairs, @NonNull Set<ExpandedSignaturePair> expanded);

    /**
     * Given a hollow {@link Account}, search for an ECDSA_SECP256K1 {@link SignaturePair} such that its prefix, when
     * keccak hashed and trimmed to the final 20 bytes, exactly match the EVM alias on the hollow account, and create
     * the corresponding {@link ExpandedSignaturePair}, adding it to the set.
     *
     * <p>This method does not validate that the account is actually hollow, it only requires the alias to be exactly
     * 20 bytes in length and match a computed alias from the prefix as described above.
     *
     * @param hollowAccount The hollow account
     * @param sigPairs The {@link SignaturePair}s to search for full key prefixes. This list must be pre-filtered such
     *                 that there are no duplicate entries and one prefix is not the prefix of another.
     * @param expanded Will be populated with all created {@link ExpandedSignaturePair}s
     */
    void expand(
            @NonNull Account hollowAccount,
            @NonNull List<SignaturePair> sigPairs,
            @NonNull Set<ExpandedSignaturePair> expanded);
}
