/*
 * Copyright (C) 2020-2022 Hedera Hashgraph, LLC
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
package com.hedera.test.factories.keys;

import com.google.protobuf.ByteString;
import com.hederahashgraph.api.proto.java.Key;
import com.swirlds.common.crypto.SignatureType;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Predicate;

public class KeyTreeLeaf implements KeyTreeNode {
    private String label;
    private boolean usedToSign = true;
    private SignatureType sigType = SignatureType.ED25519;
    private final Key NONSENSE_RSA_KEY =
            Key.newBuilder().setRSA3072(ByteString.copyFrom("MOME".getBytes())).build();
    private final Map<KeyFactory, Key> keyCache = new HashMap<>();

    public KeyTreeLeaf(boolean usedToSign, String label, SignatureType sigType) {
        this.label = label;
        this.sigType = sigType;
        this.usedToSign = usedToSign;
    }

    public KeyTreeLeaf() {}

    public boolean isUsedToSign() {
        return usedToSign;
    }

    public SignatureType getSigType() {
        return sigType;
    }

    @Override
    public Key asKey(KeyFactory factory) {
        return keyCache.computeIfAbsent(factory, this::customKey);
    }

    private Key customKey(KeyFactory factory) {
        if (sigType == SignatureType.ED25519) {
            return Optional.ofNullable(label)
                    .map(factory::labeledEd25519)
                    .orElse(factory.newEd25519());
        } else if (sigType == SignatureType.ECDSA_SECP256K1) {
            return Optional.ofNullable(label)
                    .map(factory::labeledEcdsaSecp256k1)
                    .orElse(factory.newEcdsaSecp256k1());
        } else if (sigType == SignatureType.RSA) {
            return NONSENSE_RSA_KEY;
        }
        throw new AssertionError("Impossible signature type: " + sigType + "!");
    }

    @Override
    public int numLeaves() {
        return 1;
    }

    @Override
    public void traverse(Predicate<KeyTreeNode> shouldVisit, Consumer<KeyTreeNode> visitor) {
        if (shouldVisit.test(this)) {
            visitor.accept(this);
        }
    }
}
