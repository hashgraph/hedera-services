package com.hedera.services.legacy.core.jproto;

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
import com.hederahashgraph.api.proto.java.ContractID;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.KeyList;
import com.hederahashgraph.api.proto.java.ThresholdKey;
import org.apache.commons.codec.DecoderException;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Maps to proto Key.
 */
public abstract class JKey implements Serializable {
	static final int MAX_KEY_DEPTH = 15;

	private static final long serialVersionUID = 1L;

	private boolean forScheduledTxn = false;

	/**
	 * Maps a proto Key to Jkey.
	 *
	 * @param key
	 * 		the proto Key to be converted
	 * @return the generated JKey instance
	 * @throws DecoderException
	 * 		on an inconvertible given key
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
	 * @return the converted JKey instance
	 * @throws DecoderException
	 * 		on an inconvertible given key
	 */
	public static JKey convertKey(Key key, int depth) throws DecoderException {
		if (depth > MAX_KEY_DEPTH) {
			throw new DecoderException("Exceeding max expansion depth of " + MAX_KEY_DEPTH);
		}

		if (!(key.hasThresholdKey() || key.hasKeyList())) {
			return convertBasic(key);
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
			return new JKeyList(jkeys);
		}
	}

	/**
	 * Converts a basic key.
	 *
	 * @param key
	 * 		proto Key to be converted
	 * @return the converted JKey instance
	 * @throws DecoderException
	 * 		on an inconvertible given key
	 */
	private static JKey convertBasic(Key key) throws DecoderException {
		JKey rv;
		if (!key.getEd25519().isEmpty()) {
			byte[] pubKeyBytes = key.getEd25519().toByteArray();
			rv = new JEd25519Key(pubKeyBytes);
		} else if (!key.getECDSA384().isEmpty()) {
			byte[] pubKeyBytes = key.getECDSA384().toByteArray();
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
	 * @return the converted proto Key instance
	 * @throws DecoderException
	 * 		on an inconvertible given key
	 */
	static Key convertJKeyBasic(JKey jkey) throws DecoderException {
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
			throw new DecoderException("Key type not implemented: key=" + jkey);
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
	 * @return the converted proto Key instance
	 * @throws DecoderException
	 * 		on an inconvertible given key
	 */
	public static Key convertJKey(JKey jkey, int depth) throws DecoderException {
		if (depth > MAX_KEY_DEPTH) {
			throw new DecoderException("Exceeding max expansion depth of " + MAX_KEY_DEPTH);
		}

		if (!(jkey.hasThresholdKey() || jkey.hasKeyList())) {
			return convertJKeyBasic(jkey);
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
	 * @return the converted proto Key instance
	 * @throws DecoderException
	 * 		on an inconvertible given key
	 */
	public static Key mapJKey(JKey jkey) throws DecoderException {
		return convertJKey(jkey, 1);
	}

	public byte[] serialize() throws IOException {
		return JKeySerializer.serialize(this);
	}

	public abstract boolean isEmpty();

	/**
	 * Expected to return {@code false} if the key is empty
	 *
	 * @return whether the key is valid
	 */
	public abstract boolean isValid();

	public void setForScheduledTxn(boolean flag) {
		forScheduledTxn = flag;
	}

	public boolean isForScheduledTxn() {
		return forScheduledTxn;
	}

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

	public JKey duplicate() {
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
