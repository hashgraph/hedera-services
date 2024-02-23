/*
 * Copyright (C) 2016-2023 Hedera Hashgraph, LLC
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

package com.swirlds.common.crypto;

import java.security.PublicKey;

public enum KeyType {
    EC(1, "SunEC"),
    RSA(2, "BC");

    private final int algorithmIdentifier;
    private final String provider;

    KeyType(int algorithmIdentifier, String provider) {
        this.algorithmIdentifier = algorithmIdentifier;
        this.provider = provider;
    }

    String getAlgorithmName() {
        return name();
    }

    public int getAlgorithmIdentifier() {
        return algorithmIdentifier;
    }

    public String getProvider() {
        return provider;
    }

    static KeyType getKeyType(int algorithmIdentifier) {
        switch (algorithmIdentifier) {
            case 1:
                return EC;
            case 2:
                return RSA;
        }
        return null;
    }

    static KeyType getKeyType(PublicKey key) {
        switch (key.getAlgorithm()) {
            case "EC":
                return KeyType.EC;
            case "RSA":
                return KeyType.RSA;
        }
        throw new IllegalArgumentException(key.getAlgorithm() + " is not a known key type!");
    }
}
