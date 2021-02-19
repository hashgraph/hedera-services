package com.hedera.services.bdd.spec.keys;

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

import com.google.common.io.Files;
import com.hedera.services.bdd.spec.persistence.SpecKey;
import com.hedera.services.bdd.suites.utils.keypairs.Ed25519KeyStore;
import com.hedera.services.bdd.suites.utils.keypairs.Ed25519PrivateKey;
import com.hedera.services.bdd.suites.utils.keypairs.SpecUtils;
import com.hedera.services.legacy.proto.utils.KeyExpansion;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.KeyList;
import com.hederahashgraph.api.proto.java.SignatureMap;
import com.hederahashgraph.api.proto.java.ThresholdKey;
import com.hederahashgraph.api.proto.java.Transaction;
import com.hederahashgraph.builder.TransactionSigner;
import com.hedera.services.legacy.core.AccountKeyListObj;
import com.hedera.services.legacy.core.CommonUtils;
import com.hedera.services.legacy.core.HexUtils;
import com.hedera.services.legacy.core.KeyPairObj;
import com.hedera.services.legacy.proto.utils.SignatureGenerator;
import com.hedera.services.bdd.spec.infrastructure.HapiSpecRegistry;
import com.hedera.services.bdd.spec.HapiSpecSetup;
import org.apache.commons.codec.DecoderException;
import org.apache.commons.codec.binary.Hex;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.Assert;


import java.io.*;
import java.security.KeyStoreException;
import java.security.PrivateKey;
import java.security.spec.InvalidKeySpecException;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.stream.IntStream;

import static com.hedera.services.bdd.spec.keys.SigControl.ON;
import static java.util.Map.Entry;
import static java.util.stream.Collectors.toList;

public class KeyFactory implements Serializable {
	public static String PEM_PASSPHRASE = "swirlds";
	private static final long serialVersionUID = 1L;
	private static final Logger log = LogManager.getLogger(KeyFactory.class);

	public enum KeyType {SIMPLE, LIST, THRESHOLD}

	private final HapiSpecSetup setup;
	private transient HapiSpecRegistry registry;
	private Map<String, PrivateKey> pkMap = new ConcurrentHashMap<>();
	private Map<Key, SigControl> controlMap = new ConcurrentHashMap<>();
	private SigMapGenerator.Nature defaultSigMapGen = SigMapGenerator.Nature.UNIQUE;

	public KeyFactory(HapiSpecSetup setup, HapiSpecRegistry registry) throws Exception {
		this.setup = setup;
		this.registry = registry;

		KeyPairObj genesisKp = firstStartupKp(setup);
		incorporate(
				setup.genesisAccountName(),
				genesisKp,
				KeyShape.listSigs(ON));
	}

	public Transaction getSigned(Transaction.Builder txn, List<Key> signers) throws Exception {
		return TransactionSigner.signTransactionComplexWithSigMap(txn, signers, pkMap);
	}

	public void exportSimpleKey(
			String loc,
			String name
	) throws KeyStoreException {
		exportSimpleKey(loc, name, key -> key.getEd25519().toByteArray());
	}

	public void exportSimpleKey(
			String loc,
			String name,
			String passphrase
	) throws KeyStoreException {
		exportSimpleKey(loc, name, key -> key.getEd25519().toByteArray(), passphrase);
	}

	public void exportSimpleWacl(
			String loc,
			String name
	) throws KeyStoreException {
		exportSimpleKey(loc, name, key -> key.getKeyList().getKeys(0).getEd25519().toByteArray());
	}

	public void exportSimpleKey(
			String loc,
			String name,
			Function<Key, byte[]> targetKeyExtractor
	) throws KeyStoreException {
		exportSimpleKey(loc, name, targetKeyExtractor, PEM_PASSPHRASE);
	}

