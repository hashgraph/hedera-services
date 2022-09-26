/*
 * Copyright (C) 2021-2022 Hedera Hashgraph, LLC
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

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.google.protobuf.ByteString;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.KeyList;
import com.hederahashgraph.api.proto.java.ThresholdKey;
import com.swirlds.common.utility.CommonUtils;
import java.security.PrivateKey;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import net.i2p.crypto.eddsa.EdDSAPublicKey;
import net.i2p.crypto.eddsa.KeyPairGenerator;
import org.apache.commons.lang3.NotImplementedException;

/** Utility class for different key types generation used for tests */
public class JKeyUtils {
    public static JKey getSpecificJKeysMade(
            final String action, final int numKeys, final int depth) {
        final List<JKey> keyList = new ArrayList<>();
        final List<PrivateKey> privKeyList = new ArrayList<>();
        final var totalKeys = (int) Math.pow(numKeys, depth - 1);
        for (int i = 0; i < totalKeys; i++) {
            final var pair = new KeyPairGenerator().generateKeyPair();
            final var pubKey = ((EdDSAPublicKey) pair.getPublic()).getAbyte();
            final var akey = new JEd25519Key(pubKey);
            final var priv = pair.getPrivate();
            privKeyList.add(priv);
            keyList.add(akey);
        }

        JKey jkey = null;
        if ("JThresholdKey".equalsIgnoreCase(action)) {
            final int threshold = 3;
            jkey = genThresholdKeyRecursive(numKeys, threshold, keyList);
        }
        if ("JKeyList".equalsIgnoreCase(action)) {
            jkey = genKeyListRecursive(numKeys, keyList);
        }

        return jkey;
    }

    /**
     * @param numKeys
     * @param threshold
     * @param keyList
     * @return
     */
    private static JKey genThresholdKeyRecursive(
            final int numKeys, final int threshold, final List<JKey> keyList) {
        if (keyList.size() == 1) {
            return keyList.get(0);
        }

        final List<JKey> combinedList = new ArrayList<>();

        final int len = keyList.size();
        int pos = 0;
        while (pos < len) {
            final List<JKey> keys = new ArrayList<>();
            for (int j = 0; j < numKeys; j++) {
                keys.add(keyList.get(pos++));
            }
            final var keysList = new JKeyList(keys);
            final var thresholdKey = new JThresholdKey(keysList, threshold);
            combinedList.add(thresholdKey);
        }

        return genThresholdKeyRecursive(numKeys, threshold, combinedList);
    }

    /**
     * @param numKeys
     * @param keyList
     * @return
     */
    private static JKey genKeyListRecursive(final int numKeys, final List<JKey> keyList) {
        if (keyList.size() == 1) {
            return keyList.get(0);
        }

        final List<JKey> combinedList = new ArrayList<>();

        final int len = keyList.size();
        int pos = 0;
        while (pos < len) {
            final List<JKey> myKeys = new ArrayList<>();
            for (int j = 0; j < numKeys; j++) {
                myKeys.add(keyList.get(pos++));
            }
            final var jKeyList = new JKeyList(myKeys);
            combinedList.add(jKeyList);
        }

        return genKeyListRecursive(numKeys, combinedList);
    }

    private static Key genSingleEd25519Key(final Map<String, PrivateKey> pubKey2privKeyMap) {
        final var pair = new KeyPairGenerator().generateKeyPair();
        final var pubKey = ((EdDSAPublicKey) pair.getPublic()).getAbyte();
        final var key = Key.newBuilder().setEd25519(ByteString.copyFrom(pubKey)).build();
        final var pubKeyHex = CommonUtils.hex(pubKey);
        pubKey2privKeyMap.put(pubKeyHex, pair.getPrivate());
        return key;
    }

    public static Key genSingleECDSASecp256k1Key(final Map<String, PrivateKey> pubKey2privKeyMap) {
        final var pair = new KeyPairGenerator().generateKeyPair();
        final var pubKey = ((EdDSAPublicKey) pair.getPublic()).getAbyte();
        final var key = Key.newBuilder().setECDSASecp256K1(ByteString.copyFrom(pubKey)).build();
        final var pubKeyHex = CommonUtils.hex(pubKey);
        pubKey2privKeyMap.put(pubKeyHex, pair.getPrivate());
        return key;
    }

