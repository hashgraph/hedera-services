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

package com.hedera.services.bdd.junit.hedera.embedded.fakes.tss;

import static com.hedera.services.bdd.junit.hedera.embedded.fakes.tss.FakeGroupElement.GENERATOR;
import static com.hedera.services.bdd.junit.hedera.embedded.fakes.tss.FakeTssLibrary.computeHash;

import com.hedera.cryptography.pairings.signatures.api.PairingPublicKey;
import com.hedera.cryptography.pairings.signatures.api.PairingSignature;
import java.math.BigInteger;

public class FakePairingSignature {
    PairingSignature signature;

    public FakePairingSignature(PairingSignature signature) {
        this.signature = signature;
    }

    // sig = privateKey + messageHash
    // publicKey = generator + privateKey
    // messageHash = sig - (publicKey - generator) = sig - publicKey + generator
    public boolean verify(PairingPublicKey publicKey, byte[] message) {
        final var sig = new BigInteger(1, this.signature.signature().toBytes());
        final var pk = new BigInteger(1, publicKey.publicKey().toBytes());
        final var gen = new BigInteger(1, GENERATOR.toBytes());
        final var msgHashFromSig = new BigInteger(1, sig.subtract(pk).add(gen).toByteArray());
        final var messageHash = new BigInteger(1, computeHash(message));
        final var res = messageHash.equals(msgHashFromSig);
        return res;
    }
}
