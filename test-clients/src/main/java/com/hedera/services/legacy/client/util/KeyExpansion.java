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

import com.google.protobuf.ByteString;
import com.hedera.services.legacy.proto.utils.SignatureGenerator;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.KeyList;
import com.hederahashgraph.api.proto.java.Signature;
import com.hederahashgraph.api.proto.java.SignatureList;
import com.hederahashgraph.api.proto.java.SignaturePair;
import com.hederahashgraph.api.proto.java.ThresholdKey;
import com.hederahashgraph.api.proto.java.ThresholdSignature;
import net.i2p.crypto.eddsa.EdDSAPrivateKey;
import net.i2p.crypto.eddsa.EdDSAPublicKey;
import net.i2p.crypto.eddsa.KeyPairGenerator;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.security.KeyPair;
import java.security.PrivateKey;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * This class provides utilities to expand keys.
 */
public class KeyExpansion {

	private static final Logger log = LogManager.getLogger(KeyExpansion.class);
	private static int KEY_EXPANSION_DEPTH = 15; // recursion level for expansion

	/**
	 * Generates a KeyList key from a list of keys.
	 *
	 * @param keys
	 * 		list of keys
	 * @return generated KeyList key
	 */
	public static Key genKeyList(final List<Key> keys) {
		final var keyList = KeyList.newBuilder().addAllKeys(keys).build();
		return Key.newBuilder().setKeyList(keyList).build();
	}

	/**
	 * Generates a list of Ed25519 keys.
	 *
	 * @param numKeys
	 * 		number of keys
	 * @param pubKey2privKeyMap
	 * 		map of public key hex string as key and the private key as value
	 * @return a list of generated Ed25519 keys
	 */
	public static List<Key> genEd25519Keys(final int numKeys, final Map<String, PrivateKey> pubKey2privKeyMap) {
		final List<Key> rv = new ArrayList<>();
		for (int i = 0; i < numKeys; i++) {
			Key akey = genSingleEd25519Key(pubKey2privKeyMap);
			rv.add(akey);
		}

		return rv;
	}

	/**
	 * Generates a threshold key from a list of keys.
	 *
	 * @param keys
	 * 		list of keys
	 * @param threshold
	 * 		threshold
	 * @return generated threshold key
	 */
	public static Key genThresholdKey(final List<Key> keys, final int threshold) {
		final var tkey = ThresholdKey.newBuilder()
				.setKeys(KeyList.newBuilder().addAllKeys(keys).build())
				.setThreshold(threshold).build();
		return Key.newBuilder().setThresholdKey(tkey).build();
	}

	/**
	 * Signs a message for a complex key up to a given level of depth. Both the signature and the key
	 * may be complex with multiple levels.
	 *
	 * @param key
	 * 		the complex key used to sign
	 * @param message
	 * 		message to be signed
	 * @param pubKey2privKeyMap
	 * 		map of public key hex string as key and the private key as value
	 * @param depth
	 * 		current level that is to be verified. The first level has a value of 1.
	 * @return the complex signature generated
	 * @throws Exception
	 * 		for failed sign
	 */
	public static Signature sign(final Key key,
			final byte[] message,
			final Map<String, PrivateKey> pubKey2privKeyMap,
			final int depth
	) throws Exception {
		if (depth > KEY_EXPANSION_DEPTH) {
			log.warn("Exceeding max expansion depth of " + KEY_EXPANSION_DEPTH);
		}

		if (!(key.hasThresholdKey() || key.hasKeyList())) {
			final var result = signBasic(key, pubKey2privKeyMap, message);
			log.debug("depth=" + depth + "; signBasic: result=" + result + "; key=" + key);
			return result;
		} else if (key.hasThresholdKey()) {
			final var tKeys = key.getThresholdKey().getKeys().getKeysList();
			final List<Signature> signatures = new ArrayList<>();
			int cnt = 0;
			final int thd = key.getThresholdKey().getThreshold();
			Signature signature = null;
			for (Key aKey : tKeys) {
				if (cnt < thd) {
					signature = sign(aKey, message, pubKey2privKeyMap, depth + 1);
					cnt++;
				} else {
					signature = genEmptySignature();
				}
				signatures.add(signature);
			}

			final var result = Signature.newBuilder()
					.setThresholdSignature(ThresholdSignature.newBuilder()
							.setSigs(SignatureList.newBuilder().addAllSigs(signatures)))
					.build();
			log.debug("depth=" + depth + "; sign ThresholdKey: result=" + result + "; threshold=" + thd);
			return result;
		} else {
			final var tKeys = key.getKeyList().getKeysList();
			final List<Signature> signatures = new ArrayList<>();
			for (Key aKey : tKeys) {
				final var signature = sign(aKey, message, pubKey2privKeyMap, depth + 1);
				signatures.add(signature);
			}

			final var result = Signature.newBuilder()
					.setSignatureList(SignatureList.newBuilder().addAllSigs(signatures))
					.build();
			log.debug("depth=" + depth + "; sign KeyList: result=" + result);
			return result;
		}
	}

