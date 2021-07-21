package com.hedera.services.legacy.client.util;

/*-
 * ‌
 * Hedera Services Test Clients
 * ​
 * Copyright (C) 2018 - 2021 Hedera Hashgraph, LLC
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

import com.hedera.services.legacy.proto.utils.CommonUtils;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.SignatureMap;
import com.hederahashgraph.api.proto.java.SignaturePair;
import com.hederahashgraph.api.proto.java.Transaction;
import com.hederahashgraph.api.proto.java.TransactionOrBuilder;
import net.i2p.crypto.eddsa.EdDSAPrivateKey;

import java.security.PrivateKey;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.hedera.services.legacy.client.util.KeyExpansion.keyFromBytes;

/**
 * Transaction Signing utility.
 */
public class TransactionSigner {
	/**
	 * Signs a transaction using SignatureMap format with provided private keys.
	 *
	 * @param transaction
	 * 		transaction
	 * @param privKeyList
	 * 		private key list
	 * @return signed transaction
	 */
	public static Transaction signTransaction(final Transaction transaction, final List<PrivateKey> privKeyList) {
		return signTransaction(transaction, privKeyList, false);
	}

	public static Transaction signTransaction(final Transaction transaction,
			final List<PrivateKey> privKeyList,
			final boolean appendSigMap
	) {
		final List<Key> keyList = new ArrayList<>();
		final HashMap<String, PrivateKey> pubKey2privKeyMap = new HashMap<>();
		for (PrivateKey pk : privKeyList) {
			final var pubKey = ((EdDSAPrivateKey) pk).getAbyte();
			keyList.add(keyFromBytes(pubKey));
			final var pubKeyHex = com.swirlds.common.CommonUtils.hex(pubKey);
			pubKey2privKeyMap.put(pubKeyHex, pk);
		}
		try {
			return signTransactionComplexWithSigMap(transaction, keyList, pubKey2privKeyMap, appendSigMap);
		} catch (Exception ignore) {
			ignore.printStackTrace();
		}
		return transaction;
	}

	/**
	 * Signs transaction with provided key and public to private key map. The generated signatures are
	 * contained in a SignatureMap object.
	 *
	 * @param transaction
	 * 		transaction to be singed
	 * @param keys
	 * 		complex keys for signing
	 * @param pubKey2privKeyMap
	 * 		public key to private key map
	 * @return transaction with signatures as a SignatureMap object
	 * @throws Exception
	 * 		when transaction sign fails
	 */
	public static Transaction signTransactionComplexWithSigMap(final TransactionOrBuilder transaction,
			final List<Key> keys,
			final Map<String, PrivateKey> pubKey2privKeyMap
	) throws Exception {
		return signTransactionComplexWithSigMap(transaction, keys, pubKey2privKeyMap, false);
	}

	public static Transaction signTransactionComplexWithSigMap(final TransactionOrBuilder transaction,
			final List<Key> keys,
			final Map<String, PrivateKey> pubKey2privKeyMap,
			final boolean appendSigMap
	) throws Exception {
		final var bodyBytes = CommonUtils.extractTransactionBodyBytes(transaction);
		final var sigsMap = signAsSignatureMap(bodyBytes, keys, pubKey2privKeyMap);

		final var builder = CommonUtils.toTransactionBuilder(transaction);

		if (appendSigMap) {
			final var currentSigMap = CommonUtils.extractSignatureMapOrUseDefault(transaction);
			final var sigMapToSet = currentSigMap.toBuilder().addAllSigPair(sigsMap.getSigPairList()).build();
			return builder.setSigMap(sigMapToSet).build();
		}

		return builder.setSigMap(sigsMap).build();
	}

	/**
	 * Signs a message with provided key and public to private key map. The generated signatures are
	 * contained in a SignatureMap object.
	 *
	 * @param messageBytes
	 * 		message bytes
	 * @param keys
	 * 		complex keys for signing
	 * @param pubKey2privKeyMap
	 * 		public key to private key map
	 * @return transaction with signatures as a SignatureMap object
	 * @throws Exception
	 * 		when sign fails
	 */
	public static SignatureMap signAsSignatureMap(final byte[] messageBytes,
			final List<Key> keys,
			final Map<String, PrivateKey> pubKey2privKeyMap
	) throws Exception {
		final List<Key> expandedKeys = new ArrayList<>();
		final var aKey = KeyExpansion.genKeyList(keys);
		KeyExpansion.expandKeyMinimum4Signing(aKey, 1, expandedKeys);
		final Set<Key> uniqueKeys = new HashSet<>(expandedKeys);
		final int len = findMinPrefixLength(uniqueKeys);

		final List<SignaturePair> pairs = new ArrayList<>();
		for (Key key : uniqueKeys) {
			if (key.hasContractID()) {
				// according to Leemon, "for Hedera transactions, we treat this key as never having signatures."
				continue;
			}

			final var sig = KeyExpansion.signBasicAsSignaturePair(key, len, pubKey2privKeyMap, messageBytes);
			pairs.add(sig);
		}

		return SignatureMap.newBuilder().addAllSigPair(pairs).build();
	}

	/**
	 * Finds the minimum prefix length in term of bytes.
	 *
	 * @param keys
	 * 		set of keys to process
	 * @return found minimum prefix length
	 */
	private static int findMinPrefixLength(final Set<Key> keys) {
		if (keys.size() == 1) {
			return 3;
		}

		//convert set to list of key hex strings
		//find max string length
		final List<String> keyHexes = new ArrayList<>();
		int maxBytes = 0;
		for (Key key : keys) {
			final var bytes = key.getEd25519().toByteArray();
			if (bytes.length > maxBytes) {
				maxBytes = bytes.length;
			}
			keyHexes.add(com.swirlds.common.CommonUtils.hex(bytes));
		}

		int rv = maxBytes;

		//starting from first byte (each byte is 2 hex chars) to max/2 and loop with step of 2
		for (int i = 1; i <= maxBytes; i++) {
			// get all the prefixes and form a set (unique ones), check if size of the set is reduced.
			final Set<String> prefixSet = new HashSet<>();
			for (String khex : keyHexes) {
				prefixSet.add(khex.substring(0, i * 2));
			}
			// if not reduced, the current prefix size is the answer, stop
			if (prefixSet.size() == keys.size()) {
				rv = i;
				break;
			}
		}

		return Math.max(3, rv);
	}
}
