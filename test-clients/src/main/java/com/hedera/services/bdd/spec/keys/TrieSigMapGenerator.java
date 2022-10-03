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
package com.hedera.services.bdd.spec.keys;

import static java.util.stream.Collectors.collectingAndThen;
import static java.util.stream.Collectors.toList;

import com.google.protobuf.ByteString;
import com.hedera.services.bdd.spec.HapiApiSpec;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.SignatureMap;
import com.hederahashgraph.api.proto.java.SignaturePair;
import com.swirlds.common.utility.CommonUtils;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.function.Function;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.Assertions;

public class TrieSigMapGenerator implements SigMapGenerator {
    private static final Logger log = LogManager.getLogger(TrieSigMapGenerator.class);

    private String[] fullPrefixKeys;
    private final Nature nature;

    private TrieSigMapGenerator(Nature nature) {
        this.nature = nature;
    }

    private TrieSigMapGenerator(final Nature nature, final String[] fullPrefixKeys) {
        this.nature = nature;
        this.fullPrefixKeys = fullPrefixKeys;
    }

    private static final TrieSigMapGenerator uniqueInstance;
    private static final TrieSigMapGenerator ambiguousInstance;
    private static final TrieSigMapGenerator confusedInstance;

    static {
        uniqueInstance = new TrieSigMapGenerator(Nature.UNIQUE_PREFIXES);
        ambiguousInstance = new TrieSigMapGenerator(Nature.AMBIGUOUS_PREFIXES);
        confusedInstance = new TrieSigMapGenerator(Nature.CONFUSED_PREFIXES);
    }

    public static SigMapGenerator withNature(Nature nature) {
        switch (nature) {
            default:
                return uniqueInstance;
            case AMBIGUOUS_PREFIXES:
                return ambiguousInstance;
            case CONFUSED_PREFIXES:
                return confusedInstance;
        }
    }

    public static SigMapGenerator uniqueWithFullPrefixesFor(String... fullPrefixKeys) {
        return new TrieSigMapGenerator(Nature.UNIQUE_PREFIXES, fullPrefixKeys);
    }

    @Override
    public SignatureMap forPrimitiveSigs(
            final HapiApiSpec spec, final List<Map.Entry<byte[], byte[]>> keySigs) {
        List<byte[]> keys = keySigs.stream().map(Map.Entry::getKey).collect(toList());
        ByteTrie trie = new ByteTrie(keys);

        final Set<ByteString> alwaysFullPrefixes =
                (fullPrefixKeys == null) ? Collections.emptySet() : fullPrefixSetFor(spec);

        final Function<byte[], byte[]> prefixCalc = getPrefixCalcFor(trie);
        return keySigs.stream()
                .map(
                        keySig -> {
                            final var key = keySig.getKey();
                            final var wrappedKey = ByteString.copyFrom(key);
                            final var effPrefix =
                                    alwaysFullPrefixes.contains(wrappedKey)
                                            ? wrappedKey
                                            : ByteString.copyFrom(prefixCalc.apply(key));
                            if (key.length == 32) {
                                return SignaturePair.newBuilder()
                                        .setPubKeyPrefix(effPrefix)
                                        .setEd25519(ByteString.copyFrom(keySig.getValue()))
                                        .build();
                            } else {
                                return SignaturePair.newBuilder()
                                        .setPubKeyPrefix(effPrefix)
                                        .setECDSASecp256K1(ByteString.copyFrom(keySig.getValue()))
                                        .build();
                            }
                        })
                .collect(
                        collectingAndThen(
                                toList(), l -> SignatureMap.newBuilder().addAllSigPair(l).build()));
    }

    private Set<ByteString> fullPrefixSetFor(final HapiApiSpec spec) {
        final var registry = spec.registry();
        final var fullPrefixSet = new HashSet<ByteString>();
        for (final var key : fullPrefixKeys) {
            final var explicitKey = registry.getKey(key);
            accumulateFullPrefixes(explicitKey, fullPrefixSet);
        }
        return fullPrefixSet;
    }

    private void accumulateFullPrefixes(final Key explicit, final Set<ByteString> fullPrefixSet) {
        if (!explicit.getEd25519().isEmpty()) {
            fullPrefixSet.add(explicit.getEd25519());
        } else if (explicit.hasKeyList()) {
            for (final var innerKey : explicit.getKeyList().getKeysList()) {
                accumulateFullPrefixes(innerKey, fullPrefixSet);
            }
        } else if (explicit.hasThresholdKey()) {
            for (final var innerKey : explicit.getThresholdKey().getKeys().getKeysList()) {
                accumulateFullPrefixes(innerKey, fullPrefixSet);
            }
        } else if (!explicit.getECDSASecp256K1().isEmpty()) {
            fullPrefixSet.add(explicit.getECDSASecp256K1());
        }
    }

    private Function<byte[], byte[]> getPrefixCalcFor(ByteTrie trie) {
        return key -> {
            byte[] prefix = {};
            switch (nature) {
                case UNIQUE_PREFIXES:
                    prefix = trie.shortestPrefix(key, 1);
                    break;
                case AMBIGUOUS_PREFIXES:
                    prefix = trie.shortestPrefix(key, Integer.MAX_VALUE);
                    break;
                case CONFUSED_PREFIXES:
                    prefix = trie.randomPrefix(key.length);
                    break;
            }
            log.debug(CommonUtils.hex(key) + " gets prefix " + CommonUtils.hex(prefix));
            return prefix;
        };
    }

    class ByteTrie {
        class Node {
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
            Assertions.assertTrue(lenUsed <= a.length, "No unique prefix exists!");
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