	/**
	 * Generates an empty signature.
	 *
	 * @return the empty signature generated
	 */
	private static Signature genEmptySignature() throws IllegalArgumentException {
		final String EMPTY_STR = "";
		return Signature.newBuilder()
				.setEd25519(ByteString.copyFrom(com.swirlds.common.CommonUtils.unhex(EMPTY_STR)))
				.build();
	}

	/**
	 * Signs a basic key.
	 *
	 * @param pubKey2privKeyMap
	 * 		map of public key hex string as key and the private key as value
	 * @return the signature generated
	 */
	private static Signature signBasic(final Key key,
			final Map<String, PrivateKey> pubKey2privKeyMap,
			final byte[] msgBytes
	) throws Exception {
		if (key.hasContractID()) {
			return genEmptySignature();
		} else if (!key.getEd25519().isEmpty()) {
			final var pubKeyBytes = key.getEd25519().toByteArray();
			final var sigBytes = signatureBytes(pubKeyBytes, pubKey2privKeyMap, msgBytes);
			return Signature.newBuilder()
					.setEd25519(sigBytes)
					.build();
		} else if (!key.getECDSA384().isEmpty()) {
			final var pubKeyBytes = key.getECDSA384().toByteArray();
			final var sigBytes = signatureBytes(pubKeyBytes, pubKey2privKeyMap, msgBytes);
			return Signature.newBuilder()
					.setECDSA384(sigBytes)
					.build();
		}

		throw new Exception("Key type not implemented: key=" + key);
	}

	private static ByteString signatureBytes(final byte[] pubKeyBytes,
			final Map<String, PrivateKey> pubKey2privKeyMap,
			final byte[] msgBytes
	) throws Exception {
		final var pubKeyHex = com.swirlds.common.CommonUtils.hex(pubKeyBytes);
		final var privKey = pubKey2privKeyMap.get(pubKeyHex);
		final var sig = SignatureGenerator.signBytes(msgBytes, privKey);
		return ByteString.copyFrom(sig);
	}

	/**
	 * Generates a single Ed25519 key.
	 *
	 * @param pubKey2privKeyMap
	 * 		map of public key hex string as key and the private key as value
	 * @return generated Ed25519 key
	 */
	public static Key genSingleEd25519Key(final Map<String, PrivateKey> pubKey2privKeyMap) {
		final var pair = new KeyPairGenerator().generateKeyPair();
		final var pubKey = addKeyMap(pair, pubKey2privKeyMap);
		return keyFromBytes(pubKey);
	}

	public static Key keyFromPrivateKey(final PrivateKey privateKey) {
		final var pubKeyBytes = ((EdDSAPrivateKey) privateKey).getAbyte();
		return keyFromBytes(pubKeyBytes);
	}

	public static Key keyFromBytes(final byte[] bytes) {
		return Key.newBuilder().setEd25519(ByteString.copyFrom(bytes)).build();
	}

	public static byte[] addKeyMap(final KeyPair pair, final Map<String, PrivateKey> pubKey2privKeyMap) {
		final var pubKey = ((EdDSAPublicKey) pair.getPublic()).getAbyte();
		final var pubKeyHex = com.swirlds.common.CommonUtils.hex(pubKey);
		pubKey2privKeyMap.put(pubKeyHex, pair.getPrivate());
		return pubKey;
	}

