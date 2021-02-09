package com.hedera.test.factories.keys;

/*-
 * ‌
 * Hedera Services Node
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
import com.hedera.services.utils.MiscUtils;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.KeyList;
import com.hederahashgraph.api.proto.java.ThresholdKey;
import net.i2p.crypto.eddsa.EdDSAPublicKey;
import net.i2p.crypto.eddsa.KeyPairGenerator;

import java.security.KeyPair;
import java.security.PrivateKey;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class KeyFactory {
	private static final KeyFactory DEFAULT_INSTANCE = new KeyFactory();

	public static final KeyFactory getDefaultInstance() {
		return DEFAULT_INSTANCE;
	}

	private final KeyGenerator keyGen;
	private final Map<String, Key> labelToEd25519 = new HashMap<>();
	private final Map<String, PrivateKey> publicToPrivateKey = new HashMap<>();

	public KeyFactory() {
		this(KeyFactory::genSingleEd25519Key);
	}
	public KeyFactory(KeyGenerator keyGen) {
		this.keyGen = keyGen;
	}

	public Key labeledEd25519(String label) {
		return labelToEd25519.computeIfAbsent(label, ignore -> newEd25519());
	}
	public Key newEd25519() {
		return keyGen.genEd25519AndUpdateMap(publicToPrivateKey);
	}
	public Key newList(List<Key> children) {
		return Key.newBuilder().setKeyList(KeyList.newBuilder().addAllKeys(children)).build();
	}
	public Key newThreshold(List<Key> children, int M) {
		ThresholdKey.Builder thresholdKey =
				ThresholdKey.newBuilder().setKeys(KeyList.newBuilder().addAllKeys(children).build()).setThreshold(M);
		return Key.newBuilder().setThresholdKey(thresholdKey).build();
	}

	public PrivateKey lookupPrivateKey(Key key) {
		return publicToPrivateKey.get(asPubKeyHex(key));
	}
	public PrivateKey lookupPrivateKey(String pubKeyHex) {
		return publicToPrivateKey.get(pubKeyHex);
	}
	public static String asPubKeyHex(Key key) {
		assert(!key.hasKeyList() && !key.hasThresholdKey());
		if (key.getRSA3072() != ByteString.EMPTY) {
			return MiscUtils.commonsBytesToHex(key.getRSA3072().toByteArray());
		} else if (key.getECDSA384() != ByteString.EMPTY) {
			return MiscUtils.commonsBytesToHex(key.getECDSA384().toByteArray());
		} else {
			return MiscUtils.commonsBytesToHex(key.getEd25519().toByteArray());
		}
	}

	/**
	 * Generates a single Ed25519 key.
	 *
	 * @param pubKey2privKeyMap map of public key hex string as key and the private key as value
	 * @return generated Ed25519 key
	 */
	public static Key genSingleEd25519Key(Map<String, PrivateKey> pubKey2privKeyMap) {
		KeyPair pair = new KeyPairGenerator().generateKeyPair();
		byte[] pubKey = ((EdDSAPublicKey) pair.getPublic()).getAbyte();
		Key akey = Key.newBuilder().setEd25519(ByteString.copyFrom(pubKey)).build();
		String pubKeyHex = MiscUtils.commonsBytesToHex(pubKey);
		pubKey2privKeyMap.put(pubKeyHex, pair.getPrivate());
		return akey;
	}
}