	public void exportSimpleKey(
			String loc,
			String name,
			Function<Key, byte[]> targetKeyExtractor,
			String passphrase
	) throws KeyStoreException {
		var pubKeyBytes = targetKeyExtractor.apply(registry.getKey(name));
		var privateKey = pkMap.get(Hex.encodeHexString(pubKeyBytes));

		var store = new Ed25519KeyStore.Builder().withPassword(passphrase.toCharArray()).build();
		store.insertNewKeyPair(Ed25519PrivateKey.fromBytes(privateKey.getEncoded()));
		store.write(new File(loc));
	}

	public void incorporateSimpleWacl(
			String byName,
			KeyPairObj kp
	) throws InvalidKeySpecException, DecoderException {
		incorporate(byName, kp, KeyShape.listOf(1));
	}

	public void incorporate(
			String byName,
			KeyPairObj kp
	) throws InvalidKeySpecException, DecoderException {
		incorporate(byName, kp, ON);
	}

	public void incorporate(
			String byName,
			KeyPairObj kp,
			SigControl control
	) throws InvalidKeySpecException, DecoderException {
		String pubKeyHex = kp.getPublicKeyAbyteStr();
		pkMap.put(pubKeyHex, kp.getPrivateKey());
		controlMap.put(registry.getKey(byName), control);
	}

	public void incorporate(
			String byName,
			String pubKeyHex,
			PrivateKey privateKey,
			SigControl control
	) {
		pkMap.put(pubKeyHex, privateKey);
		controlMap.put(registry.getKey(byName), control);
	}

	public SigControl controlFor(Key key) {
		return controlMap.get(key);
	}

	public void setControl(Key key, SigControl control) {
		controlMap.put(key, control);
	}

	public int controlledKeyCount(Key key, Map<Key, SigControl> overrides) {
		return asAuthor(key, overrides).getValue().numSimpleKeys();
	}

	public Transaction sign(
			Transaction.Builder txn,
			List<Key> keys,
			Map<Key, SigControl> overrides) throws Throwable {
		return sign(txn, defaultSigMapGen, authorsFor(keys, overrides));
	}

	public Transaction sign(
			Transaction.Builder txn,
			List<Key> keys,
			Map<Key, SigControl> overrides,
			SigMapGenerator.Nature sigMapGen) throws Throwable {
		return sign(txn, sigMapGen, authorsFor(keys, overrides));
	}

	public List<Entry<Key, SigControl>> authorsFor(List<Key> keys, Map<Key, SigControl> overrides) {
		return keys.stream().map(k -> asAuthor(k, overrides)).collect(toList());
	}

	private Entry<Key, SigControl> asAuthor(Key key, Map<Key, SigControl> overrides) {
		SigControl control = overrides.getOrDefault(key, controlMap.get(key));
		Assert.assertNotNull("Missing sig control!", control);
		Assert.assertTrue("Key shape doesn't match sig control! control=" + control, control.appliesTo(key));
		return new AbstractMap.SimpleEntry<>(key, control);
	}

	private Transaction sign(
			Transaction.Builder txn,
			SigMapGenerator.Nature sigMapGen,
			List<Entry<Key, SigControl>> authors) throws Throwable {
		Ed25519Signing signing = new Ed25519Signing(
				com.hedera.services.legacy.proto.utils.CommonUtils.extractTransactionBodyBytes(txn), authors);
		List<Entry<byte[], byte[]>> keySigs = signing.completed();
		SignatureMap sigMap = TrieSigMapGenerator.withNature(sigMapGen).forEd25519Sigs(keySigs);
		txn.setSigMap(sigMap);
		return txn.build();
	}

	public class Ed25519Signing {
		private Set<String> used = new HashSet<>();
		private List<Entry<byte[], byte[]>> keySigs = new ArrayList<>();
		private final byte[] data;
		private final List<Entry<Key, SigControl>> authors;

		public Ed25519Signing(byte[] data, List<Entry<Key, SigControl>> authors) {
			this.data = data;
			this.authors = authors;
		}

		public List<Entry<byte[], byte[]>> completed() throws Throwable {
			for (Entry<Key, SigControl> author : authors) {
				signRecursively(author.getKey(), author.getValue());
			}
			return keySigs;
		}

