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

import com.google.common.io.Files;

import com.google.protobuf.ByteString;
//import com.hedera.services.bdd.spec.HapiSpecSetup;
//import com.hedera.services.bdd.spec.persistence.SpecKey;
import com.hedera.services.legacy.core.AccountKeyListObj;
import com.hedera.services.legacy.core.KeyPairObj;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.KeyList;
import com.hederahashgraph.api.proto.java.ThresholdKey;
import com.swirlds.common.CommonUtils;
import net.i2p.crypto.eddsa.EdDSAPublicKey;
import net.i2p.crypto.eddsa.KeyPairGenerator;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectInputStream;
//import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyStoreException;
import java.security.PrivateKey;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

//import static com.hedera.services.bdd.suites.utils.keypairs.SpecUtils.asLegacyKp;

public class KeyFactory {
	private static final Logger log = LogManager.getLogger(KeyFactory.class);

	public static KeyPairObj genesisKeyPair;

//	private static String startupAccountLoc = "src/main/resources/StartUpAccount.txt";
	private static String startupAccountLoc = "src/main/resources/StartUpAccount.txt";
	private static String pemFile = "data/onboard/devGenesisKeypair.pem";
//	private static String pemFile = "data/onboard/topicSubmitKey.pem";
	private static String kpKey = "START_ACCOUNT";

	private static final KeyFactory DEFAULT_INSTANCE = new KeyFactory();

	public static final KeyFactory getDefaultInstance() {
		return DEFAULT_INSTANCE;
	}

	private final KeyGenerator keyGen;
	private static final Map<String, Key> labelToEd25519 = new HashMap<>();
	private static Map<String, PrivateKey> publicToPrivateKey ; // = new HashMap<>();

	public KeyFactory() {
		this(KeyFactory::genSingleEd25519Key);
	//	firstStartupKp();
		readKeyFromPemFile();
	}

	public static Key getKey() {
		Key genKey = null;
		try {
			KeyPairObj genKeyObj = genesisKeyPair;
			genKey = asPublicKey(genKeyObj.getPublicKeyAbyteStr());
		} catch (Exception e) {
			e.printStackTrace();
		}
		return genKey;
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

	public KeyPairObj getGenesisKeyPair() {
		return genesisKeyPair;
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
			return CommonUtils.hex(key.getRSA3072().toByteArray());
		} else if (key.getECDSA384() != ByteString.EMPTY) {
			return CommonUtils.hex(key.getECDSA384().toByteArray());
		} else {
			return CommonUtils.hex(key.getEd25519().toByteArray());
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
		String pubKeyHex = CommonUtils.hex(pubKey);
		pubKey2privKeyMap.put(pubKeyHex, pair.getPrivate());
		return akey;
	}


	public static void firstStartupKp()  {
		try {
			genesisKeyPair = firstListedKp(startupAccountLoc, kpKey);
		} catch (Exception e) {
			e.printStackTrace();
			System.out.println("Can't get genesis account keys");
		}
		try {
			if(publicToPrivateKey == null) {
				publicToPrivateKey = new HashMap<>();
			}
			publicToPrivateKey.put(genesisKeyPair.getPublicKeyAbyteStr(), genesisKeyPair.getPrivateKey());
			System.out.println("Loaded the genesis key successfully.");
		} catch (Exception e) {

		}
	}

	public static KeyPairObj firstListedKp(String accountInfoPath, String kpKey) throws Exception {
		Object asObj = convertFromBytes(asBase64Bytes(accountInfoPath));
		return firstKpFrom(asObj, kpKey);
	}


	private static KeyPairObj firstKpFrom(Object keyStore, String name) {
		return ((Map<String, List<AccountKeyListObj>>) keyStore)
				.get(name)
				.get(0)
				.getKeyPairList()
				.get(0);
	}

	private static byte[] asBase64Bytes(String path) throws Exception {
		//String text = "nonsense";
		String text = new String(Files.toByteArray(new File(path)));
		return com.hedera.services.statecreation.creationtxns.utils.CommonUtils.base64decode(text);
	}

	public static Key asPublicKey(String pubKeyHex)  {
		return Key.newBuilder()
				.setEd25519(ByteString.copyFrom(CommonUtils.unhex(pubKeyHex)))
				.build();
	}

	public static Object convertFromBytes(byte[] bytes) {
		try (ByteArrayInputStream bis = new ByteArrayInputStream(
				bytes); ObjectInput in = new ObjectInputStream(bis)) {
			return in.readObject();
		} catch (IOException | ClassNotFoundException e) {
			e.printStackTrace();
		}
		return null;
	}


	// Try to read from pem file to see if it's an ed25519

	public static void readKeyFromPemFile()  {
		try {
			File fin = new File(pemFile);
			//Ed25519KeyStore keyStore = Ed25519KeyStore.read("swirlds".toCharArray(), fin);
			genesisKeyPair  = asOcKeystore(fin, "passphrase");
			//genesisKeyPair = firstKpFrom(keyStore, "STARTUP_ACCOUNT");
		} catch (Exception e) {
			e.printStackTrace();
			System.out.println("Can't get pem file at {}" + pemFile);
		}
		try {
			String pubKeyStr = genesisKeyPair.getPublicKeyAbyteStr();
			PrivateKey privateKey = genesisKeyPair.getPrivateKey();
			if(publicToPrivateKey == null) {
				publicToPrivateKey = new HashMap<>();
			}
			publicToPrivateKey.put(pubKeyStr, privateKey);
			System.out.println("Loaded the genesis key successfully.");
		} catch (Exception e) {
			e.printStackTrace();
			System.out.println("Can't get the key from pemfile");
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
