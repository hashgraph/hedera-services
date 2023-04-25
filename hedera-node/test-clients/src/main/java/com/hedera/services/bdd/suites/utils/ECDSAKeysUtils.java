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

package com.hedera.services.bdd.suites.utils;

import static com.hedera.services.bdd.spec.utilops.UtilVerbs.newKeyNamed;

import com.hedera.services.bdd.spec.HapiSpecOperation;
import com.hedera.services.bdd.spec.infrastructure.HapiSpecRegistry;
import com.hedera.services.bdd.spec.keys.SigControl;
import java.util.stream.IntStream;

public class ECDSAKeysUtils {
    public static HapiSpecOperation[] onlyEcdsaKeys(int numDistinctEcdsaKeys) {
        return IntStream.range(0, numDistinctEcdsaKeys)
                .mapToObj(i -> newKeyNamed("Fuzz#" + i).shape(SigControl.SECP256K1_ON))
                .toArray(HapiSpecOperation[]::new);
    }

    public static byte[] getEvmAddressFromString(HapiSpecRegistry registry, String keyName) {
        return registry.getKey(keyName).getECDSASecp256K1().toByteArray();
    }
}
