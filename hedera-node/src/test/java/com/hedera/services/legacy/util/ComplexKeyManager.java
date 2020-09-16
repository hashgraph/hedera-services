package com.hedera.services.legacy.util;

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

import java.security.PrivateKey;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.FileID;
import com.hederahashgraph.api.proto.java.Key;
import com.hedera.services.legacy.proto.utils.KeyExpansion;

public class ComplexKeyManager {

	public static enum SUPPORTE_KEY_TYPES {
		single, keylist, thresholdKey
	}

	private static int COMPLEX_KEY_SIZE;
	private static int COMPLEX_KEY_THRESHOLD;
	private static Map<String, PrivateKey> pubKey2privKeyMap = new LinkedHashMap<>();
	private static Map<AccountID, Key> acc2ComplexKeyMap = new LinkedHashMap<>();
	private static ConcurrentHashMap<FileID, List<Key>> fid2waclMap = new ConcurrentHashMap<>();

	/**
	 * Generates a complex key up to 2 levels.
	 *
	 * @param accountKeyType
	 * 		complex key type
	 */
	public static Key genComplexKey(String accountKeyType) throws Exception {
		Key key = null;
		if (accountKeyType.equals(SUPPORTE_KEY_TYPES.thresholdKey.name())) {
			key = KeyExpansion
					.genThresholdKeyInstance(COMPLEX_KEY_SIZE, COMPLEX_KEY_THRESHOLD, pubKey2privKeyMap);
		} else if (accountKeyType.equals(SUPPORTE_KEY_TYPES.keylist.name())) {
			key = KeyExpansion.genKeyListInstance(COMPLEX_KEY_SIZE, pubKey2privKeyMap);
		} else {
			key = KeyExpansion.genSingleEd25519KeyByteEncodePubKey(pubKey2privKeyMap);
		}

		return key;
	}

	/**
	 * Generates wacls keys.
	 *
	 * @param numKeys
	 * 		number of keys to generate, each key may have different key type
	 * @return generated list of keys
	 */
	public static List<Key> genWaclComplex(int numKeys, String accountKeyType) throws Exception {
		List<Key> keys = new ArrayList<>();
		for (int i = 0; i < numKeys; i++) {
			Key key = genComplexKey(accountKeyType);
			keys.add(key);
		}

		return keys;
	}

	public static List<Key> genWaclComplex() throws Exception {
		return genWaclComplex(1, SUPPORTE_KEY_TYPES.single.name());
	}

	public static Key getAccountKey(AccountID accountID) {
		return acc2ComplexKeyMap.get(accountID);
	}

	public static void setAccountKey(AccountID accountID, Key key) {
		acc2ComplexKeyMap.put(accountID, key);
	}

	public static List<Key> getFileWacl(FileID fileID) {
		return fid2waclMap.get(fileID);
	}

	public static void setFileWacl(FileID fileID, List<Key> keys) {
		fid2waclMap.put(fileID, keys);
	}

	public static Map<String, PrivateKey> getPubKey2privKeyMap() {
		return pubKey2privKeyMap;
	}

	public static void removeFileWacl(FileID fid) {
		fid2waclMap.remove(fid);
	}

}
