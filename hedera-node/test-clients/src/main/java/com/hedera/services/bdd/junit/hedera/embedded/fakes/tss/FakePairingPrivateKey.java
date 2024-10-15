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

import com.hedera.cryptography.pairings.api.GroupElement;
import com.hedera.cryptography.pairings.signatures.api.PairingPrivateKey;
import com.hedera.cryptography.pairings.signatures.api.PairingPublicKey;

public class FakePairingPrivateKey {
    private final PairingPrivateKey privateKey;

    public FakePairingPrivateKey(PairingPrivateKey privateKey) {
        this.privateKey = privateKey;
    }

    public PairingPublicKey createPublicKey() {
        final var privateKeyElement =
                new FakeGroupElement(privateKey.privateKey().toBigInteger());
        GroupElement publicKey = FakeGroupElement.GENERATOR.add(privateKeyElement);
        return new PairingPublicKey(publicKey, privateKey.signatureSchema());
    }

    public PairingPrivateKey getPairingPrivateKey() {
        return privateKey;
    }
}
