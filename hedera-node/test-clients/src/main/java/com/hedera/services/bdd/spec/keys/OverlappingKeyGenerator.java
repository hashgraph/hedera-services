/*
 * Copyright (C) 2020-2023 Hedera Hashgraph, LLC
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

package com.hedera.services.bdd.spec.keys;

import com.google.protobuf.ByteString;
import com.hederahashgraph.api.proto.java.Key;
import com.swirlds.common.utility.CommonUtils;
import java.security.PrivateKey;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class OverlappingKeyGenerator implements KeyGenerator {
    private static final Logger log = LogManager.getLogger(OverlappingKeyGenerator.class);

    private int nextKey = 0;
    private List<Key> precomputed = new ArrayList<>();
    private Map<String, PrivateKey> pkMap = new HashMap<>();

    public static OverlappingKeyGenerator withDefaultOverlaps() {
        return new OverlappingKeyGenerator(5, 1);
    }

    public static OverlappingKeyGenerator withAtLeastOneOverlappingByte(int keys) {
        return new OverlappingKeyGenerator(keys, 1);
    }

    private OverlappingKeyGenerator(int n, int minOverlapLen) {
        Set<ByteString> usedPrefixes = new HashSet<>();
        Map<ByteString, Key> byPrefix = new HashMap<>();
        while (precomputed.size() < n) {
            Key candidate = DefaultKeyGen.genAndRememberEd25519Key(pkMap);
            ByteString prefix = pubKeyPrefixOf(candidate, minOverlapLen);
            if (byPrefix.containsKey(prefix)) {
                if (!usedPrefixes.contains(prefix)) {
                    precomputed.add(byPrefix.get(prefix));
                    usedPrefixes.add(prefix);
                }
                if (precomputed.size() < n) {
                    precomputed.add(candidate);
                }
            } else {
                byPrefix.put(prefix, candidate);
            }
        }
        log.debug("**** Hexed Public Keys ****");
        precomputed.stream()
                .forEach(k -> log.debug(CommonUtils.hex(k.getEd25519().toByteArray())));
    }

    private ByteString pubKeyPrefixOf(Key key, int prefixLen) {
        return key.getEd25519().substring(0, prefixLen);
    }

    @Override
    public Key genEd25519AndUpdateMap(Map<String, PrivateKey> mutablePkMap) {
        Key key = precomputed.get(nextKey);
        nextKey = (nextKey + 1) % precomputed.size();
        String hexPubKey = CommonUtils.hex(key.getEd25519().toByteArray());
        mutablePkMap.put(hexPubKey, pkMap.get(hexPubKey));
        return key;
    }
}
