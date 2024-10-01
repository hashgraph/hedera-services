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

package com.hedera.services.bdd.suites.hip991;

import static com.hedera.services.bdd.spec.utilops.UtilVerbs.newKeyNamed;

import com.google.protobuf.ByteString;
import com.hedera.services.bdd.spec.SpecOperation;
import com.hedera.services.bdd.spec.keys.KeyShape;
import com.hederahashgraph.api.proto.java.Key;
import java.util.ArrayList;

public class TopicCustomFeeBase {
    protected static final String TOPIC = "topic";
    protected static final String ADMIN_KEY = "adminKey";
    protected static final String SUBMIT_KEY = "submitKey";
    protected static final String FEE_SCHEDULE_KEY = "feeScheduleKey";
    protected static final String FEE_SCHEDULE_KEY_ECDSA = "feeScheduleKeyECDSA";
    protected static final String FEE_EXEMPT_KEY_PREFIX = "feeExemptKey_";

    // This key is truly invalid, as all Ed25519 public keys must be 32 bytes long
    protected static final Key STRUCTURALLY_INVALID_KEY =
            Key.newBuilder().setEd25519(ByteString.fromHex("ff")).build();

    protected static SpecOperation[] setupBaseKeys() {
        return new SpecOperation[] {
            newKeyNamed(ADMIN_KEY),
            newKeyNamed(SUBMIT_KEY),
            newKeyNamed(FEE_SCHEDULE_KEY),
            newKeyNamed(FEE_SCHEDULE_KEY_ECDSA).shape(KeyShape.SECP256K1)
        };
    }

    protected static SpecOperation[] newNamedKeysForFEKL(int count) {
        final var list = new ArrayList<SpecOperation>();
        for (int i = 0; i < count; i++) {
            list.add(newKeyNamed(FEE_EXEMPT_KEY_PREFIX + i));
        }
        return list.toArray(new SpecOperation[0]);
    }

    protected static String[] feeExemptKeyNames(int count) {
        final var list = new ArrayList<String>();
        for (int i = 0; i < count; i++) {
            list.add(FEE_EXEMPT_KEY_PREFIX + i);
        }
        return list.toArray(new String[0]);
    }
}
