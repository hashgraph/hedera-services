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
package com.hedera.test.factories.sigs;

import static java.util.Map.Entry;
import static java.util.stream.Collectors.collectingAndThen;
import static java.util.stream.Collectors.toList;

import com.google.protobuf.ByteString;
import com.hederahashgraph.api.proto.java.SignatureMap;
import com.hederahashgraph.api.proto.java.SignaturePair;
import com.swirlds.common.crypto.SignatureType;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import java.util.function.Supplier;

public class SigMapGenerator {
    private static final byte[] NONSENSE_SIG = "'Twas brillig, and the slithy toves...".getBytes();
    private final Function<ByteTrie, Function<byte[], byte[]>> prefixCalcFn;
    private int entryNo = 0;
    private Set<Integer> invalidEntries = Collections.EMPTY_SET;

    public static SigMapGenerator withUniquePrefixes() {
        return new SigMapGenerator(trie -> key -> trie.shortestPrefix(key, 1));
    }

    public static SigMapGenerator withAlternatingUniqueAndFullPrefixes() {
        final var isUnique = new AtomicBoolean(true);
        final Function<ByteTrie, Function<byte[], byte[]>> prefixCalcFn =
                trie ->
                        key -> {
                            final var goUnique = isUnique.get();
                            isUnique.set(!goUnique);
                            return goUnique ? trie.shortestPrefix(key, 1) : key;
                        };
        return new SigMapGenerator(prefixCalcFn);
    }

    public static SigMapGenerator withAmbiguousPrefixes() {
        return new SigMapGenerator(trie -> key -> trie.shortestPrefix(key, Integer.MAX_VALUE));
    }

    public static SigMapGenerator withRandomPrefixes() {
        return new SigMapGenerator(trie -> key -> trie.randomPrefix(key.length));
    }

    public SigMapGenerator(Function<ByteTrie, Function<byte[], byte[]>> prefixCalcFn) {
        this.prefixCalcFn = prefixCalcFn;
    }

    public void setInvalidEntries(Set<Integer> invalidEntries) {
        this.invalidEntries = invalidEntries;
    }

    SignatureMap generate(List<Entry<byte[], byte[]>> keySigs, Supplier<SignatureType> sigTypes) {
        List<byte[]> keys = keySigs.stream().map(Map.Entry::getKey).collect(toList());
        ByteTrie trie = new ByteTrie(keys);
        Function<byte[], byte[]> prefixCalc = prefixCalcFn.apply(trie);

        return keySigs.stream()
                .map(
                        keySig ->
                                from(
                                        prefixCalc.apply(keySig.getKey()),
                                        keySig.getValue(),
                                        sigTypes.get()))
                .collect(
                        collectingAndThen(
                                toList(), l -> SignatureMap.newBuilder().addAllSigPair(l).build()));
    }

    private SignaturePair from(byte[] pubKeyPrefix, byte[] sig, SignatureType sigType) {
        SignaturePair.Builder sp =
                SignaturePair.newBuilder().setPubKeyPrefix(ByteString.copyFrom(pubKeyPrefix));
        entryNo++;
        if (invalidEntries.contains(entryNo)) {
            sp.setEd25519(ByteString.copyFrom(NONSENSE_SIG));
        } else {
            if (sigType == SignatureType.RSA) {
                sp.setRSA3072(ByteString.copyFrom(sig));
            } else if (sigType == SignatureType.ECDSA_SECP256K1) {
                sp.setECDSASecp256K1(ByteString.copyFrom(sig));
            } else if (sigType == SignatureType.ED25519) {
                sp.setEd25519(ByteString.copyFrom(sig));
            }
        }
        return sp.build();
    }

    private static class ByteTrie {
        static class Node {
            int count = 1;
            Node[] children = new Node[256];
        }

        Node root = new Node();
        Random r = new Random();

        public ByteTrie(List<byte[]> allA) {
            allA.stream().forEach(a -> insert(a));
        }

        private void insert(byte[] a) {
            insert(a, root, 0);
        }

        private void insert(byte[] a, Node n, int i) {
            if (i == a.length) {
                return;
            }
            int v = vAt(a, i);
            if (n.children[v] == null) {
                n.children[v] = new Node();
            } else {
                n.children[v].count++;
            }
            insert(a, n.children[v], i + 1);
        }

        public byte[] randomPrefix(int maxLen) {
            int len = r.nextInt(maxLen) + 1;
            byte[] prefix = new byte[len];
            return randomPrefix(prefix, 0);
        }

        private byte[] randomPrefix(byte[] prefix, int i) {
            if (i == prefix.length) {
                return prefix;
            }
            int v = r.nextInt(256);
            byte next = (byte) ((v < 128) ? v : v - 256);
            prefix[i] = next;
            return randomPrefix(prefix, i + 1);
        }

        public byte[] shortestPrefix(byte[] a, int maxPrefixCard) {
            return shortestPrefix(a, root, maxPrefixCard, 1);
        }

        private byte[] shortestPrefix(byte[] a, Node n, int maxPrefixCard, int lenUsed) {
            if (lenUsed > a.length) {
                throw new IllegalStateException("No unique prefix exists");
            }
            int v = vAt(a, lenUsed - 1);
            if (n.children[v].count <= maxPrefixCard) {
                return Arrays.copyOfRange(a, 0, lenUsed);
            } else {
                return shortestPrefix(a, n.children[v], maxPrefixCard, lenUsed + 1);
            }
        }

        private int vAt(byte[] a, int i) {
            byte next = a[i];
            return (next < 0) ? (next + 256) : next;
        }
    }
}
