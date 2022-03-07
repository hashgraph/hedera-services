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
import com.google.protobuf.ByteString;
import com.hedera.services.bdd.spec.HapiApiSpec;
import com.hedera.services.bdd.spec.HapiSpecSetup;
import com.hedera.services.bdd.spec.infrastructure.HapiSpecRegistry;
import com.hedera.services.bdd.spec.persistence.SpecKey;
import com.hedera.services.bdd.suites.utils.keypairs.Ed25519KeyStore;
import com.hedera.services.bdd.suites.utils.keypairs.Ed25519PrivateKey;
import com.hedera.services.bdd.suites.utils.keypairs.SpecUtils;
import com.hedera.services.legacy.core.AccountKeyListObj;
import com.hedera.services.legacy.core.CommonUtils;
import com.hedera.services.legacy.core.KeyPairObj;
import com.hedera.services.legacy.proto.utils.SignatureGenerator;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.KeyList;
import com.hederahashgraph.api.proto.java.SignatureMap;
import com.hederahashgraph.api.proto.java.SignaturePair;
import com.hederahashgraph.api.proto.java.ThresholdKey;
import com.hederahashgraph.api.proto.java.Transaction;
import net.i2p.crypto.eddsa.EdDSAPrivateKey;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.bouncycastle.jcajce.provider.digest.Keccak;
import org.junit.jupiter.api.Assertions;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.NotSerializableException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.io.UncheckedIOException;
import java.nio.file.Paths;
import java.security.KeyStoreException;
import java.security.PrivateKey;
import java.security.interfaces.ECPrivateKey;
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
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static com.hedera.services.bdd.spec.keys.DefaultKeyGen.DEFAULT_KEY_GEN;
import static com.hedera.services.bdd.spec.keys.SigControl.ON;
import static com.hedera.services.bdd.spec.keys.SigMapGenerator.Nature.UNIQUE_PREFIXES;
import static com.hedera.services.bdd.spec.transactions.TxnUtils.asContractId;
import static com.hedera.services.bdd.suites.utils.keypairs.SpecUtils.asLegacyKp;
import static java.util.Map.Entry;
import static java.util.stream.Collectors.toList;

public class KeyFactory implements Serializable {
	public static String PEM_PASSPHRASE = "swirlds";
	private static final long serialVersionUID = 1L;
	private static final Logger log = LogManager.getLogger(KeyFactory.class);

	public enum KeyType {
		SIMPLE, LIST, THRESHOLD
	}

	private final HapiSpecSetup setup;
	private final Map<String, PrivateKey> pkMap = new ConcurrentHashMap<>();
	private Map<Key, SigControl> controlMap = new ConcurrentHashMap<>();
	private final SigMapGenerator defaultSigMapGen = TrieSigMapGenerator.withNature(UNIQUE_PREFIXES);

	private transient HapiSpecRegistry registry;

	public KeyFactory(HapiSpecSetup setup, HapiSpecRegistry registry) throws Exception {
		this.setup = setup;
		this.registry = registry;

		KeyPairObj genesisKp = firstStartupKp(setup);
		incorporate(
				setup.genesisAccountName(),
				genesisKp,
				KeyShape.listSigs(ON));
	}

