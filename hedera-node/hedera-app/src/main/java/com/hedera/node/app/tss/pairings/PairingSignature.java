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

package com.hedera.node.app.tss.pairings;

import static com.hedera.node.app.hapi.utils.CommonUtils.noThrowSha384HashOf;
import static com.hedera.node.app.tss.pairings.FakeGroupElement.GENERATOR;

import edu.umd.cs.findbugs.annotations.NonNull;
import java.math.BigInteger;

public record PairingSignature(@NonNull GroupElement signature, @NonNull SignatureSchema signatureSchema) {
    // sig = privateKey + messageHash
    // publicKey = generator + privateKey
    // messageHash = sig - (publicKey - generator) = sig - publicKey + generator
    public boolean verify(PairingPublicKey publicKey, byte[] message) {
        final var sig = new BigInteger(1, this.signature().toBytes());
        final var pk = new BigInteger(1, publicKey.publicKey().toBytes());
        final var gen = new BigInteger(1, GENERATOR.toBytes());
        final var msgHashFromSig = new BigInteger(1, sig.subtract(pk).add(gen).toByteArray());
        final var messageHash = new BigInteger(1, noThrowSha384HashOf(message));
        return messageHash.equals(msgHashFromSig);
    }
}
