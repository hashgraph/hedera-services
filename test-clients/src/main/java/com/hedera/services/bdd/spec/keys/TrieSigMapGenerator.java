package com.hedera.services.bdd.spec.keys;

/*-
 * ‌
 * Hedera Services Test Clients
 * ​
 * Copyright (C) 2018 - 2020 Hedera Hashgraph, LLC
 * ​
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
 * ‍
 */

import com.google.protobuf.ByteString;
import com.hederahashgraph.api.proto.java.SignatureMap;
import com.hederahashgraph.api.proto.java.SignaturePair;
import com.hedera.services.legacy.core.HexUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.Assert;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.function.Function;

import static java.util.stream.Collectors.*;

public class TrieSigMapGenerator implements SigMapGenerator {
	private static final Logger log = LogManager.getLogger(TrieSigMapGenerator.class);

	private final Nature nature;

	private TrieSigMapGenerator(Nature nature) {
		this.nature = nature;
	}

	private static final TrieSigMapGenerator uniqueInstance;
	private static final TrieSigMapGenerator ambiguousInstance;
	private static final TrieSigMapGenerator confusedInstance;

	static {
		uniqueInstance = new TrieSigMapGenerator(Nature.UNIQUE);
		ambiguousInstance = new TrieSigMapGenerator(Nature.AMBIGUOUS);
		confusedInstance = new TrieSigMapGenerator(Nature.CONFUSED);
	}

	public static SigMapGenerator withNature(Nature nature) {
		switch (nature) {
			default:
				return uniqueInstance;
			case AMBIGUOUS:
				return ambiguousInstance;
			case CONFUSED:
				return confusedInstance;
		}
	}

	@Override
	public SignatureMap forEd25519Sigs(List<Map.Entry<byte[], byte[]>> keySigs) {
		List<byte[]> keys = keySigs.stream().map(Map.Entry::getKey).collect(toList());
		ByteTrie trie = new ByteTrie(keys);
		Function<byte[], byte[]> prefixCalc = getPrefixCalcFor(trie);

		log.debug("---- Beginning SigMap Construction ----");
		return keySigs.stream()
				.map(keySig ->
						SignaturePair.newBuilder()
								.setPubKeyPrefix(ByteString.copyFrom(prefixCalc.apply(keySig.getKey())))
								.setEd25519(ByteString.copyFrom(keySig.getValue()))
								.build()
				).collect(collectingAndThen(toList(), l -> SignatureMap.newBuilder().addAllSigPair(l).build()));
	}

	private Function<byte[], byte[]> getPrefixCalcFor(ByteTrie trie) {
		return key -> {
			byte[] prefix = { };
			switch (nature) {
				case UNIQUE:
					prefix = trie.shortestPrefix(key, 1);
					break;
				case AMBIGUOUS:
					prefix = trie.shortestPrefix(key, Integer.MAX_VALUE);
					break;
				case CONFUSED:
					prefix = trie.randomPrefix(key.length);
					break;
			}
			log.debug(HexUtils.bytes2Hex(key) + " gets prefix " + HexUtils.bytes2Hex(prefix));
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
			Assert.assertTrue("No unique prefix exists!", lenUsed <= a.length);
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
