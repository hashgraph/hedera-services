package com.hedera.services.legacy.core.jproto;

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

import com.google.protobuf.ByteString;
import com.google.protobuf.TextFormat;
import com.hedera.services.utils.MiscUtils;
import com.hederahashgraph.api.proto.java.ContractID;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.KeyList;
import com.hederahashgraph.api.proto.java.ThresholdKey;
import com.hedera.services.legacy.proto.utils.KeyExpansion;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.apache.commons.codec.DecoderException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Maps to proto Key.
 *
 * @author hua Created on 2018-11-02
 */
public abstract class JKey implements Serializable, Cloneable {
	private static final long serialVersionUID = 1L;
	private static final Logger log = LogManager.getLogger(JKey.class);
	private static boolean USE_HEX_ENCODED_KEY = KeyExpansion.USE_HEX_ENCODED_KEY;

	/**
	 * Maps a proto Key to Jkey.
	 *
	 * @param key
	 * 		the proto Key to be converted
	 * @return the generated JKey instance
	 */
	public static JKey mapKey(Key key) throws DecoderException {
		return convertKey(key, 1);
	}

	/**
	 * Converts a key up to a given level of depth. Both the signature and the key may be complex with
	 * multiple levels.
	 *
	 * @param key
	 * 		the current proto Key to be converted
	 * @param depth
	 * 		current level that is to be verified. The first level has a value of 1.
	 */
	public static JKey convertKey(Key key, int depth) throws DecoderException {
		if (depth > KeyExpansion.KEY_EXPANSION_DEPTH) {
			throw new DecoderException("Exceeding max expansion depth of " + KeyExpansion.KEY_EXPANSION_DEPTH);
		}

		if (!(key.hasThresholdKey() || key.hasKeyList())) {
			JKey result = convertBasic(key);
			return result;
		} else if (key.hasThresholdKey()) {
			List<Key> tKeys = key.getThresholdKey().getKeys().getKeysList();
			List<JKey> jkeys = new ArrayList<>();
			for (Key aKey : tKeys) {
				JKey res = convertKey(aKey, depth + 1);
				jkeys.add(res);
			}
			JKeyList keys = new JKeyList(jkeys);
			int thd = key.getThresholdKey().getThreshold();
			JKey result = new JThresholdKey(keys, thd);
			return (result);
		} else {
			List<Key> tKeys = key.getKeyList().getKeysList();
			List<JKey> jkeys = new ArrayList<>();
			for (Key aKey : tKeys) {
				JKey res = convertKey(aKey, depth + 1);
				jkeys.add(res);
			}
			int thd = tKeys.size();
			JKey result = new JKeyList(jkeys);
			return (result);
		}
	}

	/**
	 * Converts a basic key.
	 *
	 * @param key
	 * 		proto Key to be converted
	 * @return converted JKey instance
	 */
	private static JKey convertBasic(Key key) throws DecoderException {
		JKey rv;
		if (!key.getEd25519().isEmpty()) {
			byte[] pubKeyBytes = null;
			if (USE_HEX_ENCODED_KEY) {
				String pubKeyHex = key.getEd25519().toStringUtf8();
				pubKeyBytes = MiscUtils.commonsHexToBytes(pubKeyHex);
			} else {
				pubKeyBytes = key.getEd25519().toByteArray();
			}

			rv = new JEd25519Key(pubKeyBytes);
		} else if (!key.getECDSA384().isEmpty()) {
			byte[] pubKeyBytes = null;
			if (USE_HEX_ENCODED_KEY) {
				String pubKeyHex = key.getECDSA384().toStringUtf8();
				pubKeyBytes = MiscUtils.commonsHexToBytes(pubKeyHex);
			} else {
				pubKeyBytes = key.getECDSA384().toByteArray();
			}

			rv = new JECDSA_384Key(pubKeyBytes);
		} else if (!key.getRSA3072().isEmpty()) {
			byte[] pubKeyBytes = key.getRSA3072().toByteArray();
			rv = new JRSA_3072Key(pubKeyBytes);
		} else if (key.getContractID() != null && key.getContractID().getContractNum() != 0) {
			ContractID cid = key.getContractID();
			rv = new JContractIDKey(cid);
		} else {
			throw new DecoderException("Key type not implemented: key=" + key);
		}

		return rv;
	}