		private void signRecursively(Key key, SigControl controller) throws Throwable {
			switch (controller.getNature()) {
				case SIG_OFF:
					break;
				case SIG_ON:
					signIfNecessary(key);
					break;
				default:
					KeyList composite = getCompositeList(key);
					SigControl[] childControls = controller.getChildControls();
					for (int i = 0; i < childControls.length; i++) {
						signRecursively(composite.getKeys(i), childControls[i]);
					}
			}
		}

		private void signIfNecessary(Key key) throws Throwable {
			byte[] pubKey = key.getEd25519().toByteArray();
			String pubKeyHex = HexUtils.bytes2Hex(pubKey);
			if (!used.contains(pubKeyHex)) {
				PrivateKey signer = pkMap.get(pubKeyHex);

				byte[] sig = HexUtils.hexToBytes(SignatureGenerator.signBytes(data, signer));
				keySigs.add(new AbstractMap.SimpleEntry<>(pubKey, sig));
				used.add(pubKeyHex);
			}
		}
	}

	public static KeyList getCompositeList(Key key) {
		return key.hasKeyList() ? key.getKeyList() : key.getThresholdKey().getKeys();
	}

	public static KeyPairObj firstStartupKp(HapiSpecSetup setup) throws Exception {
		if (StringUtils.isNotEmpty(setup.defaultPayerMnemonic())) {
			return SpecUtils.asLegacyKp(SpecKey.asEd25519Key(setup.defaultPayerMnemonic()));
		} else if (StringUtils.isNotEmpty(setup.defaultPayerPemKeyLoc())) {
			var keyPair = SpecKey.readFirstKp(
					new File(setup.defaultPayerPemKeyLoc()),
					setup.defaultPayerPemKeyPassphrase());
			return SpecUtils.asLegacyKp(keyPair);
		} else if (StringUtils.isNotEmpty(setup.startupAccountsLiteral())) {
			Object keyStore = CommonUtils.convertFromBytes(CommonUtils.base64decode(setup.startupAccountsLiteral()));
			return firstKpFrom(keyStore, setup.genesisStartupKey());
		} else {
			return firstListedKp(setup.startupAccountsPath(), setup.genesisStartupKey());
		}
	}

	public static KeyPairObj firstListedKp(String accountInfoPath, String kpKey) throws Exception {
		Object asObj = CommonUtils.convertFromBytes(asBase64Bytes(accountInfoPath));
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
		String text = new String(Files.toByteArray(new File(path)));
		return CommonUtils.base64decode(text);
	}

	synchronized public Key generateSubjectTo(SigControl controller, KeyGenerator keyGen) {
		return new Generation(controller, keyGen).outcome();
	}

	synchronized public Key generateSubjectTo(SigControl controller, KeyGenerator keyGen, KeyLabel labels) {
		return new Generation(controller, keyGen, labels).outcome();
	}

	private class Generation {
		private final KeyLabel labels;
		private final SigControl control;
		private final KeyGenerator keyGen;
		private final Map<String, Key> byLabel = new HashMap<>();

		public Generation(SigControl control, KeyGenerator keyGen) {
			this(control, keyGen, KeyLabel.uniquelyLabeling(control));
		}

		public Generation(SigControl control, KeyGenerator keyGen, KeyLabel labels) {
			this.labels = labels;
			this.control = control;
			this.keyGen = keyGen;
		}

		public Key outcome() {
			return generate(control, labels, true);
		}

		private Key generate(SigControl sc, KeyLabel label, boolean saving) {
			Key generated;

			switch (sc.getNature()) {
				case LIST:
					generated = Key.newBuilder()
							.setKeyList(composing(label.getConstituents(), sc.getChildControls())).build();
					break;
				case THRESHOLD:
					ThresholdKey tKey = ThresholdKey.newBuilder()
							.setThreshold(sc.getThreshold())
							.setKeys(composing(label.getConstituents(), sc.getChildControls())).build();
					generated = Key.newBuilder().setThresholdKey(tKey).build();
					break;
				default:
					if (byLabel.containsKey(label.literally())) {
						generated = byLabel.get(label.literally());
					} else {
						generated = keyGen.genEd25519AndUpdateMap(pkMap);

						byLabel.put(label.literally(), generated);
					}
					break;
			}
			if (saving) {
				controlMap.put(generated, sc);
			}
			return generated;
		}