    /**
     * Generates a complex key of given depth with a mix of basic key, threshold key and key list.
     *
     * @param depth of the generated key
     * @return generated key
     */
    public static Key genSampleComplexKey(
            final int depth, final Map<String, PrivateKey> pubKey2privKeyMap) {
        Key rv;
        final int numKeys = 3;
        final int threshold = 2;

        if (depth == 1) {
            rv = genSingleEd25519Key(pubKey2privKeyMap);

            // verify the size
            final int size = computeNumOfExpandedKeys(rv, 1, new AtomicInteger());
            assertEquals(1, size);
        } else if (depth == 2) {
            final List<Key> keys = new ArrayList<>();
            keys.add(genSingleEd25519Key(pubKey2privKeyMap));
            keys.add(genSingleECDSASecp256k1Key(pubKey2privKeyMap));
            keys.add(genThresholdKeyInstance(numKeys, threshold, pubKey2privKeyMap));
            keys.add(genKeyListInstance(numKeys, pubKey2privKeyMap));
            rv = genKeyList(keys);

            // verify the size
            final int size = computeNumOfExpandedKeys(rv, 1, new AtomicInteger());
            assertEquals(2 + numKeys * 2, size);
        } else {
            throw new NotImplementedException("Not implemented yet.");
        }

        return rv;
    }

    /**
     * Computes number of expanded keys by traversing the key recursively.
     *
     * @param key the complex key to be computed
     * @param depth current level that is to be traversed. The first level has a value of 1.
     * @param counter keeps track the number of keys
     * @return number of expanded keys
     */
    private static int computeNumOfExpandedKeys(
            final Key key, int depth, final AtomicInteger counter) {
        if (!(key.hasThresholdKey() || key.hasKeyList())) {
            counter.incrementAndGet();
            return counter.get();
        }

        final var tKeys =
                key.hasThresholdKey()
                        ? key.getThresholdKey().getKeys().getKeysList()
                        : key.getKeyList().getKeysList();

        if (depth <= 100) {
            depth++;
            for (var aKey : tKeys) {
                computeNumOfExpandedKeys(aKey, depth, counter);
            }
        }

        return counter.get();
    }

    /**
     * Generates a key list instance.
     *
     * @param numKeys number of keys in the generated key
     * @param pubKey2privKeyMap map of public key hex string as key and the private key as value
     * @return generated key list
     */
    private static Key genKeyListInstance(
            final int numKeys, final Map<String, PrivateKey> pubKey2privKeyMap) {
        final var keys = genEd25519Keys(numKeys, pubKey2privKeyMap);
        return genKeyList(keys);
    }

    /**
     * Generates a threshold key instance.
     *
     * @param numKeys number of keys in the generated key
     * @param threshold the threshold for the generated key
     * @param pubKey2privKeyMap map of public key hex string as key and the private key as value
     * @return generated threshold key
     */
    private static Key genThresholdKeyInstance(
            final int numKeys,
            final int threshold,
            final Map<String, PrivateKey> pubKey2privKeyMap) {
        final var keys = genEd25519Keys(numKeys, pubKey2privKeyMap);
        return genThresholdKey(keys, threshold);
    }

    /**
     * Generates a list of Ed25519 keys.
     *
     * @param pubKey2privKeyMap map of public key hex string as key and the private key as value
     * @return a list of generated Ed25519 keys
     */
    private static List<Key> genEd25519Keys(
            final int numKeys, final Map<String, PrivateKey> pubKey2privKeyMap) {
        final List<Key> rv = new ArrayList<>();
        for (int i = 0; i < numKeys; i++) {
            final var pair = new KeyPairGenerator().generateKeyPair();
            final var pubKey = ((EdDSAPublicKey) pair.getPublic()).getAbyte();
            final var key = Key.newBuilder().setEd25519(ByteString.copyFrom(pubKey)).build();
            final var pubKeyHex = CommonUtils.hex(pubKey);
            pubKey2privKeyMap.put(pubKeyHex, pair.getPrivate());
            rv.add(key);
        }

        return rv;
    }

    /**
     * Generates a threshold key from a list of keys.
     *
     * @return generated threshold key
     */
    private static Key genThresholdKey(final List<Key> keys, final int threshold) {
        final var thresholdKey =
                ThresholdKey.newBuilder()
                        .setKeys(KeyList.newBuilder().addAllKeys(keys))
                        .setThreshold(threshold);
        return Key.newBuilder().setThresholdKey(thresholdKey).build();
    }

    /**
     * Generates a KeyList key from a list of keys.
     *
     * @return generated KeyList key
     */
    private static Key genKeyList(final List<Key> keys) {
        final var keyList = KeyList.newBuilder().addAllKeys(keys);
        return Key.newBuilder().setKeyList(keyList).build();
    }
}
