package com.hedera.services.statecreation.creationtxns.utils;

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
import com.hedera.services.legacy.core.KeyPairObj;
import com.hederahashgraph.api.proto.java.Key;
import com.swirlds.common.CommonUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.security.KeyStoreException;
import java.security.PrivateKey;
import java.util.HashMap;
import java.util.Map;

public class KeyFactory {
	private static final Logger log = LogManager.getLogger(KeyFactory.class);

	public static KeyPairObj genesisKeyPair;

	private static String pemFile = "data/onboard/devGenesisKeypair.pem";

	private static final KeyFactory DEFAULT_INSTANCE = new KeyFactory();

	public static final KeyFactory getDefaultInstance() {
		return DEFAULT_INSTANCE;
	}

	private static Map<String, PrivateKey> publicToPrivateKey ;

	public KeyFactory() {
		readKeyFromPemFile();
	}

	public static Key getKey() {
		Key genKey = null;
		try {
			KeyPairObj genKeyObj = genesisKeyPair;
			genKey = asPublicKey(genKeyObj.getPublicKeyAbyteStr());
		} catch (Exception e) {
			log.warn("Can't get the generic key ", e);
		}
		return genKey;
	}

	public PrivateKey lookupPrivateKey(String pubKeyHex) {
		return publicToPrivateKey.get(pubKeyHex);
	}

	public static String asPubKeyHex(Key key) {
		assert(!key.hasKeyList() && !key.hasThresholdKey());
		if (key.getRSA3072() != ByteString.EMPTY) {
			return CommonUtils.hex(key.getRSA3072().toByteArray());
		} else if (key.getECDSA384() != ByteString.EMPTY) {
			return CommonUtils.hex(key.getECDSA384().toByteArray());
		} else {
			return CommonUtils.hex(key.getEd25519().toByteArray());
		}
	}

	public static Key asPublicKey(String pubKeyHex)  {
		return Key.newBuilder()
				.setEd25519(ByteString.copyFrom(CommonUtils.unhex(pubKeyHex)))
				.build();
	}

	public static void readKeyFromPemFile()  {
		try {
			File fin = new File(pemFile);
			genesisKeyPair  = asOcKeystore(fin, "passphrase");
		} catch (Exception e) {
			log.error("Can't get pem file at {}", pemFile, e);
		}
		try {
			String pubKeyStr = genesisKeyPair.getPublicKeyAbyteStr();
			PrivateKey privateKey = genesisKeyPair.getPrivateKey();
			if(publicToPrivateKey == null) {
				publicToPrivateKey = new HashMap<>();
			}
			publicToPrivateKey.put(pubKeyStr, privateKey);
			log.info("Loaded the genesis key successfully.");
		} catch (Exception e) {
			log.error("Can't get the key from pemfile", e);
		}
	}


	public static KeyPairObj asOcKeystore(File aes256EncryptedPkcs8Pem, String passphrase) throws KeyStoreException {
		var keyStore = Ed25519KeyStore.read(passphrase.toCharArray(), aes256EncryptedPkcs8Pem);
		var keyPair = keyStore.get(0);
		return new KeyPairObj(
				com.swirlds.common.CommonUtils.hex(keyPair.getPublic().getEncoded()),
				com.swirlds.common.CommonUtils.hex(keyPair.getPrivate().getEncoded()));
	}
}
