package com.hedera.services.legacy.unit.serialization;

/*-
 * ‌
 * Hedera Services Node
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

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.protobuf.ByteString;
import com.hedera.services.utils.MiscUtils;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.KeyList;
import com.hederahashgraph.api.proto.java.ThresholdKey;
import com.hedera.services.legacy.config.PropertiesLoader;
import com.hedera.services.legacy.core.jproto.JEd25519Key;
import com.hedera.services.legacy.core.jproto.JKey;
import com.hedera.services.legacy.core.jproto.JKeyList;
import com.hedera.services.legacy.core.jproto.JKeySerializer;
import com.hedera.services.legacy.core.jproto.JThresholdKey;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.security.KeyPair;
import java.security.PrivateKey;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.hedera.services.legacy.proto.utils.AtomicCounter;
import net.i2p.crypto.eddsa.EdDSAPublicKey;
import net.i2p.crypto.eddsa.KeyPairGenerator;
import org.junit.FixMethodOrder;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.platform.runner.JUnitPlatform;
import org.junit.runner.RunWith;
import org.junit.runners.MethodSorters;
import org.junit.Assert;

/**
 * @author oc
 * @version Junit5 Tests the JKeySerializer 1) Create a JThreshold Key , sez & desez 2) Create a
 * 		JKeyList Key , sez & desez 3) Create a JEd25519Key, sez & desez 4) Create a JECDSA_383Key, sez &
 * 		desez 5) Create a Key Proto, sez & desez
 */