	/**
	 * Converts a basic JKey to proto Key.
	 *
	 * @param jkey
	 * 		JKey object to be converted
	 * @return converted Key instance
	 */
	private static Key convertJKeyBasic(JKey jkey) throws Exception {
		Key rv = null;
		if (jkey.hasEd25519Key()) {
			rv = Key.newBuilder().setEd25519(ByteString.copyFrom(jkey.getEd25519())).build();
		} else if (jkey.hasECDSA_383Key()) {
			rv = Key.newBuilder().setECDSA384(ByteString.copyFrom(jkey.getECDSA384())).build();
		} else if (jkey.hasRSA_3072Key()) {
			rv = Key.newBuilder().setRSA3072(ByteString.copyFrom(jkey.getRSA3072())).build();
		} else if (jkey.hasContractID()) {
			rv = Key.newBuilder().setContractID(jkey.getContractIDKey().getContractID()).build();
		} else {
			throw new Exception("Key type not implemented: key=" + jkey);
		}

		return rv;
	}

	/**
	 * Converts a JKey to proto Key for up to a given level of depth.
	 *
	 * @param jkey
	 * 		the current JKey to be converted
	 * @param depth
	 * 		current level that is to be verified. The first level has a value of 1.
	 * @return converted proto Key
	 */
	public static Key convertJKey(JKey jkey, int depth) throws Exception {
		if (depth > KeyExpansion.KEY_EXPANSION_DEPTH) {
			log.debug("Exceeding max expansion depth of " + KeyExpansion.KEY_EXPANSION_DEPTH);
		}

		if (!(jkey.hasThresholdKey() || jkey.hasKeyList())) {
			Key result = convertJKeyBasic(jkey);
			return result;
		} else if (jkey.hasThresholdKey()) {
			List<JKey> jKeys = jkey.getThresholdKey().getKeys().getKeysList();
			List<Key> tkeys = new ArrayList<>();
			for (JKey aKey : jKeys) {
				Key res = convertJKey(aKey, depth + 1);
				tkeys.add(res);
			}
			KeyList keys = KeyList.newBuilder().addAllKeys(tkeys).build();
			int thd = jkey.getThresholdKey().getThreshold();
			Key result = Key.newBuilder()
					.setThresholdKey(ThresholdKey.newBuilder().setKeys(keys).setThreshold(thd)).build();
			return (result);
		} else {
			List<JKey> jKeys = jkey.getKeyList().getKeysList();
			List<Key> tkeys = new ArrayList<>();
			for (JKey aKey : jKeys) {
				Key res = convertJKey(aKey, depth + 1);
				tkeys.add(res);
			}
			KeyList keys = KeyList.newBuilder().addAllKeys(tkeys).build();
			int thd = jKeys.size();
			Key result = Key.newBuilder().setKeyList(keys).build();
			return (result);
		}
	}

	public static boolean equalUpToDecodability(JKey a, JKey b) {
		Key aKey = null, bKey = null;
		try {
			aKey = mapJKey(a);
		} catch (Exception ignore) {
		}
		try {
			bKey = mapJKey(b);
		} catch (Exception ignore) {
		}
		return Objects.equals(aKey, bKey);
	}

	/**
	 * Maps a JKey instance to a proto Key instance.
	 *
	 * @param jkey
	 * 		the JKey to be converted
	 * @return the converted Key instance
	 */
	public static Key mapJKey(JKey jkey) throws Exception {
		return convertJKey(jkey, 1);
	}

	public byte[] serialize() throws IOException {
		return JKeySerializer.serialize(this);
	}

	public abstract boolean isEmpty();

	//Key is not empty and has valid format
	public abstract boolean isValid();

	public boolean hasEd25519Key() {
		return false;
	}

	public boolean hasECDSA_383Key() {
		return false;
	}

	public boolean hasRSA_3072Key() {
		return false;
	}

	public boolean hasKeyList() {
		return false;
	}

	public boolean hasThresholdKey() {
		return false;
	}

	public boolean hasContractID() {
		return false;
	}

	public JContractIDKey getContractIDKey() {
		return null;
	}

	public JThresholdKey getThresholdKey() {
		return null;
	}

	public JKeyList getKeyList() {
		return null;
	}

	public byte[] getEd25519() {
		return null;
	}

	public byte[] getECDSA384() {
		return null;
	}

	public byte[] getRSA3072() {
		return null;
	}

	@Override
	public JKey clone() {
		try {
			var buf = serialize();
			try (var bs = new ByteArrayInputStream(buf)) {
				try (var is = new DataInputStream(bs)) {
					return JKeySerializer.deserialize(is);
				}
			}
		} catch (IOException ex) {
			throw new IllegalArgumentException(ex);
		}
	}
}
