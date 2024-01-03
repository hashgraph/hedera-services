/*
 * Copyright (C) 2023-2024 Hedera Hashgraph, LLC
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

import static com.hedera.services.bdd.suites.utils.MiscEETUtils.genRandomBytes;

import com.esaulpaugh.headlong.abi.Address;
import com.hedera.services.bdd.spec.infrastructure.HapiSpecRegistry;
import java.math.BigInteger;

public class ECDSAKeysUtils {
    private ECDSAKeysUtils() {}

    public static byte[] getEvmAddressFromString(HapiSpecRegistry registry, String keyName) {
        return registry.getKey(keyName).getECDSASecp256K1().toByteArray();
    }

    public static Address randomHeadlongAddress() {
        return Address.wrap(Address.toChecksumAddress(new BigInteger(1, genRandomBytes(20))));
    }
}