@RunWith(JUnitPlatform.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
@DisplayName("JKeySerializer Test Suite")
public class JkeySerializerTest {


	@BeforeAll
	public void setUp() {

	}

	/**
	 * Tester Util Class
	 */
	public JKey getSpecificJKeysMade(String action, int numKeys, int depth) {
		JKey jkey = null;
		List<JKey> keyList = new ArrayList<>();
		List<PrivateKey> privKeyList = new ArrayList<>();
		int totalKeys = (int) Math.pow(numKeys, depth - 1);
		for (int i = 0; i < totalKeys; i++) {
			KeyPair pair = new KeyPairGenerator().generateKeyPair();
			byte[] pubKey = ((EdDSAPublicKey) pair.getPublic()).getAbyte();
			JKey akey = new JEd25519Key(pubKey);
			PrivateKey priv = pair.getPrivate();
			privKeyList.add(priv);
			keyList.add(akey);
		}

		if ("JThresholdKey".equalsIgnoreCase(action)) {
			int threshold = 3;
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
	public JKey genThresholdKeyRecursive(int numKeys, int threshold, List<JKey> keyList) {
		// if bottom level
		if (keyList.size() == 1) {
			return keyList.get(0);
		}

		List<JKey> combinedList = new ArrayList<>();

		int len = keyList.size();
		int pos = 0;
		while (pos < len) {
			List<JKey> keys = new ArrayList<>();
			for (int j = 0; j < numKeys; j++) {
				keys.add(keyList.get(pos++));
			}
			JKeyList keysList = new JKeyList(keys);
			JKey athkey = new JThresholdKey(keysList, threshold);
			combinedList.add(athkey);
		}

		return genThresholdKeyRecursive(numKeys, threshold, combinedList);
	}

	/**
	 * @param numKeys
	 * @param keyList
	 * @return
	 */
	public JKey genKeyListRecursive(int numKeys, List<JKey> keyList) {
		// if bottom level
		if (keyList.size() == 1) {
			return keyList.get(0);
		}

		List<JKey> combinedList = new ArrayList<>();

		int len = keyList.size();
		int pos = 0;
		while (pos < len) {
			List<JKey> myKeys = new ArrayList<>();
			for (int j = 0; j < numKeys; j++) {
				myKeys.add(keyList.get(pos++));
			}
			JKeyList jkeyList = new JKeyList(myKeys);
			combinedList.add(jkeyList);
		}

		return genKeyListRecursive(numKeys, combinedList);
	}

	public static Key genSingleEd25519Key(Map<String, PrivateKey> pubKey2privKeyMap) {
		KeyPair pair = new KeyPairGenerator().generateKeyPair();
		byte[] pubKey = ((EdDSAPublicKey) pair.getPublic()).getAbyte();
		Key akey = Key.newBuilder().setEd25519(ByteString.copyFrom(pubKey)).build();
		String pubKeyHex = MiscUtils.commonsBytesToHex(pubKey);
		pubKey2privKeyMap.put(pubKeyHex, pair.getPrivate());
		return akey;
	}


	/**
	 * Generates a complex key of given depth with a mix of basic key, threshold key and key list.
	 *
	 * @param depth
	 * 		of the generated key
	 * @return generated key
	 */
	public static Key genSampleComplexKey(int depth, Map<String, PrivateKey> pubKey2privKeyMap)
			throws Exception {
		Key rv = null;
		int numKeys = 3;
		int threshold = 2;

		if (depth == 1) {
			rv = genSingleEd25519Key(pubKey2privKeyMap);

			//verify the size
			int size = computeNumOfExpandedKeys(rv, 1, new AtomicCounter());
			Assert.assertEquals(1, size);
		} else if (depth == 2) {
			List<Key> keys = new ArrayList<>();
			keys.add(genSingleEd25519Key(pubKey2privKeyMap));
			keys.add(genThresholdKeyInstance(numKeys, threshold, pubKey2privKeyMap));
			keys.add(genKeyListInstance(numKeys, pubKey2privKeyMap));
			rv = genKeyList(keys);

			//verify the size
			int size = computeNumOfExpandedKeys(rv, 1, new AtomicCounter());
			Assert.assertEquals(1 + numKeys * 2, size);
		} else {
			throw new Exception("Not implemented yet.");
		}

		return rv;
	}

	/**
	 * Computes number of expanded keys by traversing the key recursively.
	 *
	 * @param key
	 * 		the complex key to be computed
	 * @param depth
	 * 		current level that is to be traversed. The first level has a value of 1.
	 * @param counter
	 * 		keeps track the number of keys
	 * @return number of expanded keys
	 */

	public static int computeNumOfExpandedKeys(Key key, int depth, AtomicCounter counter) {
		if (!(key.hasThresholdKey() || key.hasKeyList())) {
			counter.increment();
			return counter.value();
		}

		List<Key> tKeys = null;
		if (key.hasThresholdKey()) {
			tKeys = key.getThresholdKey().getKeys().getKeysList();
		} else {
			tKeys = key.getKeyList().getKeysList();
		}

		if (depth <= PropertiesLoader.getKeyExpansionDepth()) {
			depth++;
			for (Key aKey : tKeys) {
				computeNumOfExpandedKeys(aKey, depth, counter);
			}
		}

		return counter.value();
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
	private static Key genKeyListInstance(int numKeys, Map<String, PrivateKey> pubKey2privKeyMap) {
		List<Key> keys = genEd25519Keys(numKeys, pubKey2privKeyMap);
		Key rv = genKeyList(keys);
		return rv;
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
	private static Key genThresholdKeyInstance(int numKeys, int threshold,
			Map<String, PrivateKey> pubKey2privKeyMap) {
		List<Key> keys = genEd25519Keys(numKeys, pubKey2privKeyMap);
		Key rv = genThresholdKey(keys, threshold);
		return rv;
	}

	/**
	 * Generates a list of Ed25519 keys.
	 *
	 * @param pubKey2privKeyMap
	 * 		map of public key hex string as key and the private key as value
	 * @return a list of generated Ed25519 keys
	 */
	public static List<Key> genEd25519Keys(int numKeys, Map<String, PrivateKey> pubKey2privKeyMap) {
		List<Key> rv = new ArrayList<>();
		for (int i = 0; i < numKeys; i++) {
			KeyPair pair = new KeyPairGenerator().generateKeyPair();
			byte[] pubKey = ((EdDSAPublicKey) pair.getPublic()).getAbyte();
			Key akey = Key.newBuilder().setEd25519(ByteString.copyFrom(pubKey)).build();
			String pubKeyHex = MiscUtils.commonsBytesToHex(pubKey);
			pubKey2privKeyMap.put(pubKeyHex, pair.getPrivate());
			rv.add(akey);
		}

		return rv;
	}

	/**
	 * Generates a threshold key from a list of keys.
	 *
	 * @return generated threshold key
	 */
	public static Key genThresholdKey(List<Key> keys, int threshold) {
		ThresholdKey tkey = ThresholdKey.newBuilder()
				.setKeys(KeyList.newBuilder().addAllKeys(keys).build())
				.setThreshold(threshold).build();
		Key rv = Key.newBuilder().setThresholdKey(tkey).build();
		return rv;
	}

	/**
	 * Generates a KeyList key from a list of keys.
	 *
	 * @return generated KeyList key
	 */
	public static Key genKeyList(List<Key> keys) {
		KeyList tkey = KeyList.newBuilder().addAllKeys(keys).build();
		Key rv = Key.newBuilder().setKeyList(tkey).build();
		return rv;
	}

	@Test
	@DisplayName("01.Generate JThreshold & Test")
	public void aa_buildJThresholdKeys() {
		JKey threshold = getSpecificJKeysMade("JThresholdKey", 3, 3);
		JKeyList beforeKeyList = threshold.getThresholdKey().getKeys();
		int beforeJKeyListSize = beforeKeyList.getKeysList().size();
		byte[] serial_thkey = null;
		try {
			serial_thkey = threshold.serialize();
		} catch (Exception ex) {
			System.out.println("Serialization Failed " + ex.getMessage());
		}
		assertNotNull(serial_thkey);
		// Now take the bytearray and build it back

		ByteArrayInputStream in = null;
		DataInputStream dis = null;
		JKey jkeyReborn;
		try {
			in = new ByteArrayInputStream(serial_thkey);
			dis = new DataInputStream(in);

			jkeyReborn = JKeySerializer.deserialize(dis);
			//Write Assertions Here
			assertAll("JKeyRebornChecks1",
					() -> assertNotNull(jkeyReborn),
					() -> assertTrue(jkeyReborn instanceof JThresholdKey),
					() -> assertTrue(jkeyReborn.hasThresholdKey()),
					() -> assertEquals(jkeyReborn.getThresholdKey().getThreshold(), 3)
			);
			JKeyList afterJkeysList = jkeyReborn.getThresholdKey().getKeys();

			assertAll("JKeyRebornChecks2",
					() -> assertNotNull(afterJkeysList),
					() -> assertTrue(afterJkeysList.getKeysList() != null)
			);

			int afterJkeysListSize = afterJkeysList.getKeysList().size();

			assertAll("JKeyRebornChecks2",
					() -> assertEquals(afterJkeysListSize, beforeJKeyListSize));

			dis.close();
			in.close();

		} catch (Exception ex) {
			System.out.println("**EXCEPTION**" + ex.getMessage());
		} finally {
			dis = null;
			in = null;
		}

	}

	@Test
	@DisplayName("02.Test JKeyList Keys Sez-Desez")
	public void bb_buildTestKeyList() {

		JKey jkeyList = getSpecificJKeysMade("JKeyList", 3, 3);
		int beforeJKeyListSize = jkeyList.getKeyList().getKeysList().size();

		byte[] serialized_jkey = null;
		try {
			serialized_jkey = jkeyList.serialize();
		} catch (Exception ex) {
			System.out.println("Serialization Failed " + ex.getMessage());
		}
		assertNotNull(serialized_jkey);

		ByteArrayInputStream in = null;
		DataInputStream dis = null;
		JKey jkeyReborn;
		try {
			in = new ByteArrayInputStream(serialized_jkey);
			dis = new DataInputStream(in);

			jkeyReborn = JKeySerializer.deserialize(dis);
			//Write Assertions Here
			assertAll("JKeyRebornChecks1",
					() -> assertNotNull(jkeyReborn),
					() -> assertTrue(jkeyReborn instanceof JKeyList),
					() -> assertTrue(jkeyReborn.hasKeyList()),
					() -> assertFalse(jkeyReborn.hasThresholdKey())
			);

			JKeyList afterJkeysList = jkeyReborn.getKeyList();

			assertAll("JKeyRebornChecks2",
					() -> assertNotNull(afterJkeysList),
					() -> assertTrue(afterJkeysList.getKeysList() != null)
			);

			int afterJkeysListSize = afterJkeysList.getKeysList().size();

			assertAll("JKeyRebornChecks2",
					() -> assertEquals(afterJkeysListSize, beforeJKeyListSize));

			dis.close();
			in.close();

		} catch (Exception ex) {
			System.out.println("**EXCEPTION**" + ex.getMessage());
		} finally {
			dis = null;
			in = null;
		}

	}


	@Test
	@DisplayName("03.Test JKey Proto Sez-Desez")
	public void cc_buildTestJKeyComplex() {

		Map<String, PrivateKey> pubKey2privKeyMap = new HashMap<>();
		Key protoKey;
		JKey jkey = null;
		JThresholdKey jThresholdBefore = null;
		JThresholdKey jThresholdAfter = null;

		List<JKey> jListBef = null;
		//Jkey will have JEd25519Key,JThresholdKey,JKeyList
		try {
			protoKey = genSampleComplexKey(2, pubKey2privKeyMap);
			jkey = JKey.mapKey(protoKey);
			jListBef = jkey.getKeyList().getKeysList();

		} catch (Exception ex) {
			System.out.println("Error Occurred" + ex.getMessage());
			ex.printStackTrace();
		}
		byte[] serialized_jkey = null;
		try {
			serialized_jkey = jkey.serialize();
		} catch (Exception tex) {
			System.out.println("Error Occurred" + tex.getMessage());
			tex.printStackTrace();
		}

		ByteArrayInputStream in = null;
		DataInputStream dis = null;
		final JKey jkeyReborn;
		try {
			in = new ByteArrayInputStream(serialized_jkey);
			dis = new DataInputStream(in);

			jkeyReborn = JKeySerializer.deserialize(dis);
			//Write Top Assertions Here

			assertAll("JKeyRebornChecks-Top Level",
					() -> assertNotNull(jkeyReborn),
					() -> assertTrue(jkeyReborn instanceof JKeyList),
					() -> assertTrue(jkeyReborn.hasKeyList()),
					() -> assertFalse(jkeyReborn.hasThresholdKey())
			);

			List<JKey> jListAft = jkeyReborn.getKeyList().getKeysList();

			assertEquals(jListBef.size(), jListAft.size());
			//Order Checks
			for (int i = 0; i > jListBef.size(); i++) {
				assertEquals(jListAft.get(i), jListBef.get(i));
			}

			in.close();

		} catch (Exception ex) {
			System.out.println("**EXCEPTION**" + ex.getMessage());
			ex.printStackTrace();
		} finally {
			dis = null;
			in = null;
		}

	}

}