	/**
	 * Generates a key list instance.
	 *
	 * @param numKeys
	 * 		number of keys in the generated key
	 * @param pubKey2privKeyMap
	 * 		map of public key hex string as key and the private key as value
	 * @return generated key list
	 */
	public static Key genKeyListInstance(final int numKeys, final Map<String, PrivateKey> pubKey2privKeyMap) {
		final var keys = genEd25519Keys(numKeys, pubKey2privKeyMap);
		return KeyExpansion.genKeyList(keys);
	}

	/**
	 * Generates a threshold key instance.
	 *
	 * @param numKeys
	 * 		number of keys in the generated key
	 * @param threshold
	 * 		the threshold for the generated key
	 * @param pubKey2privKeyMap
	 * 		map of public key hex string as key and the private key as value
	 * @return generated threshold key
	 */
	public static Key genThresholdKeyInstance(final int numKeys,
			final int threshold,
			final Map<String, PrivateKey> pubKey2privKeyMap
	) {
		final var keys = KeyExpansion.genEd25519Keys(numKeys, pubKey2privKeyMap);
		return KeyExpansion.genThresholdKey(keys, threshold);
	}

	/**
	 * Expands a key to a given level of depth, only keys needed for signing are expanded.
	 *
	 * @param key
	 * 		key
	 * @param depth
	 * 		depth
	 * @param expandedKeys
	 * 		list of expanded keys
	 */
	public static void expandKeyMinimum4Signing(final Key key, int depth, final List<Key> expandedKeys) {
		if (!(key.hasThresholdKey() || key.hasKeyList())) {
			expandedKeys.add(key);
		} else if (key.hasThresholdKey()) {
			final var tKeys = key.getThresholdKey().getKeys().getKeysList();
			final var thd = key.getThresholdKey().getThreshold();
			if (depth <= KEY_EXPANSION_DEPTH) {
				depth++;
				int i = 0;
				for (Key aKey : tKeys) {
					if (i++ >= thd) // if threshold is reached, stop expanding keys
					{
						log.debug("Threshold reached, stopping key expansion.");
						break;
					}
					expandKeyMinimum4Signing(aKey, depth, expandedKeys);
				}
			}
		} else {
			final var tKeys = key.getKeyList().getKeysList();
			if (depth <= KEY_EXPANSION_DEPTH) {
				depth++;
				for (Key aKey : tKeys) {
					expandKeyMinimum4Signing(aKey, depth, expandedKeys);
				}
			}
		}
	}

	/**
	 * Signs a basic key and returns a SignaturePair object.
	 *
	 * @param key
	 * 		key
	 * @param msgBytes
	 * 		message bytes
	 * @param pubKey2privKeyMap
	 * 		map of public key hex string as key and the private key as value
	 * @param prefixLen
	 * 		the length of the key prefix, if -1, use the full length of the key
	 * @return the SignaturePair generated
	 * @throws Exception
	 * 		when key type is not implemented
	 */
	public static SignaturePair signBasicAsSignaturePair(final Key key,
			final int prefixLen,
			final Map<String, PrivateKey> pubKey2privKeyMap,
			final byte[] msgBytes
	) throws Exception {
		if (key.getEd25519().isEmpty()) {
			throw new Exception("Key type not implemented: key=" + key);
		}
		final var pubKeyBytes = key.getEd25519().toByteArray();
		final var pubKeyHex = com.swirlds.common.CommonUtils.hex(pubKeyBytes);
		final var prefixBytes = prefixLen == -1
				? pubKeyBytes
				: Arrays.copyOfRange(pubKeyBytes, 0, prefixLen);

		final var privKey = pubKey2privKeyMap.get(pubKeyHex);
		final var sig = SignatureGenerator.signBytes(msgBytes, privKey);

		return SignaturePair.newBuilder()
				.setPubKeyPrefix(ByteString.copyFrom(prefixBytes))
				.setEd25519(ByteString.copyFrom(sig))
				.build();
	}
}