		private KeyList composing(KeyLabel[] ls, SigControl[] cs) {
			Assert.assertEquals("Incompatible ls and cs!", ls.length, cs.length);
			int N = ls.length;
			return KeyList.newBuilder().addAllKeys(
					IntStream.range(0, N)
							.mapToObj(i -> generate(cs[i], ls[i], false))
							.collect(toList()))
					.build();
		}
	}

	public Key generate(KeyType type) {
		return generate(type, KeyExpansion::genSingleEd25519KeyByteEncodePubKey);
	}

	public Key generate(KeyType type, KeyGenerator keyGen) {
		switch (type) {
			case THRESHOLD:
				return generateSubjectTo(
						KeyShape.threshSigs(
								setup.defaultThresholdM(),
								IntStream
										.range(0, setup.defaultThresholdN())
										.mapToObj(ignore -> SigControl.ON)
										.collect(toList()).toArray(new SigControl[0])), keyGen);
			case LIST:
				return generateSubjectTo(
						KeyShape.listSigs(
								IntStream
										.range(0, setup.defaultListN())
										.mapToObj(ignore -> SigControl.ON)
										.collect(toList()).toArray(new SigControl[0])), keyGen);
			default:
				return generateSubjectTo(ON, keyGen);
		}
	}

	public void saveKeyFactory(String dir) {
		// Note: here we didn't save and restore pkMap for KeyFactory is to avoid
		// Serialization and de-serialization of PrivateKey, which is not easy.
		// Instead, we use GENESIS key for the migration test accounts and serve the
		// same purpose.
		//		savePkMap(dir + "/pkmap.ser");
		saveControlMap(dir + "/controlmap.ser");
	}

	public void loadKeyFactory(String dir) {
		loadControlMap(dir + "/controlmap.ser");
	}

	public void saveControlMap(String path) {
		FileOutputStream fos = null;
		ObjectOutputStream oos = null;
		log.info("Serialize controlMap to : " + path);
		try {
			fos = new FileOutputStream(path);
			oos = new ObjectOutputStream(fos);
			oos.writeObject(controlMap);
			oos.close();
			fos.close();
			oos = null;
			fos = null;
		} catch (NotSerializableException e) {
			log.error("Serializable exception catched while serialize for " + path + ":" + e);
		} catch (FileNotFoundException e) {
			log.error("File not found exception catched while serialize for  " + path + ":" + e);
		} catch (Exception e) {
			log.error("Other exception catched while serialize for " + path + ":" + e);
		} finally {
			try {
				if (oos != null) {
					oos.close();
				}
				if (fos != null) {
					fos.close();
				}

			} catch (IOException e) {
				log.error("Error while closing file " + path + ":" + e);
			}
		}
		;
		log.info("Successfully serialized controlMap to : " + path);
	}

	public void loadControlMap(String path) {
		FileInputStream fis = null;
		ObjectInputStream ois = null;

		log.info("Deserialize controlMap from : " + path);
		try {
			fis = new FileInputStream(path);
			ois = new ObjectInputStream(fis);
			controlMap = (Map<Key, SigControl>) ois.readObject();

			ois.close();
			fis.close();
			fis = null;
		} catch (Exception e) {
			log.error("De-serializable exception catched while working on " + path + ":" + e);
		} finally {
			try {
				if (ois != null) {
					ois.close();
				}
				if (fis != null) {
					fis.close();
				}
			} catch (IOException e) {
				log.error("File closing exception catched while closing " + path + ":" + e);
			}
		}
		log.info(" Sucessfully de-serialized controlMap from " + path);
	}
}
