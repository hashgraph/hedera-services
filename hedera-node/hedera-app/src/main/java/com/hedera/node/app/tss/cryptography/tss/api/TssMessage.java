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

package com.hedera.node.app.tss.cryptography.tss.api;

import com.hedera.node.app.tss.cryptography.bls.SignatureSchema;
import com.hedera.node.app.tss.cryptography.pairings.api.FieldElement;
import com.hedera.node.app.tss.cryptography.pairings.api.GroupElement;

/**
 * A message sent as part of either genesis keying, or rekeying.
 */
public interface TssMessage {

    /**
     * Current supported version.
     * All messages using a different version will throw an error when parsed.
     */
    int MESSAGE_CURRENT_VERSION = 0;

    /**
     * Specification of the format:<p>
     *  Given
     *  <ul>
     *      <li>{@code e}: {@link FieldElement#size()}</li>
     *      <li>{@code g}: {@link GroupElement#size()} of {@link SignatureSchema#getPublicKeyGroup()}</li>
     *      <li>{@code t}: threshold value</li>
     *      <li>{@code s}: total-shares (Participants*Shares)</li>
     *  </ul>
     * A {@link TssMessage} byte representation will consist of:<br>
     * <ul>
     *     <li>4 bytes (big-endian) representing the version of the message. Must be {@code MESSAGE_CURRENT_VERSION} constant value</li>
     *     <li>1 byte for {@link SignatureSchema} that originated the message.</li>
     *     <li>4 bytes (big-endian) representing the shareId that originated the message.</li>
     *     <li>A list of {@code e} elements, each of size {@code g} bytes, representing the shared randomness (total of {@code e * g} bytes).</li>
     *     <li>{@code s} lists of {@code e} elements, each of size {@code g} bytes, representing the encrypted shares (total of {@code s * e * g} bytes).</li>
     *     <li>A list of {@code t} elements, each of size {@code g} bytes, representing the polynomial commitment (total of {@code t * g} bytes).</li>
     *     <li>{@code g} bytes representing the proof element {@code f}.</li>
     *     <li>{@code g} bytes representing the proof element {@code a}.</li>
     *     <li>{@code e} bytes representing the proof scalar {@code zr}.</li>
     *     <li>{@code e} bytes representing the proof scalar {@code za}.</li>
     * </ul>
     * @return the byte representation of a TssMessage
     * @see SignatureSchema#toByte()
     * @see GroupElement#toBytes()
     * @see FieldElement#toBytes()
     */
    byte[] toBytes();
}