	public void exportSimpleKeyAsLegacyStartUpAccount(String exportKey, AccountID owner, String b64EncodedLoc) {
		final var protoKey = registry.getKey(exportKey);
		final var pubKeyBytes = protoKey.getEd25519().toByteArray();
		final var hexedPubKey = com.swirlds.common.CommonUtils.hex(pubKeyBytes);
		final var privKey = pkMap.get(hexedPubKey);

		try {
			final var b64Form = SpecUtils.ed25519KeyToOcKeystore((EdDSAPrivateKey) privKey, owner);
			java.nio.file.Files.writeString(Paths.get(b64EncodedLoc), b64Form);
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
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
		var hexedPubKey = com.swirlds.common.CommonUtils.hex(pubKeyBytes);

		var privateKey = pkMap.get(hexedPubKey);

		var store = new Ed25519KeyStore.Builder().withPassword(passphrase.toCharArray()).build();
		store.insertNewKeyPair(Ed25519PrivateKey.fromBytes(privateKey.getEncoded()));
		store.write(new File(loc));
	}

	public void incorporateSimpleWacl(
			String byName,
			KeyPairObj kp
	) throws InvalidKeySpecException, IllegalArgumentException {
		incorporate(byName, kp, KeyShape.listOf(1));
	}

	public void incorporate(
			String byName,
			KeyPairObj kp
	) throws InvalidKeySpecException, IllegalArgumentException {
		incorporate(byName, kp, ON);
	}

	public void incorporate(
			String byName,
			KeyPairObj kp,
			SigControl control
	) throws InvalidKeySpecException, IllegalArgumentException {
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
			final HapiApiSpec spec,
			final Transaction.Builder txn,
			final List<Key> keys,
			final Map<Key, SigControl> overrides
	) throws Throwable {
		return sign(spec, txn, defaultSigMapGen, authorsFor(keys, overrides));
	}

	public Transaction sign(
			final HapiApiSpec spec,
			final Transaction.Builder txn,
			final List<Key> keys,
			final Map<Key, SigControl> overrides,
			final SigMapGenerator sigMapGen
	) throws Throwable {
		return sign(spec, txn, sigMapGen, authorsFor(keys, overrides));
	}

	public List<Entry<Key, SigControl>> authorsFor(List<Key> keys, Map<Key, SigControl> overrides) {
		return keys.stream().map(k -> asAuthor(k, overrides)).collect(toList());
	}

	private Entry<Key, SigControl> asAuthor(Key key, Map<Key, SigControl> overrides) {
		SigControl control = overrides.getOrDefault(key, controlMap.get(key));

		if (control == null) {
			throw new IllegalArgumentException("No sig control for key " + key);
		}
		if (!control.appliesTo(key)) {
			throw new IllegalStateException("Control " + control + " for key " + key + " doesn't apply");
		}

		return Pair.of(key, control);
	}

	private Transaction sign(
			final HapiApiSpec spec,
			final Transaction.Builder txn,
			final SigMapGenerator sigMapGen,
			final List<Entry<Key, SigControl>> authors
	) throws Throwable {
		final var signing = new PrimitiveSigning(
				com.hedera.services.legacy.proto.utils.CommonUtils.extractTransactionBodyBytes(txn), authors);

		final var primitiveSigs = signing.completed();
		final var sigMap = sigMapGen.forPrimitiveSigs(spec, primitiveSigs);

		txn.setSigMap(sigMap);

		return txn.build();
	}

	public Transaction signWithFullPrefixEd25519Keys(
			final Transaction.Builder txn,
			final List<Key> keys
	) throws Throwable {
		final List<Entry<Key, SigControl>> authors = keys.stream()
				.<Entry<Key, SigControl>>map(k -> Pair.of(k, SigControl.ED25519_ON))
				.toList();
		final var signing = new PrimitiveSigning(
				com.hedera.services.legacy.proto.utils.CommonUtils.extractTransactionBodyBytes(txn), authors);
		final var primitiveSigs = signing.completed();
		final var sigMap = SignatureMap.newBuilder();
		for (final var sig : primitiveSigs) {
			sigMap.addSigPair(SignaturePair.newBuilder()
					.setPubKeyPrefix(ByteString.copyFrom(sig.getKey()))
					.setEd25519(ByteString.copyFrom(sig.getValue())));
		}
		txn.setSigMap(sigMap);
		return txn.build();
	}

	public class PrimitiveSigning {
		private byte[] keccak256Digest;

		private final byte[] data;
		private final Set<String> used = new HashSet<>();
		private final List<Entry<Key, SigControl>> authors;
		private final List<Entry<byte[], byte[]>> keySigs = new ArrayList<>();

		public PrimitiveSigning(byte[] data, List<Entry<Key, SigControl>> authors) {
			this.data = data;
			this.authors = authors;
		}

		public List<Entry<byte[], byte[]>> completed() throws Throwable {
			for (final var author : authors) {
				signRecursively(author.getKey(), author.getValue());
			}
			return keySigs;
		}

		private void signRecursively(Key key, SigControl controller) throws Throwable {
			switch (controller.getNature()) {
				case SIG_OFF:
				case CONTRACT_ID:
				case DELEGATABLE_CONTRACT_ID:
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

		private void signIfNecessary(final Key key) throws Throwable {
			final var pk = extractPubKey(key);
			final var hexedPk = com.swirlds.common.CommonUtils.hex(pk);
			if (!used.contains(hexedPk)) {
				final var privateKey = pkMap.get(hexedPk);
				final byte[] sig;
				if (privateKey instanceof ECPrivateKey) {
					if (keccak256Digest == null) {
						keccak256Digest = new Keccak.Digest256().digest(data);
					}
					sig = SignatureGenerator.signBytes(keccak256Digest, privateKey);
				} else {
					sig = SignatureGenerator.signBytes(data, privateKey);
				}
				keySigs.add(new AbstractMap.SimpleEntry<>(pk, sig));
				used.add(hexedPk);
			}
		}

		private byte[] extractPubKey(final Key key) {
			if (!key.getECDSASecp256K1().isEmpty()) {
				return key.getECDSASecp256K1().toByteArray();
			} else if (!key.getEd25519().isEmpty()) {
				return key.getEd25519().toByteArray();
			} else {
				throw new IllegalArgumentException("No supported public key in " + key);
			}
		}
	}

	public static KeyList getCompositeList(Key key) {
		return key.hasKeyList() ? key.getKeyList() : key.getThresholdKey().getKeys();
	}

	public static KeyPairObj firstStartupKp(HapiSpecSetup setup) throws Exception {
		if (StringUtils.isNotEmpty(setup.defaultPayerMnemonicFile())) {
			var mnemonic = mnemonicFromFile(setup.defaultPayerMnemonicFile());
			return asLegacyKp(SpecKey.mnemonicToEd25519Key(mnemonic));
		} else if (StringUtils.isNotEmpty(setup.defaultPayerMnemonic())) {
			return asLegacyKp(SpecKey.mnemonicToEd25519Key(setup.defaultPayerMnemonic()));
		} else if (StringUtils.isNotEmpty(setup.defaultPayerPemKeyLoc())) {
			var keyPair = SpecKey.readFirstKpFromPem(
					new File(setup.defaultPayerPemKeyLoc()),
					setup.defaultPayerPemKeyPassphrase());
			return asLegacyKp(keyPair);
		} else if (StringUtils.isNotEmpty(setup.startupAccountsLiteral())) {
			Object keyStore = CommonUtils.convertFromBytes(CommonUtils.base64decode(setup.startupAccountsLiteral()));
			return firstKpFrom(keyStore, setup.genesisStartupKey());
		} else {
			return firstListedKp(setup.startupAccountsPath(), setup.genesisStartupKey());
		}
	}

	public static String mnemonicFromFile(String wordsLoc) {
		try {
			return java.nio.file.Files.lines(Paths.get(wordsLoc)).collect(Collectors.joining(" "));
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	public static KeyPairObj firstListedKp(String accountInfoPath, String kpKey) throws Exception {
		Object asObj = CommonUtils.convertFromBytes(asBase64Bytes(accountInfoPath));
		return firstKpFrom(asObj, kpKey);
	}

	@SuppressWarnings("unchecked")
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

	synchronized public Key generateSubjectTo(
			final HapiApiSpec spec,
			final SigControl controller,
			final KeyGenerator keyGen
	) {
		return new Generation(spec, controller, keyGen).outcome();
	}

	synchronized public Key generateSubjectTo(
			final HapiApiSpec spec,
			final SigControl controller,
			final KeyGenerator keyGen,
			final KeyLabel labels
	) {
		return new Generation(spec, controller, keyGen, labels).outcome();
	}

	private class Generation {
		private final SigControl.KeyAlgo[] algoChoices = {
			SigControl.KeyAlgo.ED25519, SigControl.KeyAlgo.SECP256K1
		};

		private final KeyLabel labels;
		private final SigControl control;
		private final HapiApiSpec spec;
		private final KeyGenerator keyGen;
		private final Map<String, Key> byLabel = new HashMap<>();

		private int nextUnspecifiedAlgo = 0;

		public Generation(HapiApiSpec spec, SigControl control, KeyGenerator keyGen) {
			this(spec, control, keyGen, KeyLabel.uniquelyLabeling(control));
		}

		public Generation(HapiApiSpec spec, SigControl control, KeyGenerator keyGen, KeyLabel labels) {
			this.spec = spec;
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
				case CONTRACT_ID:
					final var cid = asContractId(sc.contract(), spec);
					generated = Key.newBuilder().setContractID(cid).build();
					break;
				case DELEGATABLE_CONTRACT_ID:
					final var dcid = asContractId(sc.delegatableContract(), spec);
					generated = Key.newBuilder().setDelegatableContractId(dcid).build();
					break;
				case LIST:
					generated = Key.newBuilder()
							.setKeyList(composing(label.getConstituents(), sc.getChildControls())).build();
					break;
				case THRESHOLD:
					final var tKey = ThresholdKey.newBuilder()
							.setThreshold(sc.getThreshold())
							.setKeys(composing(label.getConstituents(), sc.getChildControls())).build();
					generated = Key.newBuilder().setThresholdKey(tKey).build();
					break;
				default:
					if (byLabel.containsKey(label.literally())) {
						generated = byLabel.get(label.literally());
					} else {
						final SigControl.KeyAlgo choice;
						if (sc.keyAlgo() == SigControl.KeyAlgo.UNSPECIFIED) {
							final var defaultAlgo = spec.setup().defaultKeyAlgo();
							if (defaultAlgo != SigControl.KeyAlgo.UNSPECIFIED) {
								choice = defaultAlgo;
							} else {
								/* A spec run with unspecified default algorithm alternates between Ed25519 and ECDSA */
								choice = algoChoices[nextUnspecifiedAlgo];
								nextUnspecifiedAlgo = (nextUnspecifiedAlgo + 1) % algoChoices.length;
							}
						} else {
							choice = sc.keyAlgo();
						}
						generated = generateByAlgo(choice);
						byLabel.put(label.literally(), generated);
					}
					break;
			}
			if (saving) {
				controlMap.put(generated, sc);
			}
			return generated;
		}

		private Key generateByAlgo(final SigControl.KeyAlgo algo) {
			if (algo == SigControl.KeyAlgo.ED25519) {
				return keyGen.genEd25519AndUpdateMap(pkMap);
			} else if (algo == SigControl.KeyAlgo.SECP256K1) {
				return keyGen.genEcdsaSecp256k1AndUpdate(pkMap);
			} else {
				throw new IllegalArgumentException(algo + " not supported");
			}
		}

		private KeyList composing(KeyLabel[] ls, SigControl[] cs) {
			Assertions.assertEquals(ls.length, cs.length, "Incompatible ls and cs!");
			int N = ls.length;
			return KeyList.newBuilder().addAllKeys(
					IntStream.range(0, N)
							.mapToObj(i -> generate(cs[i], ls[i], false))
							.collect(toList()))
					.build();
		}
	}

	public Key generate(final HapiApiSpec spec, final KeyType type) {
		return generate(spec, type, DEFAULT_KEY_GEN);
	}

	public Key generate(final HapiApiSpec spec, final KeyType type, final KeyGenerator keyGen) {
		switch (type) {
			case THRESHOLD:
				return generateSubjectTo(
						spec,
						KeyShape.threshSigs(
								setup.defaultThresholdM(),
								IntStream
										.range(0, setup.defaultThresholdN())
										.mapToObj(ignore -> SigControl.ON)
										.toArray(SigControl[]::new)),
						keyGen);
			case LIST:
				return generateSubjectTo(
						spec,
						KeyShape.listSigs(
								IntStream
										.range(0, setup.defaultListN())
										.mapToObj(ignore -> SigControl.ON)
										.toArray(SigControl[]::new)),
						keyGen);
			default:
				return generateSubjectTo(spec, ON, keyGen);
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
		log.info("Successfully serialized controlMap to : " + path);
	}

	@SuppressWarnings("unchecked")
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
