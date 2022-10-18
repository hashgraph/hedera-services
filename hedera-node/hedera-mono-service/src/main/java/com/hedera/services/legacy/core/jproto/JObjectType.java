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
package com.hedera.services.legacy.core.jproto;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;

/** Mapping of Class name and Object Id */
public enum JObjectType {
    FC_KEY,
    FC_KEY_LIST,
    FC_THRESHOLD_KEY,
    FC_ED25519_KEY,
    FC_ECDSA384_KEY,
    FC_RSA3072_KEY,
    FC_CONTRACT_ID_KEY,
    FC_DELEGATE_CONTRACT_ID_KEY,
    FC_FILE_INFO,
    FC_ECDSA_SECP256K1_KEY,
    FC_CONTRACT_ALIAS_KEY,
    FC_DELEGATE_CONTRACT_ALIAS_KEY;

    private static final Map<JObjectType, Long> LOOKUP_TABLE = new EnumMap<>(JObjectType.class);
    private static final HashMap<Long, JObjectType> REV_LOOKUP_TABLE = new HashMap<>();

    static {
        addLookup(FC_KEY, 15503731);
        addLookup(FC_KEY_LIST, 15512048);
        addLookup(FC_THRESHOLD_KEY, 15520365);
        addLookup(FC_ED25519_KEY, 15528682);
        addLookup(FC_ECDSA384_KEY, 15536999);
        addLookup(FC_RSA3072_KEY, 15620169);
        addLookup(FC_CONTRACT_ID_KEY, 15545316);
        addLookup(FC_FILE_INFO, 15636803);
        addLookup(FC_CONTRACT_ID_KEY, 15545316);
        addLookup(FC_DELEGATE_CONTRACT_ID_KEY, 15577777);
        addLookup(FC_ECDSA_SECP256K1_KEY, 15661654);
        addLookup(FC_CONTRACT_ALIAS_KEY, 15691654);
        addLookup(FC_DELEGATE_CONTRACT_ALIAS_KEY, 15696954);
    }

    private static void addLookup(final JObjectType type, final long value) {
        LOOKUP_TABLE.put(type, value);
        REV_LOOKUP_TABLE.put(value, type);
    }

    public static JObjectType valueOf(final long value) {
        return REV_LOOKUP_TABLE.get(value);
    }

    public long longValue() {
        return LOOKUP_TABLE.get(this);
    }
}
