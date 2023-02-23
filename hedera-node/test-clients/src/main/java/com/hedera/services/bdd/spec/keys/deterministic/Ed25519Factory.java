/*
 * Copyright (C) 2021-2023 Hedera Hashgraph, LLC
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

package com.hedera.services.bdd.spec.keys.deterministic;

import com.google.protobuf.ByteString;
import com.hedera.services.bdd.spec.persistence.SpecKey;
import com.hederahashgraph.api.proto.java.Key;
import net.i2p.crypto.eddsa.EdDSAPrivateKey;
import net.i2p.crypto.eddsa.spec.EdDSANamedCurveTable;
import net.i2p.crypto.eddsa.spec.EdDSAPrivateKeySpec;

public class Ed25519Factory {
    public static EdDSAPrivateKey ed25519From(byte[] privateKey) {
        var params = EdDSANamedCurveTable.getByName(EdDSANamedCurveTable.ED_25519);
        var privateKeySpec = new EdDSAPrivateKeySpec(privateKey, params);
        return new EdDSAPrivateKey(privateKeySpec);
    }

    public static Key populatedFrom(byte[] pubKey) {
        return Key.newBuilder().setEd25519(ByteString.copyFrom(pubKey)).build();
    }

    public static void main(String... args) {
        System.out.println(SpecKey.randomMnemonic());
    }
}
